package benchmark.protocol

import benchmark.domain.BenchmarkTask
import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Prepares public Lean proof tasks and extracts their proof bodies. */
class LeanProofProtocol(
    private val promptRenderer: PromptRenderer,
    private val submissionExtractor: SubmissionExtractor = SubmissionExtractor(),
) : TaskProtocol {
    override val id = ProtocolId("lean_proof")

    override fun renderPrompt(task: BenchmarkTask): String {
        requireTaskShape(task, id, SubmissionKind("lean_proof_body"))
        val reader = TaskMaterialReader(task)
        return promptRenderer.render(
            leanPromptValues(
                task = task,
                reader = reader,
                marker = MARKER,
                allowedEvidenceRoles = setOf(paperEvidenceRole, enrichedEvidenceRole),
                evidenceRequired = false,
            ),
        )
    }

    override fun extractSubmission(response: String): String {
        return submissionExtractor.extractLeanProof(response)
    }

    companion object {
        internal const val MARKER = "__BENCHMARK_PROOF__"
    }
}
