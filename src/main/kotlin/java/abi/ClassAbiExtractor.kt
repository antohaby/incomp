package prj.incomp.java.abi

import org.objectweb.asm.*

// TODO: Test
class ClassAbiExtractor
private constructor() : ClassVisitor(Opcodes.ASM9) {
    var version: Int? = null
    var modifiers: Set<Modifier>? = null
    var name: ClassName? = null
    var superClass: ClassName? = null
    var interfaces: List<ClassName>? = null

    val fields: MutableList<ClassAbi.FieldAbi> = mutableListOf()
    val constants: MutableList<ClassAbi.ConstantAbi> = mutableListOf()
    val methods: MutableList<ClassAbi.MethodAbi> = mutableListOf()

    val exposed: MutableSet<ClassName> = mutableSetOf()
    val internal: MutableSet<ClassName> = mutableSetOf()

    /**
     * If className used under ACC_PRIVATE modifier add it as internal dependency
     * otherwise consider it as exposed dependency
     */
    private fun addDependencies(access: Int, types: List<TypeDescriptor>) {
        // Exclude JDK classes & primitive classes
        val nonPrimitive = types
            .filterNot { it.isPrimitive }
            .map { it.className }
            .filterNot { it.qualifiedName.startsWith("java.") }

        if ((access and Opcodes.ACC_PRIVATE) == 0) {
            exposed += nonPrimitive
        } else {
            internal += nonPrimitive
        }
    }

    private fun addDependencies(access: Int, type: TypeDescriptor) = addDependencies(access, listOf(type))

    // returns true If final static and non-private
    private fun Int.isExposedConstant() =
        this and (Opcodes.ACC_STATIC or Opcodes.ACC_FINAL) != 0 && (this and Opcodes.ACC_PRIVATE) == 0


    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        this.version = version
        this.modifiers = Modifier.parse(access)
        this.name = ClassName.fromInternalName(name)
        this.superClass = superName?.let(ClassName.Companion::fromInternalName)
        this.interfaces = interfaces?.map(ClassName.Companion::fromInternalName)

        superName?.let { addDependencies(access, TypeDescriptor(Type.getObjectType(it))) }
        interfaces?.forEach { addDependencies(access, TypeDescriptor(Type.getObjectType(it))) }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {

        // Register constant
        if (access.isExposedConstant() && value != null) {
            // For simplicity calc value hash of string value
            // technically can break if hash magically will match after change
            val valueHash = value.toString().hashCode()
            constants += ClassAbi.ConstantAbi(name, valueHash)
        }

        val type = TypeDescriptor(descriptor)
        fields += ClassAbi.FieldAbi(
            modifiers = Modifier.parse(access),
            name = name,
            type = type
        )

        addDependencies(access, type)

        return FieldExtractor(name)
    }

    private class FieldExtractor(
        private val name: String
    ) : FieldVisitor(Opcodes.ASM9) {
        override fun visitAttribute(attribute: Attribute) {

        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val type = Type.getMethodType(descriptor)

        val returnType = TypeDescriptor(type.returnType.descriptor)
        val argumentTypes = type.argumentTypes.map { TypeDescriptor(it.descriptor) }
        val exceptionTypes = exceptions?.map { ClassName.fromInternalName(it) }?.toSet() ?: emptySet()

        methods += ClassAbi.MethodAbi(
            modifiers = Modifier.parse(access),
            name = name,
            arguments = argumentTypes,
            returnType = returnType,
            exceptions = exceptionTypes
        )

        addDependencies(access, argumentTypes + returnType)

        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitAttribute(attribute: Attribute) {
                // TODO: Implement attributes support
            }
        }
    }

    companion object {
        fun extract(reader: ClassReader): ClassAbi {
            val visitor = ClassAbiExtractor()
            reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            val name = visitor.name ?: wasNotParsed("class name")

            // TODO: Read internal deps
            val typesFromConstantPool = reader
                .readClassesFromConstantPool()
                .notPrimitiveClassNames()

            // types from constant pool is considered internal unless they appeared in exposed set
            val internal = visitor.internal + typesFromConstantPool - name - visitor.exposed
            val exposed = visitor.exposed - name

            return ClassAbi(
                version = visitor.version ?: wasNotParsed("class version"),
                modifiers = visitor.modifiers ?: wasNotParsed("class modifiers"),
                name = name,
                superClass = visitor.superClass,
                interfaces = visitor.interfaces,
                fields = visitor.fields,
                constants = visitor.constants,
                methods = visitor.methods,
                exposed = exposed,
                internal = internal
            )
        }

        private fun List<TypeDescriptor>.notPrimitiveClassNames() = this
            .filterNot { it.isPrimitive }
            .map { it.className }
            .filterNot { it.qualifiedName.startsWith("java.") }

        private fun ClassReader.readClassesFromConstantPool(): List<TypeDescriptor> {
            val collector = mutableListOf<TypeDescriptor>()

            val buf = CharArray(maxStringLength)
            val total = itemCount
            repeat(total) {
                val itemOffset = getItem(it)
                // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.1
                if (itemOffset > 0 && readByte(itemOffset - 1) == 7) {
                    val descriptor = readUTF8(itemOffset, buf)
                    collector += TypeDescriptor(Type.getObjectType(descriptor))
                }
            }

            return collector
        }

        private fun wasNotParsed(classPartName: String): Nothing = error("$classPartName wasn't parsed")
    }
}