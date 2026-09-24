package benchmark.run

import benchmark.domain.EvaluationResult
import benchmark.domain.TaskId

/** One independent sample, including its initial attempt and any repairs. */
data class TaskRunRecord(
    val taskId: TaskId,
    val sampleIndex: Int,
    val systemPrompt: String,
    val taskPrompt: String,
    val turns: List<Turn>,
    val finalResult: EvaluationResult,
) {
    init {
        require(sampleIndex > 0) { "sample index must be positive" }
    }

    data class Turn(
        val repairTurn: Int,
        val userMessage: String,
        val response: String,
        val submission: String?,
        val result: EvaluationResult,
    )
}