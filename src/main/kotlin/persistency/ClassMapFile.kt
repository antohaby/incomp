package prj.incomp.persistency

import prj.incomp.java.ClassMap
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

//TODO: test
class ClassMapFile(
    private val path: Path
) {
    fun read(): ClassMap? {
        if (!Files.exists(path)) return null

        return Files.readAllLines(path)
            .associate {
                val (k, v) = it.split(';')
                k to v
            }
            .let(::ClassMap)
    }

    fun write(classMap: ClassMap) {
        val map = classMap.serialiseToMap()
        Files.newBufferedWriter(path, CREATE, TRUNCATE_EXISTING).use { writer ->
            for ((k, v) in map) {
                writer.appendLine("$k;$v")
            }
        }
    }
}