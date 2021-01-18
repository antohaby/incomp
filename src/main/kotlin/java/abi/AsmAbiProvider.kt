package prj.incomp.java.abi

import org.objectweb.asm.ClassReader
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

object AsmAbiProvider {
    fun provide(classRoot: Path): Map<ClassName, ClassAbi> {
        return Files.walk(classRoot, FileVisitOption.FOLLOW_LINKS)
            .asSequence()
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
            .map { it
                .let(Files::newInputStream)
                .let(::ClassReader)
                .let(ClassAbiExtractor.Companion::extract)
            }
            .associateBy { it.name }
    }
}