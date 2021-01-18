package prj.incomp.java

import prj.incomp.java.abi.ClassAbi
import prj.incomp.java.abi.ClassName

// TODO: test
/**
 * @param adjacencyMap states on which classes a class depends on
 */
data class ClassDependencyGraph (
    val adjacencyMap: Map<ClassName,ClassDependency>
) {
    private val reverseAdjacencyMap: Map<ClassName, ClassDependency> by lazy {
        val map = mutableMapOf<ClassName,Pair<MutableSet<ClassName>,MutableSet<ClassName>>>()

        for ((className, deps) in adjacencyMap) {
            for (dep in deps.internal) {
                val (internal, _) = map.getOrPut(dep) { mutableSetOf<ClassName>() to mutableSetOf() }
                internal += className
            }
            for (dep in deps.exposed) {
                val (_, exposed) = map.getOrPut(dep) { mutableSetOf<ClassName>() to mutableSetOf() }
                exposed += className
            }
        }
        
        map.mapValues {
            ClassDependency(
                internal = it.value.first,
                exposed = it.value.second
            )
        }
    }

    data class ClassDependency(
        val internal: Set<ClassName>,
        val exposed: Set<ClassName>
    ) {
        val all get() = internal + exposed
    }

    fun dependenciesOf(className: ClassName): ClassDependency? = adjacencyMap[className]

    fun dependantsOf(className: ClassName): ClassDependency? = reverseAdjacencyMap[className]

    companion object {
        fun fromAbiMap(abiMap: Map<ClassName,ClassAbi>): ClassDependencyGraph {
            val adjacencyMap = abiMap.mapValues { ClassDependency(it.value.internal, it.value.exposed) }
            return ClassDependencyGraph(adjacencyMap)
        }
    }
}
