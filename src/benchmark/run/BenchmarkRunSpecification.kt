package benchmark.run

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/** Model and limits shared by every sample in one benchmark run. */
data class BenchmarkRunSpecification(
    val model: LLModel,
    val samplesPerTask: Int,
    val repairPolicy: RepairPolicy,
    val maxToolCallsPerAttempt: Int,
    val maxAgentIterations: Int,
) {
    init {
        require(model.id.isNotBlank()) { "model ID must not be blank" }
        require(samplesPerTask > 0) { "samples per task must be positive" }
        require(maxToolCallsPerAttempt >= 0) {
            "maximum tool calls per attempt must not be negative"
        }
        require(maxAgentIterations > 0) {
            "maximum agent iterations must be positive"
        }
        require(
            maxToolCallsPerAttempt == 0 ||
                    model.supports(LLMCapability.Tools)
        ) {
            "tool calls require a model configured with tool support"
        }
    }
}