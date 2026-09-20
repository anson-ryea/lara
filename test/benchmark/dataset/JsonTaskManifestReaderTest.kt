package benchmark.dataset

import benchmark.domain.ArtefactRole
import benchmark.domain.TaskManifest
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonTaskManifestReaderTest {
    private val reader = JsonTaskManifestReader()

    @Test
    fun readsCurrentV1Manifest() {
        val manifest = reader.read(
            Path(
                "dataset",
                "paper_1",
                "tasks",
                "task_1.1_theorem_11",
                "manifest.json",
            ),
        )

        assertEquals(TaskManifest.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals("task_1.1_theorem_11", manifest.taskId.value)
        assertEquals("paper_1", manifest.paperId.value)
        assertEquals("theorem_11", manifest.sourceResultId.value)
        assertEquals(2, manifest.taskType.value)
        assertEquals("lean_proof", manifest.protocol.value)
        assertEquals("paper_evidence", manifest.condition.value)
        assertEquals("theorem_11", manifest.contextId.value)
        assertEquals(
            setOf(
                ArtefactRole("lean_context"),
                ArtefactRole("lean_target"),
                ArtefactRole("paper_evidence"),
            ),
            manifest.artefacts.map { artefact -> artefact.role }.toSet(),
        )
        assertEquals("lean_proof_body", manifest.submission.kind.value)
        assertTrue(manifest.submission.allowLocalHelpers)
        assertEquals(
            setOf("propext", "Classical.choice", "Quot.sound"),
            manifest.evaluation.allowedAxioms,
        )
    }

    @Test
    fun readsTaskTypesWithoutAClosedLegacyMapping() {
        val manifest = reader.read(
            Path(
                "dataset",
                "paper_1",
                "tasks",
                "task_1.2_theorem_11",
                "manifest.json",
            ),
        )

        assertEquals(5, manifest.taskType.value)
        assertEquals("lean_proof", manifest.protocol.value)
        assertEquals(
            ArtefactRole("enriched_evidence"),
            manifest.artefacts.single { artefact ->
                artefact.path.toString() == "evidence.md"
            }.role,
        )
    }

    @Test
    fun rejectsTheRemovedManifestShape() {
        withTemporaryManifest(
            """
            {
              "schema_version": 1,
              "hashes": {}
            }
            """.trimIndent(),
        ) { manifestPath ->
            val failure = assertFailsWith<ManifestReadException> {
                reader.read(manifestPath)
            }

            assertEquals(
                "manifest contains unsupported fields: hashes",
                failure.reason,
            )
        }
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        withTemporaryManifest("""{"schema_version": 2}""") { manifestPath ->
            val failure = assertFailsWith<ManifestReadException> {
                reader.read(manifestPath)
            }

            assertEquals("unsupported schema version: 2", failure.reason)
        }
    }

    private fun withTemporaryManifest(
        contents: String,
        block: (java.nio.file.Path) -> Unit,
    ) {
        val manifestPath = Files.createTempFile(
            "task-manifest-",
            ".json",
        )
        try {
            manifestPath.writeText(contents)
            block(manifestPath)
        } finally {
            manifestPath.deleteIfExists()
        }
    }
}
