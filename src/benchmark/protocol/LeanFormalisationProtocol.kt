package benchmark.protocol

import benchmark.domain.BenchmarkTask
import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Shared prompt handling for Lean formalisation tasks that need source review. */
abstract class LeanFormalisationProtocol(
    final override val id: ProtocolId,
    private val submissionKind: SubmissionKind,
    private val marker: String,
    private val promptRenderer: PromptRenderer,
    private val submissionExtractor: SubmissionExtractor,
) : TaskProtocol {
    final override fun renderPrompt(task: BenchmarkTask): String {
        requireTaskShape(task, id, submissionKind)
        val reader = TaskMaterialReader(task)
        return promptRenderer.render(
            leanPromptValues(
                task = task,
                reader = reader,
                marker = marker,
                allowedEvidenceRoles = setOf(paperEvidenceRole),
                evidenceRequired = true,
            ),
        )
    }

    final override fun extractSubmission(response: String): String {
        return submissionExtractor.extractLeanCode(response)
    }
}
