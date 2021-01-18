package prj.incomp.java.abi

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import prj.incomp.java.abi.Modifier.*

// TODO: Test
inline class TypeDescriptor(
    val type: Type
) {
    constructor(descriptorString: String): this(Type.getType(descriptorString))

    val isArray: Boolean get() = type.sort == Type.ARRAY

    val dimensions: Int get() = type.dimensions

    val className: ClassName get() = if (isArray) {
        ClassName(type.elementType.className)
    } else {
        ClassName(type.className)
    }

    val isPrimitive: Boolean get() {
        val elementType = if (isArray) {
            type.elementType
        } else {
            type
        }


        // Must be compatible with ASM [Type]
        return elementType.sort < Type.ARRAY
    }
}

/**
 * @param qualifiedName qualified name with package path separated by dots (eg: java.lang.String)
 */
inline class ClassName(
    val qualifiedName: String
) {
    companion object {
        // TODO: Test
        /**
         * @param name class name with slashes
         * @see "4.2.1. Binary Class and Interface Names" of Java Spec
         */
        fun fromInternalName(name: String) = ClassName(name.replace('/', '.'))
    }
}

enum class Modifier(val mask: Int) {
    PUBLIC(Opcodes.ACC_PUBLIC),
    PRIVATE(Opcodes.ACC_PRIVATE),
    PROTECTED(Opcodes.ACC_PROTECTED),
    STATIC(Opcodes.ACC_STATIC),
    FINAL(Opcodes.ACC_FINAL),
    SUPER(Opcodes.ACC_SUPER),
    SYNCHRONIZED(Opcodes.ACC_SYNCHRONIZED),
    OPEN(Opcodes.ACC_OPEN),
    TRANSITIVE(Opcodes.ACC_TRANSITIVE),
    VOLATILE(Opcodes.ACC_VOLATILE),
    BRIDGE(Opcodes.ACC_BRIDGE),
    STATIC_PHASE(Opcodes.ACC_STATIC_PHASE),
    VARARGS(Opcodes.ACC_VARARGS),
    TRANSIENT(Opcodes.ACC_TRANSIENT),
    NATIVE(Opcodes.ACC_NATIVE),
    INTERFACE(Opcodes.ACC_INTERFACE),
    ABSTRACT(Opcodes.ACC_ABSTRACT),
    STRICT(Opcodes.ACC_STRICT),
    SYNTHETIC(Opcodes.ACC_SYNTHETIC),
    ANNOTATION(Opcodes.ACC_ANNOTATION),
    ENUM(Opcodes.ACC_ENUM),
    MANDATED(Opcodes.ACC_MANDATED),
    MODULE(Opcodes.ACC_MODULE);

    // TODO: Test
    companion object {
        fun parse(flags: Int): Set<Modifier> = values()
            .filter { (flags and it.mask) != 0 }
            .toSet()
    }
}

enum class AccessModifier {
    PRIVATE,
    PACKAGE,
    PROTECTED,
    PUBLIC;
}

data class ClassAbi(
    val version: Int,
    val modifiers: Set<Modifier>,
    val name: ClassName,
    val superClass: ClassName?,
    val interfaces: List<ClassName>?,
    val fields: List<FieldAbi>,
    val constants: List<ConstantAbi>,
    val methods: List<MethodAbi>,

    val exposed: Set<ClassName>, // Exposed classes
    val internal: Set<ClassName> // Internal dependencies eg. in method bodies or private fields
) {
    val allDependencies = exposed + internal

    val accessModifier by lazy { modifiers.asAccessModifier() }

    val fieldsMap by lazy { fields.associateBy { it.name } }

    val methodsMap by lazy {
        methods.associateBy { it.signature }
    }

    val constantsMap by lazy { constants.associateBy { it.name } }

    data class FieldAbi(
        val modifiers: Set<Modifier>,
        val name: String,
        val type: TypeDescriptor
    ) {
        val accessModifier by lazy { modifiers.asAccessModifier() }
    }

    data class MethodAbi(
        val modifiers: Set<Modifier>,
        val name: String,
        val arguments: List<TypeDescriptor>,
        val returnType: TypeDescriptor,
        val exceptions: Set<ClassName>
    ) {
        val accessModifier by lazy { modifiers.asAccessModifier() }

        val signature = MethodSignature(name, arguments, returnType)
    }

    data class MethodSignature(
        val name: String,
        val arguments: List<TypeDescriptor>,
        val returnType: TypeDescriptor
    )

    data class ConstantAbi(
        val name: String,
        val valueHash: Int
    )
}

private fun Set<Modifier>.asAccessModifier() = when {
    PRIVATE in this -> AccessModifier.PRIVATE
    PROTECTED in this -> AccessModifier.PROTECTED
    PUBLIC in this -> AccessModifier.PUBLIC
    else -> AccessModifier.PACKAGE
}