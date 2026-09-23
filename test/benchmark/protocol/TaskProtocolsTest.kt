package benchmark.protocol

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TaskProtocolsTest {
    @Test
    fun registersLeanProofProtocol() {
        withProtocolTask(
            protocol = "lean_proof",
            submissionKind = "lean_proof_body",
            artefacts = mapOf(
                "lean_context" to "context-marker",
                "lean_target" to "target-marker __BENCHMARK_PROOF__",
            ),
        ) { task ->
            val protocol = TaskProtocols.create(Path.of("prompts", "v1"))
                .findFor(task)

            assertContains(protocol.renderPrompt(task), "target-marker __BENCHMARK_PROOF__")
            assertEquals("by\n  trivial", protocol.extractSubmission("by\n  trivial"))
        }
    }
}
