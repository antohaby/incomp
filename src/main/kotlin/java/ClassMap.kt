package prj.incomp.java

import prj.incomp.java.abi.ClassName

class ClassMap(
    init: Map<String,String> = emptyMap() // ClassName to SourceFile
) {
    private val classToSource = init
        .mapKeys { ClassName(it.key) }
        .toMutableMap()

    private val sourceToClass = init
        .entries
        .groupBy { it.value }
        .mapValues { (_, entries) ->
            entries
                .map { ClassName(it.key) }
                .toMutableSet()
        }
        .toMutableMap()

    fun serialiseToMap(): Map<String,String> = classToSource.mapKeys { it.key.qualifiedName }

    fun classesOf(sourcePath: String): Set<ClassName> = sourceToClass[sourcePath] ?: error("Illegal access")
    fun sourceFileOf(className: ClassName): String = classToSource[className] ?: error("Illegal access")

    fun removeSourceFile(source: String): Set<ClassName> {
        val classes = sourceToClass[source] ?: emptySet()
        sourceToClass -= source
        classToSource -= classes

        return classes
    }

    fun registerSourceClassRelationship(source: String, classes: Set<ClassName>) {
        classes.forEach { className -> classToSource[className] = source }
        sourceToClass[source] = classes.toMutableSet()
    }
}