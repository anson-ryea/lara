package benchmark.run

import ai.koog.prompt.message.Message
import benchmark.domain.EvaluationResult
import benchmark.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaskRunEngineTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun acceptsTheFirstSubmission() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(listOf("proof"))
            val engine = createRunEngine(temporaryDirectory, executor)

            val record = engine.run(task, sampleIndex = 1)

            assertEquals(EvaluationResult.Accepted, record.finalResult)
            assertEquals(task.manifest.taskId, record.taskId)
            assertEquals("System instructions.", record.systemPrompt)
            assertEquals("Task: ${task.manifest.taskId.value}", record.taskPrompt)
            assertEquals(listOf(0), record.turns.map { it.repairTurn })
            assertEquals("proof", record.turns.single().submission)
            assertEquals(1, executor.prompts.size)
            assertEquals(
                record.taskPrompt,
                executor.prompts.single().messages
                    .filterIsInstance<Message.User>()
                    .last()
                    .textContent(),
            )
        }
    }

    @Test
    fun repairsARejectedSubmissionWithinTheSameSample() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(listOf("bad", "good"))
            val engine = createRunEngine(
                temporaryDirectory,
                executor,
                assessSubmission = { _, submission ->
                    if (submission == "bad") {
                        EvaluationResult.Rejected("Lean rejected the proof")
                    } else {
                        EvaluationResult.Accepted
                    }
                },
            )

            val record = engine.run(task, sampleIndex = 1)

            assertEquals(EvaluationResult.Accepted, record.finalResult)
            assertEquals(listOf(0, 1), record.turns.map { it.repairTurn })
            assertEquals(listOf("bad", "good"), record.turns.map { it.submission })
            assertEquals("Repair: Lean rejected the proof", record.turns[1].userMessage)
            assertEquals(2, executor.prompts.size)
        }
    }

    @Test
    fun stopsAfterTheConfiguredRepairLimit() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(listOf("bad-1", "bad-2"))
            val engine = createRunEngine(
                temporaryDirectory,
                executor,
                specification = runTestSpecification(maxRepairTurns = 1),
                assessSubmission = { _, _ -> EvaluationResult.Rejected("still invalid") },
            )

            val record = engine.run(task, sampleIndex = 1)

            assertIs<EvaluationResult.Rejected>(record.finalResult)
            assertEquals(listOf(0, 1), record.turns.map { it.repairTurn })
            assertEquals(2, executor.prompts.size)
        }
    }

    @Test
    fun doesNotRepairAResultThatNeedsReview() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(listOf("proposal"))
            val engine = createRunEngine(
                temporaryDirectory,
                executor,
                assessSubmission = { _, _ -> EvaluationResult.NeedsReview("human check") },
            )

            val record = engine.run(task, sampleIndex = 1)

            assertIs<EvaluationResult.NeedsReview>(record.finalResult)
            assertEquals(1, record.turns.size)
            assertEquals(1, executor.prompts.size)
        }
    }

    @Test
    fun recordsExecutorFailureAsInfrastructureFailure() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(emptyList())
            val engine = createRunEngine(temporaryDirectory, executor)

            val record = engine.run(task, sampleIndex = 1)

            val failure = assertIs<EvaluationResult.InfrastructureFailure>(record.finalResult)
            assertContains(failure.reason, "agent execution failed")
            assertTrue(record.turns.isEmpty())
            assertEquals(1, executor.prompts.size)
        }
    }

    @Test
    fun rejectsUnfrozenTaskBeforeCallingModel() {
        runBlocking {
            val task = createRunTask(
                temporaryDirectory,
                status = TaskStatus.CANDIDATE_PENDING_INDEPENDENT_REVIEW,
            )
            val executor = ScriptedPromptExecutor(listOf("unused"))
            val engine = createRunEngine(temporaryDirectory, executor)

            assertFailsWith<IllegalArgumentException> {
                engine.run(task, sampleIndex = 1)
            }
            assertTrue(executor.prompts.isEmpty())
        }
    }
}
