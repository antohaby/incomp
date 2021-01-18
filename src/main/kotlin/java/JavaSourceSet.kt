package prj.incomp.java

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

interface JavaSourceSet {
    val rootPath: Path
    fun listFiles(): List<JavaSourceFile>
}

data class JavaSourceFile(
    val path: Path,
    val relativePath: String
)

class PathSourceSet(
    override val rootPath: Path
): JavaSourceSet {
    override fun listFiles(): List<JavaSourceFile> = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
        .asSequence()
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
        .map { JavaSourceFile(it, rootPath.relativize(it).toString()) }
        .toList()

}

class FileListSourceSet(
    override val rootPath: Path,
    private val files: List<JavaSourceFile>
) : JavaSourceSet {
    override fun listFiles(): List<JavaSourceFile> = files
}