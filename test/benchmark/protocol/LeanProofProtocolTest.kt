package benchmark.protocol

import benchmark.domain.ArtefactPath
import benchmark.domain.ArtefactRole
import benchmark.domain.BenchmarkTask
import benchmark.domain.ConditionId
import benchmark.domain.ContextId
import benchmark.domain.EvaluationKind
import benchmark.domain.EvaluationSpecification
import benchmark.domain.PaperId
import benchmark.domain.ProtocolId
import benchmark.domain.Sha256Digest
import benchmark.domain.SourceResultId
import benchmark.domain.SubmissionKind
import benchmark.domain.SubmissionSpecification
import benchmark.domain.TaskArtefact
import benchmark.domain.TaskId
import benchmark.domain.TaskManifest
import benchmark.domain.TaskStatus
import benchmark.domain.TaskTypeId
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LeanProofProtocolTest {
    private val protocol = LeanProofProtocol(
        PromptRenderer(Path.of("prompts", "v1", "lean-proof.md")),
    )

    @Test
    fun rendersPaperEvidenceProofTask() {
        withTask("paper_evidence") { task ->
            val prompt = protocol.renderPrompt(task)

            assertContains(prompt, "context-marker")
            assertContains(prompt, "target-marker __BENCHMARK_PROOF__")
            assertContains(prompt, "evidence-marker")
            assertContains(prompt, "Return only one `by` proof body")
        }
    }

    @Test
    fun rendersProofTaskWithoutEvidence() {
        withTask(null) { task ->
            val prompt = protocol.renderPrompt(task)

            assertContains(prompt, "context-marker")
            assertContains(prompt, "target-marker __BENCHMARK_PROOF__")
            assertFalse(prompt.contains("## Evidence"))
        }
    }

    @Test
    fun rendersEnrichedEvidenceProofTask() {
        withTask("enriched_evidence") { task ->
            assertContains(protocol.renderPrompt(task), "evidence-marker")
        }
    }

    @Test
    fun rejectsTaskWithWrongSubmissionKind() {
        withTask(null) { task ->
            val wrongManifest = task.manifest.copy(
                submission = task.manifest.submission.copy(
                    kind = SubmissionKind("lean_term"),
                ),
            )

            val failure = assertFailsWith<IllegalArgumentException> {
                protocol.renderPrompt(task.copy(manifest = wrongManifest))
            }

            assertContains(failure.message.orEmpty(), "does not require lean_proof_body")
        }
    }

    @Test
    fun rejectsTargetWithRepeatedProofMarker() {
        withTask(null) { task ->
            Files.writeString(
                task.packageDirectory.resolve("Target.lean"),
                "__BENCHMARK_PROOF__\n__BENCHMARK_PROOF__",
            )

            val failure = assertFailsWith<IllegalArgumentException> {
                protocol.renderPrompt(task)
            }

            assertContains(failure.message.orEmpty(), "exactly one __BENCHMARK_PROOF__")
        }
    }

    private fun withTask(
        evidenceRole: String?,
        block: (BenchmarkTask) -> Unit,
    ) {
        val root = Files.createTempDirectory("lean-proof-protocol-")
        try {
            val packageDirectory = root.resolve("task_1.1_theorem_11")
            Files.createDirectory(packageDirectory)
            Files.writeString(packageDirectory.resolve("Context.lean"), "context-marker")
            Files.writeString(
                packageDirectory.resolve("Target.lean"),
                "target-marker __BENCHMARK_PROOF__",
            )

            val artefacts = mutableListOf(
                artefact("lean_context", "Context.lean"),
                artefact("lean_target", "Target.lean"),
            )
            if (evidenceRole != null) {
                Files.writeString(packageDirectory.resolve("evidence.md"), "evidence-marker")
                artefacts += artefact(evidenceRole, "evidence.md")
            }

            val manifest = TaskManifest(
                schemaVersion = TaskManifest.SCHEMA_VERSION,
                taskId = TaskId("task_1.1_theorem_11"),
                paperId = PaperId("paper_1"),
                sourceResultId = SourceResultId("theorem_11"),
                taskType = TaskTypeId(2),
                protocol = ProtocolId("lean_proof"),
                status = TaskStatus.CANDIDATE_PENDING_INDEPENDENT_REVIEW,
                condition = ConditionId("paper_evidence"),
                contextId = ContextId("theorem_11"),
                artefacts = artefacts,
                submission = SubmissionSpecification(
                    kind = SubmissionKind("lean_proof_body"),
                    allowLocalHelpers = true,
                    allowTopLevelDeclarations = false,
                ),
                evaluation = EvaluationSpecification(
                    kind = EvaluationKind("lean_kernel"),
                    timeoutSeconds = 180,
                    allowedAxioms = emptySet(),
                    forbiddenMechanisms = emptySet(),
                    forbiddenIdentifiers = emptySet(),
                ),
            )

            block(BenchmarkTask(manifest, packageDirectory))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun artefact(role: String, path: String): TaskArtefact {
        return TaskArtefact(
            role = ArtefactRole(role),
            path = ArtefactPath(Path.of(path)),
            sha256Digest = Sha256Digest("0".repeat(64)),
        )
    }
}
