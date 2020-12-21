package prj.incomp.java

import java.io.File
import java.nio.file.Path
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject

/**
 * File System source set
 */
data class JavaFsSourceSet(
    val path: Path
) {
    constructor(stringPath: String) : this(File(stringPath).toPath())

    init {
        val file = path.toFile()
        require(file.exists() && file.isDirectory) { "Java File System Source Set must be an existing directory" }
    }
}

class JavaFile(
    val file: File
) : SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
    init {
        require(file.exists()) { "Java File must exists" }
        require(file.isFile) { "Java File must be a valid file" }
        require(file.extension == "java") { "Java File must have '.java' extension" }
    }

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
        file.readText()

    override fun getLastModified(): Long = file.lastModified()
}

// TODO: test
fun JavaCompilationContext.findClassFile(sourceFile: File): File? {
    val classFileName = "${sourceFile.nameWithoutExtension}.class"

    val relativeClassFilePath = sourceSet.path
        .relativize(sourceFile.toPath())
        .resolveSibling(classFileName)

    val classFile = targetLocation
        .resolve(relativeClassFilePath)
        .toFile()

    return if (classFile.exists()) {
        classFile
    } else {
        null
    }
}

fun JavaCompilationContext.listSourceFiles(): Sequence<JavaCompilationUnit> = sourceSet
    .path.toFile()
    .walkTopDown()
    .asSequence()
    .filter { it.isFile && it.extension == "java" }
    .map { sourceFile ->
        JavaCompilationUnit(
            pathInSourceSet = RelativeSourcePath(sourceSet.path.relativize(sourceFile.toPath())),
            sourceFile = sourceFile,
            classFile = findClassFile(sourceFile)
        )
    }

fun JavaCompilationContext.findSingle(pathInSourceSet: String): JavaCompilationUnit? =
    findSingle(fileSystem.getPath(pathInSourceSet))

fun JavaCompilationContext.findSingle(pathInSourceSet: Path): JavaCompilationUnit? {
    val file = sourceSet.path.resolve(pathInSourceSet).toFile()
    return if (file.exists() && file.extension == "java") {
        JavaCompilationUnit(
            pathInSourceSet = RelativeSourcePath(pathInSourceSet),
            sourceFile = file,
            classFile = findClassFile(file)
        )
    } else {
        null
    }
}

fun JavaCompilationUnit.sourceLastModified() = sourceFile.lastModified()

fun JavaCompilationUnit.targetLastModified() = classFile?.lastModified()