package benchmark.domain

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskManifestTest {
    @Test
    fun acceptsManifestWithDistinctArtefactPaths() {
        val artefacts = listOf(
            artefact("lean_context", "Context.lean"),
            artefact("lean_context", "Supporting.lean"),
            artefact("lean_target", "Target.lean"),
        )

        val manifest = manifest(artefacts = artefacts)

        assertEquals(artefacts, manifest.artefacts)
    }

    @Test
    fun rejectsNonPositiveSchemaVersion() {
        for (version in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                manifest(schemaVersion = version)
            }
        }
    }

    @Test
    fun rejectsEmptyArtefactList() {
        assertFailsWith<IllegalArgumentException> {
            manifest(artefacts = emptyList())
        }
    }

    @Test
    fun rejectsDuplicateArtefactPaths() {
        val artefacts = listOf(
            artefact("lean_context", "Context.lean"),
            artefact("paper_evidence", "Context.lean"),
        )

        assertFailsWith<IllegalArgumentException> {
            manifest(artefacts = artefacts)
        }
    }

    private fun manifest(
        schemaVersion: Int = 2,
        artefacts: List<TaskArtefact> = listOf(
            artefact("lean_context", "Context.lean"),
        ),
    ): TaskManifest {
        return TaskManifest(
            schemaVersion = schemaVersion,
            taskId = TaskId("task_1.1_theorem_11"),
            taskType = TaskTypeId(2),
            protocol = ProtocolId("lean_proof"),
            status = TaskStatus.CANDIDATE_PENDING_INDEPENDENT_REVIEW,
            condition = ConditionId("paper_evidence"),
            artefacts = artefacts,
        )
    }

    private fun artefact(
        role: String,
        path: String,
    ): TaskArtefact {
        return TaskArtefact(
            role = ArtefactRole(role),
            path = ArtefactPath(Path(path)),
            sha256Digest = Sha256Digest("0".repeat(64)),
        )
    }
}