package prj.incomp.java

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Test
import prj.incomp.java.abi.*
import prj.incomp.persistency.SourceHashesFile
import prj.incomp.persistency.ClassMapFile
import java.nio.file.FileSystems
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import javax.tools.ToolProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IncrementalCompilationTest {
    @Test
    fun `no changes`() {
        testIncrementalCompilation {
            initialSources = {
                "class A {}" saveAs "a"
                "class B {}" saveAs "b"
            }

            sourceChanges = {
                // Nothing
            }

            assertCompilationResults = {
                compiledOk()
            }
        }
    }

    @Test
    fun `new file`() {
        // Also covers first case of https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.3
        testIncrementalCompilation {
            initialSources = {
                "class A {}" saveAs "a"
            }

            sourceChanges = {
                "class B {}" saveAs "b"
            }

            assertCompilationResults = {
                compiledOk("b")
            }
        }
    }

    @Test
    fun `change class without dependants`() {
        testIncrementalCompilation {
            initialSources = {
                "class A {}" saveAs "a"
                "class B {}" saveAs "b"
            }

            sourceChanges = {
                "class B {}" saveAs "b"
            }

            assertCompilationResults = {
                compiledOk("b")
            }
        }
    }

    @Test
    fun `change class with dependants`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public void foo() {} }" saveAs "a"
                "class B { public A a; }" saveAs "b"
                "class C { public B b; }" saveAs "c"
            }

            sourceChanges = {
                "class A { }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { public void foo() {} public void bar() {} }" saveAs "a"
                "class B { public A a; }" saveAs "b"
                "class C { public B b; private void foo() { b.a.foo(); } }" saveAs "c"
            }

            sourceChanges = {
                "class A { public void foo() {} }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b", "c")
            }
        }
    }

    @Test
    fun `change class with dependants to class with the same public ABI`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public void foo() {} }" saveAs "a"
                "class B { public A a; }" saveAs "b"
                "class C { public B b; }" saveAs "c"
            }

            sourceChanges = {
                "class A { public void foo() { int y = x * x; }; private int x = 12; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    @Test
    fun `remove file`() {
        testIncrementalCompilation {
            initialSources = {
                "package foo; class A { public int foo() { return 12; } }" saveAs "foo/a"
                "package foo; public class B extends A { }" saveAs "foo/B"
                "class C extends foo.B { }" saveAs "C"
            }

            sourceChanges = {
                remove("foo/a")
            }

            assertCompilationResults = {
                compilationFailed()
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "package foo; class A { public int foo() { return 12; } }" saveAs "foo/a"
                "package foo; public class B extends A { }" saveAs "foo/B"
                "class C extends foo.B { }" saveAs "C"
            }

            sourceChanges = {
                remove("foo/a")
                "package foo; public class B { }" saveAs "foo/B"
            }

            assertCompilationResults = {
                compiledOk("foo/B", "C")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.1
     */
    @Test
    fun `abstract classes`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "abstract class A { }" saveAs "a"
            }

            assertCompilationResults = {
                // TODO: this can be improved, actually B may not be compiled, since there is no instantiation of A
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "abstract class A { }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.2
     */
    @Test
    fun `final classes`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "final class A { }" saveAs "a"
            }

            assertCompilationResults = {
                // TODO: this can be improved, actually B may not be compiled, since it is not ancestor of A
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "final class A { }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.3
     */
    @Test
    fun `public classes`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { }" saveAs "A"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "public class A { }" saveAs "A"
            }

            assertCompilationResults = {
                compiledOk("A")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "public class A { }" saveAs "A"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { }" saveAs "A"
            }

            assertCompilationResults = {
                // TODO: this can be improved, actually B may not be re-compiled, since it has same access level as A
                compiledOk("A", "b")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.4
     */
    @Test
    fun `Superclasses and Superinterfaces`() {
        testIncrementalCompilation {
            initialSources = {
                "interface A { }" saveAs "a"
                "interface B { }" saveAs "b"
                "interface C extends A, B { }" saveAs "c"
            }

            sourceChanges = {
                "interface A extends C { }" saveAs "a"
            }

            assertCompilationResults = {
                compilationFailed()
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "interface A { }" saveAs "a"
                "interface B extends A { }" saveAs "b"
                "interface C extends B { }" saveAs "c"
            }

            sourceChanges = {
                "interface A extends C { }" saveAs "a"
            }

            assertCompilationResults = {
                compilationFailed()
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "interface A { }" saveAs "a"
                "interface B extends A { }" saveAs "b"
                "interface C extends B { }" saveAs "c"
            }

            sourceChanges = {
                "interface A extends D { }" saveAs "a"
                "interface D { }" saveAs "d"
            }

            assertCompilationResults = {
                compiledOk("a", "b", "d")
            }
        }

    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.6
     */
    @Test
    fun `Class Body and Member Declarations`() {
        testIncrementalCompilation {
            initialSources = {
                "class A {  }" saveAs "a"
                "class B extends A { }" saveAs "b"
            }

            sourceChanges = {
                "class A { public static void a() {}; static void b() {}; static int c = 12; int d; void e() {}; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { void foo() {}; }" saveAs "a"
                "class B extends A { }" saveAs "b"
            }

            sourceChanges = {
                "class A { public static void notFoo() {}; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }
    }


    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.7
     */
    @Test
    fun `Access to Members and Constructors`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { protected int x; }" saveAs "a"
                "class B { private void foo() { A a = new A(); } }" saveAs "b"
            }

            sourceChanges = {
                "class A { private int x; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { protected int x; private int y; }" saveAs "a"
                "class B { private void foo() { A a = new A(); } }" saveAs "b"
            }

            sourceChanges = {
                "class A { public int x; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    @Test
    fun `Field Declarations`() {
        // TODO: Fix this test, by including dependants of all parents
        // and we can check if fields are re-declared
        testIncrementalCompilation {
            initialSources = {
                """class A { String s = "xxx"; }""" saveAs "a"
                """class B extends A { }""" saveAs "b"
                """class C { B b = new B(); }""" saveAs "c"
                """class D { public void foo() { String x = new C().b.s; } }""" saveAs "d"
            }

            sourceChanges = {
                """class B extends A { int s = 42; }""" saveAs "b"
            }

            assertCompilationResults = {
                compilationFailed()
            }
        }
    }

    @Test
    fun `field removal`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public String s; }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.9
     */
    @Test
    fun `final Fields`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public String s; }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { public final String s = \"xxx\"; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { public final String s; public A(String x) { s = x; } }" saveAs "a"
                "class B { A a; }" saveAs "b"
            }

            sourceChanges = {
                "class A { public String s; public A(String x) { s = x; } }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.9
     */
    @Test
    fun `static constant variables`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public static final int x = 12; }" saveAs "a"
                "class B { A a; }" saveAs "b"
                "class C { }" saveAs "c"
            }

            sourceChanges = {
                "class A { public static final int x = 42; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b", "c")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { public static final int x = 12; }" saveAs "a"
                "class B { A a; }" saveAs "b"
                "class C { }" saveAs "c"
            }

            sourceChanges = {
                "class A { }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b", "c")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { }" saveAs "a"
                "class B { A a; }" saveAs "b"
                "class C { }" saveAs "c"
            }

            sourceChanges = {
                "class A { public static final int x = 12; }" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.10
     */
    @Test
    fun `static fields`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { public static int x = 12; }" saveAs "a"
                "class B { A a; }" saveAs "b"

                "class C { public int x = 12; }" saveAs "c"
                "class D { C c; }" saveAs "d"
            }

            sourceChanges = {
                "class A { public int x = 12; }" saveAs "a"
                "class C { public static int x = 12; }" saveAs "c"
            }

            assertCompilationResults = {
                compiledOk("a", "b", "c", "d")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                "class A { private static int x = 12; }" saveAs "a"
                "class B { A a; }" saveAs "b"

                "class C { private int x = 12; }" saveAs "c"
                "class D { C c; }" saveAs "d"
            }

            sourceChanges = {
                "class A { private int x = 12; }" saveAs "a"
                "class C { private static int x = 12; }" saveAs "c"
            }

            assertCompilationResults = {
                compiledOk("a", "c")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.11
     */
    @Test
    fun `transient fields`() {
        testIncrementalCompilation {
            initialSources = {
                "class A { transient Object x; }" saveAs "a"
                "class B { Object x; }" saveAs "b"
                "class C { A a; B b; }" saveAs "c"
            }

            sourceChanges = {
                "class A { Object x; }" saveAs "a"
                "class B { transient Object x; }" saveAs "b"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.12
     */
    @Test
    fun `Method and Constructor Declarations`() {
        testIncrementalCompilation {
            initialSources = {
                """class A { String x() { return "xxx"; } }""" saveAs "a"
                """class B extends A { }""" saveAs "b"
                """class C { B b() { return new B(); } }""" saveAs "c"
                """class D { void foo() { String x = new C().b().x(); } }""" saveAs "d"
            }

            sourceChanges = {
                """class B extends A { String x() { return "yyy"; } }""" saveAs "b"
            }

            assertCompilationResults = {
                compiledOk("b", "c", "d")
            }
        }
    }

    @Test
    fun `method removal`() {
        testIncrementalCompilation {
            initialSources = {
                """class A { String x() { return "xxx"; } }""" saveAs "a"
                """class B extends A { }""" saveAs "b"
            }

            sourceChanges = {
                """class A { }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                """class A { private String x() { return "xxx"; } }""" saveAs "a"
                """class B extends A { }""" saveAs "b"
            }

            sourceChanges = {
                """class A { }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.14
     */
    @Test
    fun `Method and Constructor Formal Parameters`() {
        testIncrementalCompilation {
            initialSources = {
                """class A { String x(String a) { return a; } }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { String x(String b) { return b; } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                """class A { String x(String a) { return a; } }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { String x(String b, String c) { return b + c; } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                """class A { int x(int a) { return a; } }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { int x(int a) { return a; } int x(int b, int c) { return b + c; } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }

        testIncrementalCompilation {
            initialSources = {
                """class A { int x(int[] a) { return a[0]; } }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { int x(int... a) { return a[0]; } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.15
     */
    @Test
    fun `Method Result Type`() {
        testIncrementalCompilation {
            initialSources = {
                """class A { void x(String a) { } }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { String x(String b) { return b; } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a", "b")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.16
     */
    @Test
    fun `abstract Methods`() {
        testIncrementalCompilation {
            initialSources = {
                """abstract class A { abstract void x(String a); }""" saveAs "a"
                """class B { A a; }""" saveAs "b"

                """class C { void x(String a) {} }""" saveAs "c"
                """class D { C c; }""" saveAs "d"
            }

            sourceChanges = {
                """abstract class A { void x(String a) {} }""" saveAs "a"
                """abstract class C { abstract void x(String a); }""" saveAs "c"
            }

            assertCompilationResults = {
                compiledOk("a", "c", "d")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.17
     */
    @Test
    fun `final Methods`() {
        testIncrementalCompilation {
            initialSources = {
                """class A { final void x(String a) {} }""" saveAs "a"
                """class B { A a; }""" saveAs "b"

                """class C { void x(String a) {} }""" saveAs "c"
                """class D { C c; }""" saveAs "d"
            }

            sourceChanges = {
                """class A { void x(String a) {} }""" saveAs "a"
                """class C { final void x(String a) {} }""" saveAs "c"
            }

            assertCompilationResults = {
                compiledOk("a", "c", "d")
            }
        }
    }


    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.17
     */
    @Test
    fun `Method and Constructor Throws` () {
        testIncrementalCompilation {
            initialSources = {
                """class A { void x() throws E1 {} }""" saveAs "a"
                """class B { A a; }""" saveAs "b"

                """class E1 extends Exception { }""" saveAs "e1"
            }

            sourceChanges = {
                """class A { void x() throws E1, E2 {} }""" saveAs "a"

                """class E2 extends Exception { }""" saveAs "e2"
            }

            assertCompilationResults = {
                compiledOk("a", "e2")
            }
        }
    }

    /**
     * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.17
     */
    @Test
    fun `Method and Constructor Body` () {
        testIncrementalCompilation {
            initialSources = {
                """class A { void x() {} private void y() {} }""" saveAs "a"
                """class B { A a; }""" saveAs "b"
            }

            sourceChanges = {
                """class A { void x() { y(); } private void y() { x(); } }""" saveAs "a"
            }

            assertCompilationResults = {
                compiledOk("a")
            }
        }
    }

}

private fun testIncrementalCompilation(code: TestSpec.() -> Unit) {

    val spec = TestSpec().apply(code)
    try {
        val request = CompilationRequest(
            sourceSet = PathSourceSet(spec.sourcePath),
            classpath = spec.makeClassPath(),
            destination = spec.targetPath,
        )

        val sourceHashesFile = SourceHashesFile(spec.tmpDir.resolve("source-hashes.txt"))
        val classMapFile = ClassMapFile(spec.tmpDir.resolve("class-map.txt"))

        val compiler = IncrementalCompilation(
            fileChangesTracker = FileChangesTracker(sourceHashesFile),
            abiProvider = AsmAbiProvider,
            abiCompatibility = java8SpecIncompatibilityChecker(),
            classMapStore = classMapFile,
            compiler = JdkCompiler(ToolProvider.getSystemJavaCompiler())
        )

        spec.initFs()
        val firstTimeCompilationResult = compiler.runCompilation(request)

        spec.applyFsChanges()
        val secondTimeCompilationResult = compiler.runCompilation(request)

        spec.assert(TestSpec.Results(firstTimeCompilationResult, secondTimeCompilationResult))
    } finally {
        spec.cleanUp()
    }
}

private class TestSpec {
    // TODO: implement FileManager for JDK compiler that will be Jimfs friendly
    // val fs = Jimfs.newFileSystem(Configuration.unix())
    // val fs = FileSystems.getDefault()

    val tmpDir = Files.createTempDirectory("java-incomp-tests")

    val sourcePath = tmpDir.resolve("sources")
    val libsPath = tmpDir.resolve("libs")
    val targetPath = tmpDir.resolve("classes")

    var initialSources: (TestSpec.() -> Unit)? = null
    var sourceChanges: (TestSpec.() -> Unit)? = null
    var assertCompilationResults: (Results.() -> Unit)? = null

    class Results(
        val firstCompilation: CompilationResult,
        val secondCompilation: CompilationResult
    ) {
        fun compiledOk(vararg sourceFiles: String) {
            assertTrue(firstCompilation.isSuccessful, "First compilation must be successful")
            assertTrue(secondCompilation.isSuccessful,"Second compilation must be successful")

            val compiled = secondCompilation
                .compiledClasses
                .keys
                .map { it.relativePath }
                .toSet()

            val expected = sourceFiles
                .map { "$it.java" }
                .toSet()

            assertEquals(expected, compiled)
        }

        fun compilationFailed() {
            assertFalse(secondCompilation.isSuccessful, "Second compilation must fail")
        }
    }

    infix fun String.saveAs(fileName: String) {
        val filePath = sourcePath.resolve("$fileName.java")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, this)
    }

    fun remove(fileName: String) {
        Files.delete(sourcePath.resolve("$fileName.java"))
    }

    fun makeClassPath() = JavaClasspath.parse("/libs")

    fun initFs() {
        Files.createDirectories(sourcePath)
        Files.createDirectories(targetPath)
        Files.createDirectories(libsPath)
        initialSources?.invoke(this)
    }

    fun applyFsChanges() = sourceChanges?.invoke(this)
    fun cleanUp() = Files.walk(tmpDir)
        .sorted(Comparator.reverseOrder())
        .forEach(Files::delete)

    fun assert(results: Results) {
        assertCompilationResults?.invoke(results)
    }
}
