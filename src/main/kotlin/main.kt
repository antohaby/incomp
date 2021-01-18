package prj.incomp

import prj.incomp.java.*
import prj.incomp.java.abi.*
import prj.incomp.persistency.ClassMapFile
import prj.incomp.persistency.SourceHashesFile
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@ExperimentalTime
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Missing arguments: [sourceDir] [targetDir] [classPath]")
        exitProcess(1)
    }

    val fs = FileSystems.getDefault()
    val sourceDir = fs.getPath(args[0])
    val targetPath = fs.getPath(args[1])
    val classPath = args[2]

    if (!Files.exists(targetPath)) {
        Files.createDirectories(targetPath)
    }

    val request = CompilationRequest(
        sourceSet = PathSourceSet(sourceDir),
        classpath = JavaClasspath.parse(classPath, fs),
        destination = targetPath,
    )

    val sourceHashesFile = SourceHashesFile(targetPath.resolve("source-hashes.txt"))
    val classMapFile =  ClassMapFile(targetPath.resolve("class-map.txt"))
    val compiler = IncrementalCompilation(
        fileChangesTracker = FileChangesTracker(sourceHashesFile),
        abiProvider = AsmAbiProvider,
        abiCompatibility = java8SpecIncompatibilityChecker(),
        classMapStore = classMapFile,
        compiler = JdkCompiler(ToolProvider.getSystemJavaCompiler())
    )


    val (result, duration) = measureTimedValue { compiler.runCompilation(request) }

    if (result.isSuccessful) {
        println("Compiled Files: ${result.compiledClasses.keys}")
        println("Total ${result.compiledClasses.size} files in $duration")
    } else {
        println("Compilation failed in $duration")
    }
}
