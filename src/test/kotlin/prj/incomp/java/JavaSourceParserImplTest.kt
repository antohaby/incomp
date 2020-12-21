package prj.incomp.java

import java.io.File
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class JavaSourceParserImplTest {
    private val rootPath = File(javaClass.classLoader.getResource("test-project/src")!!.path).toPath()
    private val parser = JavaSourceParserImpl(
        rootPath = rootPath
    )

    @Test
    fun `it should identify implicit class signature dependencies`() {
        // C5 has dependency to C2 through C4 so we should expect it
        val dependencies = parser.parseDependencies(rootPath.resolve("root/C5.java").toFile())
            ?.map { it.name }
            ?.toSet()

        assertEquals(
            setOf("root.C5", "root.C2", "root.C4"),
            dependencies
        )
    }
}