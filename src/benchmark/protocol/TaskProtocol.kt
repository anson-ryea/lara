package benchmark.protocol

import benchmark.domain.BenchmarkTask
import benchmark.domain.ProtocolId

/** Defines the model input and expected response for one task protocol. */
interface TaskProtocol {
    val id: ProtocolId

    /** Renders the public task material as a model prompt. */
    fun renderPrompt(task: BenchmarkTask): String

    /** Extracts the proposed submission from a model response. */
    fun extractSubmission(response: String): String
}
