package prj.incomp.java

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

data class CompilationRequest(
    val sourceSet: JavaSourceSet,
    val classpath: JavaClasspath,
    val destination: Path
)

data class JavaClasspath(
    val entries: List<Entry>
) {
    data class Entry(
        val path: Path
    )

    override fun toString(): String = entries.joinToString(":") { it.path.toString() }

    companion object {
        fun parse(string: String, fs: FileSystem = FileSystems.getDefault()): JavaClasspath {
            val entries = string
                .split(':')
                .map { Entry(fs.getPath(it)) }

            return JavaClasspath(entries)
        }
    }
}
