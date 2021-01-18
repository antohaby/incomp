package prj.incomp.java

import prj.incomp.persistency.SourceHashesFile
import java.nio.file.Files
import java.nio.file.Path

class FileChangesTracker(
    private val sourceHashStore: SourceHashesFile,
    private val calcHash: JavaSourceFile.() -> String = { Files.getLastModifiedTime(path).toString() }
) {
    fun begin(sourceSet: JavaSourceSet) = Transaction(
        sourceSet = sourceSet,
        newSourceHashes = sourceHashStore.read(),
        sourceMapStore = sourceHashStore,
        calcHash = calcHash
    )

    class Transaction(
        private val sourceSet: JavaSourceSet,
        private var newSourceHashes: Map<String,String>? = null,
        private val sourceMapStore: SourceHashesFile,
        private val calcHash: JavaSourceFile.() -> String
    ) {

        fun commit() {
            newSourceHashes?.let { sourceMapStore.write(it) }
        }

        fun rollback() {
            newSourceHashes = null
        }

        fun listChangedFiles(): ChangedSourceSet {
            val sourceHashes = newSourceHashes
            if (sourceHashes == null) {
                val files = sourceSet.listFiles()
                newSourceHashes = files.associate { it.relativePath to it.calcHash() }
                return ChangedSourceSet(
                    originalSourceSet = sourceSet,
                    added = files,
                    changed = listOf(),
                    removed = listOf()
                )
            }

            val added = mutableListOf<JavaSourceFile>()
            val changed = mutableListOf<JavaSourceFile>()

            val files = sourceSet.listFiles()
            val newSourceHashes = mutableMapOf<String, String>()
            for (sourceFile in files) {
                val oldHash = sourceHashes[sourceFile.relativePath]
                val newHash = sourceFile.calcHash()

                newSourceHashes[sourceFile.relativePath] = newHash

                if (newHash == oldHash) continue

                if (oldHash == null) {
                    added += sourceFile
                } else {
                    changed += sourceFile
                }
            }

            val removed = sourceHashes.keys - files.map { it.relativePath }

            this.newSourceHashes = newSourceHashes
            return ChangedSourceSet(
                originalSourceSet = sourceSet,
                added = added,
                changed = changed,
                removed = removed.toList()
            )
        }
    }
}

class ChangedSourceSet(
    val originalSourceSet: JavaSourceSet,
    val added: List<JavaSourceFile>,
    val changed: List<JavaSourceFile>,
    val removed: List<String>
): JavaSourceSet by originalSourceSet {
    fun isEmpty(): Boolean = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
    override fun listFiles(): List<JavaSourceFile> = added + changed
}
