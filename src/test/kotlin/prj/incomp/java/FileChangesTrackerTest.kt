package prj.incomp.java

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import prj.incomp.persistency.SourceHashesFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

@ExtendWith(MockKExtension::class)
internal class FileChangesTrackerTest {
    private val fs = Jimfs.newFileSystem(Configuration.unix())
    private val root = fs.getPath("/root").also { Files.createDirectory(it) }

    private val store = SourceHashesFile(fs.getPath("store.txt"))
    private val sourceSet = PathSourceSet(root)

    @Test
    fun `it can detect changed files`() {
        // Given
        files("a", "b", "c")
        assertChanges {
            assertAdded("a", "b", "c")
            assertChanged()
            assertRemoved()
        }

        // When
        change("a", "b")

        // Then
        assertChanges {
            assertAdded()
            assertChanged("a", "b")
            assertRemoved()
        }
    }

    @Test
    fun `it can detect added files`() {
        // Given
        files("a")
        assertChanges { assertAdded("a") }

        // When
        change("b", "c")

        // Then
        assertChanges {
            assertChanged()
            assertAdded("b", "c")
            assertRemoved()
        }
    }

    @Test
    fun `it can detect deleted files`() {
        // Given
        files("a", "b", "c")
        assertChanges { assertAdded("a", "b", "c") }

        // When
        change("a")
        remove("b")

        // Then
        assertChanges {
            assertAdded()
            assertChanged("a")
            assertRemoved("b")
        }
    }

    @Test
    fun `it can detect incremental changes`() {
        // Start with empty
        files()
        assertChanges {
            assertAdded()
            assertChanged()
            assertRemoved()
        }

        // Add few files
        change("a", "b")
        change("a", "c")
        assertChanges {
            assertAdded("a", "b", "c")
            assertChanged()
            assertRemoved()
        }

        // Change some files
        remove("a")
        change("b", "d")
        assertChanges {
            assertAdded("d")
            assertChanged("b")
            assertRemoved("a")
        }

    }

    private fun files(vararg files: String) {
        files.forEach { file -> Files.writeString(root.resolve("$file.java"), file, StandardOpenOption.CREATE) }
    }

    private fun change(vararg files: String) {
        files.forEach { file -> Files.writeString(root.resolve("$file.java"), UUID.randomUUID().toString()) }
    }

    private fun remove(vararg files: String) {
        files.forEach { file -> Files.delete(root.resolve("$file.java")) }
    }


    private fun listChanged(): ChangedSourceSet {
        val tracker = FileChangesTracker(sourceHashStore = store)
        val tx = tracker.begin(sourceSet)
        return tx.listChangedFiles().also { tx.commit() }
    }

    private fun ChangedSourceSet.assertChanged(vararg files: String) {
        assertEquals(
            files.map { file -> "$file.java" },
            changed.map { it.relativePath }
        )
    }

    private fun ChangedSourceSet.assertAdded(vararg files: String) {
        assertEquals(
            files.map { file -> "$file.java" },
            added.map { it.relativePath }
        )
    }

    private fun ChangedSourceSet.assertRemoved(vararg files: String) {
        assertEquals(
            files.map { file -> "$file.java" },
            removed
        )
    }

    private fun assertChanges(assert: ChangedSourceSet.() -> Unit) {
        val changedSet = listChanged()
        changedSet.assert()
    }

}