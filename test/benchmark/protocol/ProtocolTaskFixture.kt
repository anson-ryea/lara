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
import java.util.Comparator

internal fun withProtocolTask(
    protocol: String,
    submissionKind: String,
    artefacts: Map<String, String>,
    block: (BenchmarkTask) -> Unit,
) {
    val root = Files.createTempDirectory("protocol-task-")
    try {
        val packageDirectory = root.resolve("task_1.1_theorem_11")
        Files.createDirectory(packageDirectory)

        val declaredArtefacts = artefacts.map { (role, contents) ->
            val relativePath = java.nio.file.Path.of("$role.txt")
            Files.writeString(packageDirectory.resolve(relativePath), contents)
            TaskArtefact(
                role = ArtefactRole(role),
                path = ArtefactPath(relativePath),
                sha256Digest = Sha256Digest("0".repeat(64)),
            )
        }
        val manifest = TaskManifest(
            schemaVersion = TaskManifest.SCHEMA_VERSION,
            taskId = TaskId("task_1.1_theorem_11"),
            paperId = PaperId("paper_1"),
            sourceResultId = SourceResultId("theorem_11"),
            taskType = TaskTypeId(1),
            protocol = ProtocolId(protocol),
            status = TaskStatus.CANDIDATE_PENDING_INDEPENDENT_REVIEW,
            condition = ConditionId("paper_evidence"),
            contextId = ContextId("theorem_11"),
            artefacts = declaredArtefacts,
            submission = SubmissionSpecification(
                kind = SubmissionKind(submissionKind),
                allowLocalHelpers = false,
                allowTopLevelDeclarations = false,
            ),
            evaluation = EvaluationSpecification(
                kind = EvaluationKind("lean_elaboration_and_review"),
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
