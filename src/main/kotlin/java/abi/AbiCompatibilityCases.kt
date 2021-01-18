package prj.incomp.java.abi

import prj.incomp.java.abi.Modifier.*
import prj.incomp.java.abi.CompatibilityLevel.*

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.1
 */
fun abstractClasses(old: ClassAbi, new: ClassAbi): CompatibilityLevel? = when {
    ABSTRACT.wasRemoved(old, new) -> COMPATIBLE
    ABSTRACT.wasAdded(old, new) -> INCOMPATIBLE
    else -> null
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.2
 */
fun finalClasses(old: ClassAbi, new: ClassAbi): CompatibilityLevel? = when {
    FINAL.wasAdded(old, new) -> INCOMPATIBLE
    FINAL.wasRemoved(old, new) -> COMPATIBLE
    else -> null
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.3
 */
fun publicClasses(old: ClassAbi, new: ClassAbi): CompatibilityLevel? = when {
    PUBLIC.wasAdded(old, new) -> COMPATIBLE
    PUBLIC.wasRemoved(old, new) -> INCOMPATIBLE
    else -> null
}

/**
 * TODO: add proper 13.4.4 handling
 *
 * Specification states that
 * Changing the direct superclass or the set of direct superinterfaces of a class type will not break compatibility
 * with pre-existing binaries, provided that the total set of superclasses or superinterfaces,
 * respectively, of the class type loses no members.
 *
 * Currently assume whenever superClass or interfaces set changes as incompatible change
 *
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.4
 */
fun superclassesAndSuperinterfaces(old: ClassAbi, new: ClassAbi): CompatibilityLevel? = when {
    old.superClass != new.superClass -> INCOMPATIBLE
    old.interfaces != new.interfaces -> INCOMPATIBLE
    else -> null
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.6
 */
fun fieldsCompatibility(
    old: ClassAbi,
    new: ClassAbi,
    checks: List<(old: ClassAbi.FieldAbi, new: ClassAbi.FieldAbi) -> CompatibilityLevel?>
): CompatibilityLevel {
    // should be ok to add new fields. TODO: check complex inheritance cases
    // val added = new.fieldsMap - old.fieldsMap

    val removed = old.fieldsMap - new.fieldsMap.keys
    val nonPrivateFieldWasRemoved = removed.any { it.value.accessModifier != AccessModifier.PRIVATE }
    if (nonPrivateFieldWasRemoved) return INCOMPATIBLE

    for (oldField in old.fields) {
        val newField = new.fieldsMap[oldField.name] ?: continue
        if (oldField.accessModifier == AccessModifier.PRIVATE) continue
        for (check in checks) {
            when (check(oldField, newField)) {
                // Early returns when Incompatible
                INCOMPATIBLE -> return INCOMPATIBLE
                GLOBALLY_INCOMPATIBLE -> return GLOBALLY_INCOMPATIBLE
                else -> Unit
            }
        }
    }

    return COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.7
 */
fun accessToFields(old: ClassAbi.FieldAbi, new: ClassAbi.FieldAbi): CompatibilityLevel =
    if (new.accessModifier < old.accessModifier) {
        INCOMPATIBLE
    } else {
        COMPATIBLE
    }

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.8
 */
fun staticFieldChange(old: ClassAbi.FieldAbi, new: ClassAbi.FieldAbi): CompatibilityLevel = when {
    STATIC in old.modifiers && STATIC !in new.modifiers -> INCOMPATIBLE
    STATIC !in old.modifiers && STATIC in new.modifiers -> INCOMPATIBLE
    else -> COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.9
 */
fun finalFieldChange(old: ClassAbi.FieldAbi, new: ClassAbi.FieldAbi): CompatibilityLevel = when {
    FINAL.wasRemoved(old.modifiers, new.modifiers) -> COMPATIBLE
    FINAL.wasAdded(old.modifiers, new.modifiers) -> INCOMPATIBLE
    else -> COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.9
 */
fun constantsChange(old: ClassAbi, new: ClassAbi): CompatibilityLevel {
    val isIncompatible = old.constantsMap.any { it.value != new.constantsMap[it.key] }
    return if (isIncompatible) {
        GLOBALLY_INCOMPATIBLE
    } else {
        COMPATIBLE
    }
}

fun newFieldThatOverrides(abiMap: Map<ClassName, ClassAbi>, old: ClassAbi, new: ClassAbi): CompatibilityLevel {
    val newFields = (new.fieldsMap - old.fieldsMap.keys).values
    if (newFields.isEmpty()) return COMPATIBLE

    val parents: List<ClassAbi> = abiMap.parentsOf(old.name)
    for (newField in newFields) {
        val overrideFound = parents.any { parent -> newField.name in parent.fieldsMap }
        if (overrideFound) return INCOMPATIBLE
    }

    return COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.12
 */
fun methodsCompatibility(
    old: ClassAbi,
    new: ClassAbi,
    checks: List<(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi) -> CompatibilityLevel?>
): CompatibilityLevel {
    // should be ok to add new fields. TODO: check complex inheritance cases
    // val added = new.fieldsMap - old.fieldsMap

    // Exclude <clinit> (static constructor) from removed, since it doesnt affect on compatibility
    val removed = (old.methodsMap - new.methodsMap.keys).filter { it.value.name != "<clinit>" }
    val nonPrivateMethodWasRemoved = removed.any { it.value.accessModifier != AccessModifier.PRIVATE }

    if (nonPrivateMethodWasRemoved) return INCOMPATIBLE

    for (oldMethod in old.methods) {
        val newMethod = new.methodsMap[oldMethod.signature] ?: continue
        if (oldMethod.accessModifier == AccessModifier.PRIVATE) continue
        for (check in checks) {
            when (check(oldMethod, newMethod)) {
                // Early returns when Incompatible
                INCOMPATIBLE -> return INCOMPATIBLE
                GLOBALLY_INCOMPATIBLE -> return GLOBALLY_INCOMPATIBLE
                else -> Unit
            }
        }
    }

    return COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.12
 */
fun accessToMethods(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi): CompatibilityLevel =
    if (new.accessModifier < old.accessModifier) {
        INCOMPATIBLE
    } else {
        COMPATIBLE
    }

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.14
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.15
 */
fun methodSignature(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi): CompatibilityLevel = when {
    old.name != new.name -> INCOMPATIBLE
    old.returnType != new.returnType -> INCOMPATIBLE
    // TODO: last argument can be vararg or array
    old.arguments != new.arguments -> INCOMPATIBLE
    else -> COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.16
 */
fun abstractMethodChange(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi): CompatibilityLevel = when {
    ABSTRACT.wasRemoved(old.modifiers, new.modifiers) -> COMPATIBLE
    ABSTRACT.wasAdded(old.modifiers, new.modifiers) -> INCOMPATIBLE
    else -> COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.17
 */
fun finalMethodChange(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi): CompatibilityLevel = when {
    FINAL.wasRemoved(old.modifiers, new.modifiers) -> COMPATIBLE
    STATIC !in old.modifiers && FINAL.wasAdded(old.modifiers, new.modifiers) -> INCOMPATIBLE
    else -> COMPATIBLE
}

/**
 * @see https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.19
 */
fun staticMethodChange(old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi): CompatibilityLevel = when {
    STATIC in old.modifiers && STATIC !in new.modifiers -> INCOMPATIBLE
    STATIC !in old.modifiers && STATIC in new.modifiers -> INCOMPATIBLE
    else -> COMPATIBLE
}

fun newMethodThatOverrides(abiMap: Map<ClassName, ClassAbi>, old: ClassAbi, new: ClassAbi): CompatibilityLevel {
    val newMethods = (new.methodsMap - old.methodsMap.keys).values
    if (newMethods.isEmpty()) return COMPATIBLE

    val parents: List<ClassAbi> = abiMap.parentsOf(old.name)
    for (newMethod in newMethods) {
        val overrideFound = parents.any { parent -> newMethod.signature in parent.methodsMap }
        if (overrideFound) return INCOMPATIBLE
    }

    return COMPATIBLE
}

private fun Modifier.wasRemoved(old: ClassAbi, new: ClassAbi) = wasRemoved(old.modifiers, new.modifiers)
private fun Modifier.wasAdded(old: ClassAbi, new: ClassAbi) = wasAdded(old.modifiers, new.modifiers)

private fun Modifier.wasRemoved(old: Set<Modifier>, new: Set<Modifier>) = this in old && this !in new
private fun Modifier.wasAdded(old: Set<Modifier>, new: Set<Modifier>) = this !in old && this in new

private fun Map<ClassName, ClassAbi>.parentsOf(className: ClassName): List<ClassAbi> {
    var currentParent: ClassAbi? = get(className)
    val parents = mutableListOf<ClassAbi>()

    while (currentParent != null) {
        parents += currentParent
        currentParent = get(currentParent.superClass)
    }

    return parents
}