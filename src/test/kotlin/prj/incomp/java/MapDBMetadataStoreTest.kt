package prj.incomp.java

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mapdb.DB
import org.mapdb.DBMaker
import java.io.File

internal class MapDBMetadataStoreTest {

    private lateinit var db: DB
    private lateinit var store: MapDBMetadataStore

    @BeforeEach
    fun init() {
        db = DBMaker.tempFileDB()
            .transactionEnable()
            .make()
        store = MapDBMetadataStore(db)
    }

    private val defaultContext = JavaCompilationContext(
        currentTime = 0,
        sourceSet = JavaFsSourceSet("/tmp"),
        targetLocation = File("/tmp").toPath()
    )

    @Test
    fun `it should return empty metadata if file is missing`() {
        val metadata = store.loadOrDefault(defaultContext)
        assertTrue(metadata.dependants.isEmpty())
    }

    @Test
    fun testStoreNLoad() {
        val metadata = IncrementalCompilationMetadata(
            lastCompileTime = 42,
            dependencies = mapOf(
                RelativeSourcePath("/tmp/foo") to setOf(RelativeSourcePath("/tmp/bar"))
            ),
            dependants = mapOf(
                RelativeSourcePath("/tmp/bar") to setOf(RelativeSourcePath("/tmp/foo"))
            ),
            sourceFileAstHash = mapOf()
        )
        store.store(defaultContext, metadata)
        val loadedMetadata = store.loadOrDefault(defaultContext)

        assertEquals(metadata, loadedMetadata)
    }

}