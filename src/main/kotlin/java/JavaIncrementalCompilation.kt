package prj.incomp.java

import org.mapdb.DB
import prj.incomp.common.*
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.tools.*

interface JavaMetadataStore : CompilationMetadataStore<JavaCompilationContext, IncrementalCompilationMetadata>

typealias JavaIncrementalPipeline =
        StatefulCompilationPipeline<JavaCompilationContext, JavaCompilationUnit, IncrementalCompilationMetadata>


inline class RelativeSourcePath(
    val stringPath: String
) {
    constructor(path: Path): this(path.toString())
}

/**
 * @param lastCompileTime a timestamp of last compilation session
 * @param dependencies Contains a map of Java Source files dependencies,
 * key is the subject's path and value is set of sources paths the subjects depends on
 *
 * @param dependants is a revers representation of [dependencies], so key is a dependency and value  is
 * a set of sources that it depending on.
 *
 */
data class IncrementalCompilationMetadata(
    val lastCompileTime: Long,
    val dependencies: Map<RelativeSourcePath, Set<RelativeSourcePath>>,
    val dependants: Map<RelativeSourcePath, Set<RelativeSourcePath>>,
    val sourceFileAstHash: Map<String, String> // Unused
)

data class JavaCompilationUnit(
    val pathInSourceSet: RelativeSourcePath,
    val sourceFile: File,
    val classFile: File?,
)

/**
 * Compilation Context that is used as entrypoint in pipeline
 * contains all required information to execute compilation
 */
data class JavaCompilationContext(
    val currentTime: Long,
    val sourceSet: JavaFsSourceSet,
    val targetLocation: Path,
    val classPath: String = ""
) {
    val fileSystem: FileSystem get() = sourceSet.path.fileSystem
}

fun javaIncrementalCompilationPipeline(
    db: DB,
    compiler: JavaCompiler,
    sourceParser: JavaCompilationContext.() -> JavaSourceParser = { JavaSourceParserImpl(sourceSet.path) }
): JavaIncrementalPipeline {
    return StatefulCompilationPipeline(
        metadataStore = MapDBMetadataStore(db),
        sourceReader = JavaCompilationContext::incrementalSourceReader,
        compile = adaptNativeJavaCompiler(compiler),
        updateMetadata = updateMetadataWithParser(sourceParser),
        updateMetadataOnStart = { metadata ->
            metadata.copy(lastCompileTime = currentTime)
        }
    )
}

fun updateMetadataWithParser(
    sourceParserProvider: JavaCompilationContext.() -> JavaSourceParser
): UpdateMetadata<JavaCompilationContext, JavaCompilationUnit, IncrementalCompilationMetadata> {
    return lambda@{ metadata, currentUnit, result ->
        // If failure, dont update metadata
        if (result is CompilationResult.Failed) return@lambda metadata

        val parser = sourceParserProvider()

        val newUnitDependencies = parser
            .parseDependencies(currentUnit.sourceFile)
            ?.map { name -> RelativeSourcePath(name.path(fileSystem)) }
            ?.filter { it != currentUnit.pathInSourceSet } // Prevent self-dependencies
            ?.toSet()
            ?: emptySet()
        val oldUnitDependencies = metadata.dependencies[currentUnit.pathInSourceSet] ?: emptySet()

        val removedDependencies = oldUnitDependencies - newUnitDependencies
        val addedDependencies = newUnitDependencies - oldUnitDependencies

        val currentUnitPath = currentUnit.pathInSourceSet
        val dependantsAfterRemoving = removedDependencies.fold(metadata.dependants) { dependants, dependency ->
            val dependantsSet = dependants[dependency] ?: emptySet()
            val newDependantSet = dependantsSet - currentUnitPath

            dependants + (dependency to newDependantSet)
        }

        val newDependants = addedDependencies.fold(dependantsAfterRemoving) { dependants, dependency ->
            val dependantsSet = dependants[dependency] ?: emptySet()
            val newDependantSet = dependantsSet + currentUnitPath

            dependants + (dependency to newDependantSet)
        }

        // println("New Dependants: $newDependants")

        metadata.copy(
            dependencies = metadata.dependencies + (currentUnit.pathInSourceSet to newUnitDependencies),
            dependants = newDependants
        )
    }
}

fun JavaCompilationContext.incrementalSourceReader(
    metadata: IncrementalCompilationMetadata
): Sequence<JavaCompilationUnit> = this
    .listSourceFiles()
    .filterLastModified(
        sourceLastModified = JavaCompilationUnit::sourceLastModified,
        targetLastModified = JavaCompilationUnit::targetLastModified
    )
    // TODO: Filter unchanged ASTs
    .includeDependentSources {
        metadata.dependants[pathInSourceSet]
            ?.mapNotNull { findSingle(it.stringPath) }
            ?.asSequence()
            ?: emptySequence()
    }
    .distinctBy { it.pathInSourceSet }



fun adaptNativeJavaCompiler(
    compiler: JavaCompiler
): Compiler<JavaCompilationContext, JavaCompilationUnit, IncrementalCompilationMetadata> = { sources, _ ->
    val result = mutableMapOf<String, Pair<JavaCompilationUnit, CompilationResult>>()
    val javaFiles = mutableSetOf<JavaFile>()

    for (unit in sources) {
        javaFiles += JavaFile(unit.sourceFile)
        result[unit.sourceFile.path] = unit to CompilationResult.Success
    }

    val options = listOf(
        "-classpath", "${classPath}:${targetLocation}",
        "-d", targetLocation.toString(),
        // "-verbose"
    )

    val errors = mutableMapOf<String, MutableList<String>>()
    if (javaFiles.isNotEmpty()) {
        val task = compiler.getTask(
            null,
            null,
            { diag ->
                val javaFile = diag.source as JavaFile
                val key = javaFile.file.path
                errors.getOrPut(key) { mutableListOf() }.add(diag.toString())
            },
            options,
            null,
            javaFiles
        )
        task.call()
    }

    errors.forEach { (key, errors) ->
        result[key] = result[key]!!.first to CompilationResult.Failed(errors)
    }

    result.values.asSequence()
}