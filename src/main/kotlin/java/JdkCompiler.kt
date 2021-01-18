// that was tricky one, I had to search
// I had to find this class
// https://github.com/JetBrains/kotlin/blob/29b23e79f32791e456a5b4a453277f0f0b3e984d/compiler/frontend.java/src/org/jetbrains/kotlin/resolve/jvm/checkers/JvmModuleAccessibilityChecker.kt
// so it was a nice dive
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package prj.incomp.java

import com.sun.source.util.JavacTask
import com.sun.source.util.TaskEvent
import com.sun.source.util.TaskListener
import com.sun.tools.javac.code.Symbol // is not exported by jdk.compiler module
import prj.incomp.java.abi.ClassName
import java.io.File
import java.nio.file.Path
import javax.lang.model.element.NestingKind
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

class JdkCompiler(
    private val compiler: JavaCompiler
) {
    fun runCompilation(request: CompilationRequest): CompilationResult {
        val options = listOf(
            "-classpath", "${request.classpath}:${request.destination}",
            "-d", request.destination.toString(),
            // "-verbose"
        )

        val fs = compiler.getStandardFileManager(null, null, null)
        val javaFiles = request
            .sourceSet
            .listFiles()
            .takeIf { it.isNotEmpty() }
            ?.map { it.path }
            ?.let { fs.getJavaFileObjectsFromPaths(it) }
            ?: return CompilationResult(compiledClasses = emptyMap(), isSuccessful = true)

        val task = compiler.getTask(
            null,
            fs,
            null,
            options,
            null,
            javaFiles
        ) as JavacTask

        val classMapCollector = ClassMapCollector(request.sourceSet.rootPath)
        task.addTaskListener(classMapCollector)
        val res = task.call()


        return CompilationResult(
            compiledClasses = classMapCollector.classMap().mapKeys {
                JavaSourceFile(request.sourceSet.rootPath.resolve(it.key), it.key)
            },
            isSuccessful = res
        )
    }
}

private class ClassMapCollector(private val rootPath: Path): TaskListener {
    private val map = mutableMapOf<String,MutableSet<ClassName>>()

    fun classMap(): Map<String,Set<ClassName>> = map

    override fun finished(e: TaskEvent) {
        if (e.kind != TaskEvent.Kind.GENERATE) return
        val sourceFile: JavaFileObject? = e.sourceFile
        if (sourceFile == null || sourceFile.kind != JavaFileObject.Kind.SOURCE) return
        val typeElement = e.typeElement as? Symbol.TypeSymbol ?: return // skip everything but java types
        if (typeElement.flatName().isEmpty) return

        val relativePath = rootPath.relativize(File(sourceFile.name).toPath()).toString()
        val classSet = map.getOrPut(relativePath) { mutableSetOf() }
        classSet += ClassName(typeElement.flatName().toString())
    }
}

data class CompilationResult(
    val compiledClasses: Map<JavaSourceFile,Set<ClassName>>,
    val isSuccessful: Boolean
)
