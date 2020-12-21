package prj.incomp.common

typealias SourceReader<Context, SourceUnit, Metadata> =
        Context.(Metadata) -> Sequence<SourceUnit>

typealias Compiler<Context, SourceUnit, Metadata> =
        Context.(Sequence<SourceUnit>, Metadata) -> Sequence<Pair<SourceUnit, CompilationResult>>

typealias UpdateMetadata<Context, SourceUnit, Metadata> =
        Context.(Metadata, SourceUnit, CompilationResult) -> Metadata

/**
 * Generic representation of Stateful Compilation pipeline through loading & storing [Metadata]
 *
 * @param metadataStore exposes functionality to store and load [Metadata] associated with some [Context]
 * @param sourceReader provides a sequence of [SourceUnit] depending on given [Context] and loaded [Metadata]
 * @param compile consumes the sequence of [SourceUnit]'s compiles them and provides result in
 * another sequence of [CompilationResult]'s of [SourceUnit]
 *
 * @param updateMetadata function-mutator that updates [Metadata] from compiled [SourceUnit]'s
 * @param updateMetadataOnStart function-mutator to update [Metadata] before compilation starts
 * @param updateMetadataOnFinish function-mutator to update [Metadata] after compilation
 */
class StatefulCompilationPipeline<Context, SourceUnit, Metadata>(
    private val metadataStore: CompilationMetadataStore<Context,Metadata>,
    private val sourceReader: SourceReader<Context, SourceUnit, Metadata>,
    private val compile: Compiler<Context, SourceUnit, Metadata>,
    private val updateMetadata: UpdateMetadata<Context, SourceUnit, Metadata>,
    private val updateMetadataOnStart: Context.(Metadata) -> Metadata = { it },
    private val updateMetadataOnFinish: Context.(Metadata) -> Metadata = { it },
) {

    /**
     * Execute compilation of [context]
     */
    fun compile(context: Context): Sequence<Pair<SourceUnit, CompilationResult>> {
        val metadata = metadataStore.loadOrDefault(context)

        val sources = context.sourceReader(metadata)
        val compilationResults = context.compile(sources, metadata)

        return sequence {
            var newMetadata = context.updateMetadataOnStart(metadata)
            for (result in compilationResults) {
                newMetadata = context.updateMetadata(newMetadata, result.first, result.second)
                yield(result)
            }
            newMetadata = context.updateMetadataOnFinish(newMetadata)
            metadataStore.store(context, newMetadata)
        }
    }
}

/**
 * Compilation Metadata store
 */
interface CompilationMetadataStore<SourceSet, Metadata> {
    /**
     * Should always return empty-metadata in case of data corruption or lacking
     */
    fun loadOrDefault(sourceSet: SourceSet): Metadata


    fun store(sourceSet: SourceSet, metadata: Metadata)
}

/**
 * Simple representaiton of compilation result of certain Compile Unit
 */
sealed class CompilationResult {
    object Success : CompilationResult()
    class Failed(val errors: List<String>) : CompilationResult()
}

/**
 * Simple way of incremental compilation is to compare Source and Target units modification time
 *
 * When:
 *  * SourceModificationTime <= TargetModificationTime -> can be skipped
 *  * SourceModificationTime > TargetModificationTime -> should be compiled
 *  * TargetModificationTime is unknown -> should be compiled
 *
 */
fun <SourceUnit> Sequence<SourceUnit>.filterLastModified(
    sourceLastModified: SourceUnit.() -> Long,
    targetLastModified: SourceUnit.() -> Long?,
): Sequence<SourceUnit> = filter { sourceUnit ->
    val targetLM = sourceUnit.targetLastModified()
    targetLM == null || targetLM < sourceUnit.sourceLastModified()
}

/**
 * SourceUnit A can be dependent on SourceUnit B
 * and in case if B changes it might be required to recompile A
 *
 * So result after calling B.[dependants] should include A
 */
fun <SourceUnit> Sequence<SourceUnit>.includeDependentSources(
    dependants: SourceUnit.() -> Sequence<SourceUnit>,
): Sequence<SourceUnit> = flatMap { unit ->
    sequenceOf(unit) + unit.dependants()
}

