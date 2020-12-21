package prj.incomp.java

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.MethodReferenceExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.streams.asSequence

interface JavaSourceParser {
    /**
     * Parses Java Source File and returns list of its dependencies within the same Source Set
     */
    fun parseDependencies(sourceFile: File): List<ClassQualifiedName>?
}

/**
 * Represents a Qualifiend Name of a Java Class
 * Ex: org.example.TestClass
 */
inline class ClassQualifiedName(val name: String) {
    fun path(fs: FileSystem): Path = name
        .split('.')
        .joinToString(fs.separator, postfix = ".java")
        .let { fs.getPath(it) }
}


class JavaSourceParserImpl(
    private val rootPath: Path
): JavaSourceParser {
    private val config = ParserConfiguration().apply {
        setSymbolResolver(JavaSymbolSolver(JavaParserTypeSolver(rootPath)))
        isAttributeComments = false
    }
    private val parser = JavaParser(config)

    override fun parseDependencies(sourceFile: File): List<ClassQualifiedName>? {
        val cu = parser.parse(sourceFile).result.orElse(null) ?: return null
        return cu.stream().asSequence()
            .mapNotNull { node ->
                try {
                    val type = when (node) {
                        is FieldAccessExpr -> node.resolve().type
                        is MethodCallExpr -> node.scope.map { it.calculateResolvedType() }.orElse(null)
                        is MethodReferenceExpr -> node.scope.calculateResolvedType()
                        is ClassOrInterfaceType -> node.resolve().toRawType()
                        else -> null
                    } ?: return@mapNotNull null

                    if (type.isReferenceType) {
                        type.asReferenceType().qualifiedName
                    } else {
                        null
                    }
                } catch (e: UnsolvedSymbolException) {
                    // TODO: log it
                    null
                }
            }
            .distinct()
            .map(::ClassQualifiedName)
            .toList()
    }
}