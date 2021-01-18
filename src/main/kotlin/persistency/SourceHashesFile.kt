package prj.incomp.persistency

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// TODO: Test
class SourceHashesFile(
    private val path: Path
) {
    fun read(): Map<String, String>? = path
        .takeIf(Files::exists)
        ?.let(Files::readAllLines)
        ?.associate {
            val (fileName, hash) = it.split(';')
            fileName to hash
        }

    fun write(value: Map<String, String>) {
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            value.forEach { writer.appendLine("${it.key};${it.value}") }
        }
    }
}