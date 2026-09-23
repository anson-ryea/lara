package benchmark.protocol

import benchmark.domain.ArtefactRole
import benchmark.domain.BenchmarkTask
import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Requests a prose proof from a paper statement and optional paper evidence. */
class InformalProofProtocol(
    private val promptRenderer: PromptRenderer,
    private val submissionExtractor: SubmissionExtractor = SubmissionExtractor(),
) : TaskProtocol {
    override val id = ProtocolId("informal_proof")

    override fun renderPrompt(task: BenchmarkTask): String {
        requireTaskShape(task, id, SubmissionKind("informal_proof"))
        val reader = TaskMaterialReader(task)
        return promptRenderer.render(
            mapOf(
                "PAPER_STATEMENT" to reader.readRequired(
                    ArtefactRole("paper_statement"),
                ),
                "EVIDENCE_SECTION" to evidenceSection(
                    task = task,
                    reader = reader,
                    allowedRoles = setOf(paperEvidenceRole),
                    required = false,
                ),
            ),
        )
    }

    override fun extractSubmission(response: String): String {
        return submissionExtractor.extractInformalProof(response)
    }
}
