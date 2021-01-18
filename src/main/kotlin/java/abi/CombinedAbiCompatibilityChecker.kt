package prj.incomp.java.abi

import prj.incomp.java.abi.CompatibilityLevel.*

enum class CompatibilityLevel {
    COMPATIBLE,
    INCOMPATIBLE,
    GLOBALLY_INCOMPATIBLE // for example when constant changed
}

typealias ClassAbiCompatibilityCheck = (old: ClassAbi, new: ClassAbi) -> CompatibilityLevel?
typealias FieldAbiCompatibilityCheck = (old: ClassAbi.FieldAbi, new: ClassAbi.FieldAbi) -> CompatibilityLevel?
typealias MethodAbiCompatibilityCheck = (old: ClassAbi.MethodAbi, new: ClassAbi.MethodAbi) -> CompatibilityLevel?

typealias ClassAbiCompatibilityCheckWithAbiMap = (
    abiMap: Map<ClassName,ClassAbi>,
    old: ClassAbi,
    new: ClassAbi
) -> CompatibilityLevel?

class CombinedAbiCompatibilityChecker(
    private val checks: List<ClassAbiCompatibilityCheck>,
    private val checksWithAbiMap: List<ClassAbiCompatibilityCheckWithAbiMap>
) {
    fun isCompatible(abiMap: Map<ClassName,ClassAbi>, old: ClassAbi, new: ClassAbi): CompatibilityLevel {
        var currentLevel = COMPATIBLE
        for (check in checks) {
            val compatibilityLevel = check(old, new) ?: continue
            if (compatibilityLevel == GLOBALLY_INCOMPATIBLE) {
                return GLOBALLY_INCOMPATIBLE
            }

            if (compatibilityLevel == INCOMPATIBLE) {
                currentLevel = INCOMPATIBLE
            }
        }

        for (check in checksWithAbiMap) {
            val compatibilityLevel = check(abiMap, old, new) ?: continue
            if (compatibilityLevel == GLOBALLY_INCOMPATIBLE) {
                return GLOBALLY_INCOMPATIBLE
            }

            if (compatibilityLevel == INCOMPATIBLE) {
                currentLevel = INCOMPATIBLE
            }
        }

        return currentLevel
    }
}

fun java8SpecIncompatibilityChecker(): CombinedAbiCompatibilityChecker {
    val checks = listOf<ClassAbiCompatibilityCheck>(
        ::abstractClasses,
        ::finalClasses,
        ::publicClasses,
        ::superclassesAndSuperinterfaces,
        ::constantsChange,
        { old, new ->
            val checks = listOf<FieldAbiCompatibilityCheck>(
                ::accessToFields,
                ::staticFieldChange,
                ::finalFieldChange
            )
            fieldsCompatibility(old, new, checks)
        },
        { old, new ->
            val checks = listOf<MethodAbiCompatibilityCheck>(
                ::accessToMethods,
                ::methodSignature,
                ::abstractMethodChange,
                ::finalMethodChange,
                ::staticMethodChange,
            )
            methodsCompatibility(old, new, checks)
        },
    )

    val checksWithAbiMap = listOf<ClassAbiCompatibilityCheckWithAbiMap>(
        ::newFieldThatOverrides,
        ::newMethodThatOverrides
    )

    return CombinedAbiCompatibilityChecker(
        checks = checks,
        checksWithAbiMap = checksWithAbiMap
    )
}