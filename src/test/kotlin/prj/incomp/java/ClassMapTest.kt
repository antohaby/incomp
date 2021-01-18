package prj.incomp.java

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import prj.incomp.java.abi.ClassName

internal class ClassMapTest {

    private fun classesOf(vararg qualifiedNames: String) = qualifiedNames.map { ClassName(it) }.toSet()

    @Test
    fun `it stores both way source-class relationship`() {
        val classMap = ClassMap(
            mapOf(
                "p.F" to "foo",
                "p.B" to "bar",
                "p.F$1" to "foo",
            )
        )
        classMap.registerSourceClassRelationship("a", classesOf("p.X", "p.X$1", "p.X$2"))
        classMap.registerSourceClassRelationship("b", classesOf("p.Y"))

        assertEquals(classesOf("p.F", "p.F$1"), classMap.classesOf("foo"))
        assertEquals(classesOf("p.B"), classMap.classesOf("bar"))
        assertEquals(classesOf("p.X", "p.X$1", "p.X$2"), classMap.classesOf("a"))
        assertEquals(classesOf("p.Y"), classMap.classesOf("b"))


        assertEquals("b", classMap.sourceFileOf(ClassName("p.Y")))
        assertEquals("a", classMap.sourceFileOf(ClassName("p.X$1")))
        assertEquals("a", classMap.sourceFileOf(ClassName("p.X")))
        assertEquals("foo", classMap.sourceFileOf(ClassName("p.F")))
    }
}