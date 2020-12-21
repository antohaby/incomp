package prj.incomp.java

import org.mapdb.DB
import org.mapdb.HTreeMap
import org.mapdb.Serializer

class MapDBMetadataStore(
    db: DB,
    private val fileSeparator: String = ";"
) : JavaMetadataStore {
    private val lastCompileTime = db.atomicLong("lastCompileTime", 0).createOrOpen()
    private val dependants = db.hashMap("dependants", Serializer.STRING, Serializer.STRING).createOrOpen()
    private val dependencies = db.hashMap("dependencies", Serializer.STRING, Serializer.STRING).createOrOpen()

    private fun HTreeMap<String, String>.toDependenciesMap(): Map<RelativeSourcePath, Set<RelativeSourcePath>> = this
        .mapKeys { RelativeSourcePath(it.key) }
        .mapValues { (_, value) -> value.split(fileSeparator).map { RelativeSourcePath(it) }.toSet() }

    override fun loadOrDefault(sourceSet: JavaCompilationContext): IncrementalCompilationMetadata {
        return IncrementalCompilationMetadata(
            lastCompileTime = lastCompileTime.get(),
            dependants = dependants.toDependenciesMap(),
            dependencies = dependencies.toDependenciesMap(),
            sourceFileAstHash = mapOf(),
        )
    }

    override fun store(sourceSet: JavaCompilationContext, metadata: IncrementalCompilationMetadata) {
        lastCompileTime.set(metadata.lastCompileTime)
        dependants.clear()
        metadata.dependants.forEach { (key, deps) ->
            val depsString = deps.joinToString(fileSeparator) { it.stringPath }
            dependants[key.stringPath] = depsString
        }

        dependencies.clear()
        metadata.dependencies.forEach { (key, deps) ->
            val depsString = deps.joinToString(fileSeparator) { it.stringPath }
            dependencies[key.stringPath] = depsString
        }
    }
}