package prj.incomp.java

import prj.incomp.java.abi.*
import prj.incomp.persistency.ClassMapFile
import java.nio.file.Files
import java.nio.file.Path

class IncrementalCompilation(
    private val fileChangesTracker: FileChangesTracker,
    private val abiProvider: AsmAbiProvider,
    private val abiCompatibility: CombinedAbiCompatibilityChecker,
    private val classMapStore: ClassMapFile,
    private val compiler: JdkCompiler,
) {
    private data class CompilationContext(
        val request: CompilationRequest,
        val changedSourceFiles: ChangedSourceSet,
        val abiMap: Map<ClassName, ClassAbi>,
        val classMap: ClassMap
    )

    private data class IncrementalCompilationResult(
        val classMap: ClassMap,
        val result: CompilationResult
    )

    fun runCompilation(request: CompilationRequest): CompilationResult =
        withChangedFiles(request.sourceSet) { changedSourceFiles ->
            if (changedSourceFiles.isEmpty()) {
                CompilationResult(compiledClasses = emptyMap(), isSuccessful = true)
            } else {
                val result = compile(request, changedSourceFiles)
                classMapStore.write(result.classMap)
                result.result
            }
        }

    private fun compile(
        request: CompilationRequest,
        changedSourceFiles: ChangedSourceSet
    ): IncrementalCompilationResult {
        // Try read class map from past compilation, if failed recompileAll
        val classMap = classMapStore.read() ?: return recompileAll(request)

        val abiMap = abiProvider.provide(request.destination)
        val context = CompilationContext(
            request = request,
            changedSourceFiles = changedSourceFiles,
            classMap = classMap,
            abiMap = abiMap
        )

        // Remove Source - Class associations and related class files
        // And save set of removed classes in Compilation Context
        val removedClasses = classMap.removeClassesOfSources(
            sources = changedSourceFiles.removed,
            classRoot = request.destination
        )
        val changedSourcesResult = context.compileChangedSources()

        val changedClasses = changedSourcesResult
            .compiledClasses
            .flatMap { it.value }
            .toSet()
        val affectedClasses = changedClasses + removedClasses
        val dependantSourcesResult = context.compileDependantSources(affectedClasses) ?: return recompileAll(request)

        val finalCompilationResult = CompilationResult(
            compiledClasses = changedSourcesResult.compiledClasses + dependantSourcesResult.compiledClasses,
            isSuccessful = changedSourcesResult.isSuccessful && dependantSourcesResult.isSuccessful
        )
        return IncrementalCompilationResult(
            classMap = classMap,
            result = finalCompilationResult
        )
    }

    private fun withChangedFiles(
        sourceSet: JavaSourceSet,
        compile: (ChangedSourceSet) -> CompilationResult
    ): CompilationResult {
        val changedFilesTx = fileChangesTracker.begin(sourceSet)

        val changedSourceFiles = changedFilesTx.listChangedFiles()
        return try {
            compile(changedSourceFiles).also { result ->
                if (result.isSuccessful) {
                    changedFilesTx.commit()
                } else {
                    changedFilesTx.rollback()
                }
            }
        } catch (e: Exception) {
            changedFilesTx.rollback()
            throw e
        }
    }

    private fun ClassMap.removeClassesOfSources(
        sources: List<String>,
        classRoot: Path
    ) = sources.flatMap { sourceFile ->
        removeSourceFile(sourceFile).onEach { it.removeClassFile(classRoot) }
    }.toSet()

    private fun recompileAll(request: CompilationRequest): IncrementalCompilationResult {
        val compilationResult = compiler.runCompilation(request)
        val newClassMap = ClassMap()

        compilationResult.compiledClasses.forEach { (sourceFile, classes) ->
            newClassMap.registerSourceClassRelationship(sourceFile.relativePath, classes)
        }

        return IncrementalCompilationResult(
            classMap = newClassMap,
            result = compilationResult
        )
    }

    private fun CompilationContext.compileChangedSources(): CompilationResult {
        val compilationResult = compiler.runCompilation(request.copy(sourceSet = changedSourceFiles))

        // Register new Source - Class associations
        compilationResult.compiledClasses.forEach { (sourceFile, classes) ->
            classMap.registerSourceClassRelationship(sourceFile.relativePath, classes)
        }

        classMapStore.write(classMap)
        return compilationResult
    }

    private fun CompilationContext.compileDependantSources(
        affectedClasses: Set<ClassName>
    ): CompilationResult? {
        val newAbiMap = abiProvider.provide(request.destination)
        val dependencyGraph = ClassDependencyGraph.fromAbiMap(newAbiMap)

        var globalIncompatibilityFound = false
        val sourcesToRecompile = affectedClasses
            .asSequence()
            .map { className ->
                val old = abiMap[className]
                val new = newAbiMap[className]
                className to isCompatible(abiMap, old, new)
            }
            .filter { it.second != CompatibilityLevel.COMPATIBLE }
            .onEach { globalIncompatibilityFound = globalIncompatibilityFound || it.second == CompatibilityLevel.GLOBALLY_INCOMPATIBLE } // TODO: fold/reduce?
            .flatMap { dependencyGraph.dependantsOf(it.first)?.all ?: emptySet() }
            .filter { it !in affectedClasses } // exclude already affected (compiled or removed) classes
            .map { className ->
                val relativePath = classMap.sourceFileOf(className)
                val path = request.sourceSet.rootPath.resolve(relativePath)
                JavaSourceFile(path, relativePath)
            }
            .toList()

        // Recompile all if global incompatibility found
        if (globalIncompatibilityFound) return null

        val sourceSet = FileListSourceSet(request.sourceSet.rootPath, sourcesToRecompile)
        return compiler.runCompilation(request.copy(sourceSet = sourceSet))
    }

    private fun isCompatible(abiMap: Map<ClassName, ClassAbi>, old: ClassAbi?, new: ClassAbi?) = when {
        // When new class is added, it is ABI-compatible change
        old == null && new != null -> CompatibilityLevel.COMPATIBLE
        old != null && new == null ->
            if (old.constants.isNotEmpty()) {
                // When old class with constants is deleted it is possible Global Incompatibility
                CompatibilityLevel.GLOBALLY_INCOMPATIBLE
            } else {
                CompatibilityLevel.INCOMPATIBLE
            }
        old != null && new != null -> abiCompatibility.isCompatible(abiMap, old, new)
        else -> CompatibilityLevel.COMPATIBLE // when all is null consider as compatible
    }
}

private fun ClassName.removeClassFile(rootPath: Path) {
    val filePath = qualifiedName.replace('.', '/') + ".class"
    return Files.delete(rootPath.resolve(filePath))
}