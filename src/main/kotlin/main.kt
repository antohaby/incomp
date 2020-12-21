package prj.incomp

import org.mapdb.DBMaker
import prj.incomp.common.CompilationResult
import prj.incomp.java.*
import java.io.File
import javax.tools.ToolProvider
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Missing arguments: [sourceDir] [targetDir] [classPath]")
        exitProcess(1)
    }

    val sourceDir = File(args[0])
    val targetDir = File(args[1])

    if (!targetDir.exists()) {
        targetDir.mkdir()
    }

    val context = JavaCompilationContext(
        currentTime = System.currentTimeMillis(),
        sourceSet = JavaFsSourceSet(sourceDir.path),
        targetLocation = targetDir.toPath(),
        classPath = args.getOrElse(2) { "" }
    )

    val db = DBMaker
        .fileDB(targetDir.resolve("meta.db").path)
        .transactionEnable()
        .make()

    val pipeline = javaIncrementalCompilationPipeline(
        db = db,
        compiler = ToolProvider.getSystemJavaCompiler()
    )

    var total = 0
    val duration = measureTime {
        val compilationResults = pipeline.compile(context)
        for ((unit, result) in compilationResults) {
            total++
            print("${unit.pathInSourceSet}: ")
            when (result) {
                is CompilationResult.Success -> println("OK")
                is CompilationResult.Failed -> {
                    println("Failed")
                    result.errors.forEach(::println)
                }
            }
        }
    }

    db.commit()
    db.close()
    println("Compiled $total files in: $duration")
}
