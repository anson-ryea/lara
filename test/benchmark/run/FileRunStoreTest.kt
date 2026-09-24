package benchmark.run

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import benchmark.domain.EvaluationResult
import benchmark.domain.TaskId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileRunStoreTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val taskId = TaskId("task_1.1_theorem_11")

    @Test
    fun savesPromptsTurnsAndCompletedRun() = runBlocking {
        val runDirectory = temporaryDirectory.resolve("run-001")
        val store = FileRunStore(runDirectory)
        store.begin(specification(), listOf(taskId))
        store.save(record())
        store.complete()

        val run = readJson(runDirectory.resolve("run.json"))
        assertEquals("complete", run["status"]?.jsonPrimitive?.content)
        assertEquals(taskId.value, run["task_ids"]?.jsonArray?.single()?.jsonPrimitive?.content)

        val sample = readJson(
            runDirectory.resolve("tasks/${taskId.value}/samples/001/record.json"),
        )
        assertEquals("system instructions", sample["system_prompt"]?.jsonPrimitive?.content)
        assertEquals("task instructions", sample["task_prompt"]?.jsonPrimitive?.content)
        assertEquals(1, sample["turns"]?.jsonArray?.size)
        assertEquals(
            "accepted",
            sample["final_result"]?.jsonObject?.get("kind")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun doesNotCompleteWithMissingSamples() = runBlocking {
        val runDirectory = temporaryDirectory.resolve("run-002")
        val store = FileRunStore(runDirectory)
        store.begin(specification(samplesPerTask = 2), listOf(taskId))
        store.save(record())

        assertFailsWith<IllegalStateException> { store.complete() }
        assertEquals(
            "running",
            readJson(runDirectory.resolve("run.json"))["status"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun rejectsDuplicateSamples() {
        runBlocking {
            val store = FileRunStore(temporaryDirectory.resolve("run-003"))
            store.begin(specification(), listOf(taskId))
            store.save(record())

            assertFailsWith<IllegalStateException> { store.save(record()) }
        }
    }

    @Test
    fun rejectsToolEnabledRunsUntilToolExchangesCanBeStored() {
        runBlocking {
            val store = FileRunStore(temporaryDirectory.resolve("run-004"))
            val toolsModel = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "test-model",
                capabilities = listOf(ai.koog.prompt.llm.LLMCapability.Tools),
            )
            val specification = specification(
                model = toolsModel,
                maxToolCallsPerAttempt = 1,
            )

            assertFailsWith<IllegalArgumentException> {
                store.begin(specification, listOf(taskId))
            }
        }
    }

    private fun specification(
        model: LLModel = LLModel(LLMProvider.OpenRouter, "test-model"),
        samplesPerTask: Int = 1,
        maxToolCallsPerAttempt: Int = 0,
    ) = BenchmarkRunSpecification(
        model = model,
        samplesPerTask = samplesPerTask,
        repairPolicy = RepairPolicy(1),
        maxToolCallsPerAttempt = maxToolCallsPerAttempt,
        maxAgentIterations = 4,
    )

    private fun record() = TaskRunRecord(
        taskId = taskId,
        sampleIndex = 1,
        systemPrompt = "system instructions",
        taskPrompt = "task instructions",
        turns = listOf(
            TaskRunRecord.Turn(
                repairTurn = 0,
                userMessage = "task instructions",
                response = "by trivial",
                submission = "by trivial",
                result = EvaluationResult.Accepted,
            ),
        ),
        finalResult = EvaluationResult.Accepted,
    )

    private fun readJson(path: Path) =
        Json.parseToJsonElement(Files.readString(path)).jsonObject
}
