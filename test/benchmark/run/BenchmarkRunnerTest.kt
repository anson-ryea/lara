package benchmark.run

import benchmark.dataset.TaskRepository
import benchmark.domain.BenchmarkTask
import benchmark.domain.EvaluationKind
import benchmark.domain.EvaluationResult
import benchmark.domain.TaskId
import benchmark.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BenchmarkRunnerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun savesEveryIndependentSampleBeforeCompleting() {
        runBlocking {
            val first = createRunTask(temporaryDirectory)
            val second = createRunTask(
                temporaryDirectory,
                id = "task_1.2_theorem_11",
            )
            val executor = ScriptedPromptExecutor(List(4) { "proof" })
            val store = RecordingRunStore()
            val runner = runner(
                tasks = listOf(second, first),
                executor = executor,
                store = store,
                specification = runTestSpecification(samplesPerTask = 2),
            )

            runner.run()

            assertEquals(
                listOf(
                    "begin",
                    "save:${first.manifest.taskId}:1",
                    "save:${first.manifest.taskId}:2",
                    "save:${second.manifest.taskId}:1",
                    "save:${second.manifest.taskId}:2",
                    "complete",
                ),
                store.events,
            )
            assertEquals(listOf(first.manifest.taskId, second.manifest.taskId), store.taskIds)
            assertEquals(4, executor.prompts.size)
            assertTrue(store.records.all { it.finalResult == EvaluationResult.Accepted })
        }
    }

    @Test
    fun verifiesAllPackagesBeforeStartingTheRun() {
        runBlocking {
            val valid = createRunTask(temporaryDirectory)
            val changed = createRunTask(
                temporaryDirectory,
                id = "task_1.2_theorem_11",
            )
            Files.writeString(
                changed.packageDirectory.resolve("Context.lean"),
                "changed after hashing",
            )
            val executor = ScriptedPromptExecutor(listOf("unused"))
            val store = RecordingRunStore()

            assertFailsWith<IllegalStateException> {
                runner(listOf(valid, changed), executor, store).run()
            }

            assertTrue(store.events.isEmpty())
            assertTrue(executor.prompts.isEmpty())
        }
    }

    @Test
    fun savesInfrastructureFailureBeforeStopping() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(emptyList())
            val store = RecordingRunStore()
            val runner = runner(
                tasks = listOf(task),
                executor = executor,
                store = store,
                specification = runTestSpecification(samplesPerTask = 2),
            )

            assertFailsWith<IllegalStateException> { runner.run() }

            assertEquals(listOf("begin", "save:${task.manifest.taskId}:1"), store.events)
            assertIs<EvaluationResult.InfrastructureFailure>(store.records.single().finalResult)
            assertEquals(1, executor.prompts.size)
        }
    }

    @Test
    fun savesNeedsReviewBeforeStopping() {
        runBlocking {
            val task = createRunTask(temporaryDirectory)
            val executor = ScriptedPromptExecutor(listOf("proposal"))
            val store = RecordingRunStore()
            val runner = runner(
                tasks = listOf(task),
                executor = executor,
                store = store,
                assessSubmission = { _, _ -> EvaluationResult.NeedsReview("human check") },
            )

            assertFailsWith<IllegalStateException> { runner.run() }

            assertEquals(listOf("begin", "save:${task.manifest.taskId}:1"), store.events)
            assertIs<EvaluationResult.NeedsReview>(store.records.single().finalResult)
        }
    }

    @Test
    fun rejectsSelectionWithoutFrozenTasks() {
        runBlocking {
            val candidate = createRunTask(
                temporaryDirectory,
                status = TaskStatus.CANDIDATE_PENDING_INDEPENDENT_REVIEW,
            )
            val executor = ScriptedPromptExecutor(listOf("unused"))
            val store = RecordingRunStore()

            assertFailsWith<IllegalStateException> {
                runner(listOf(candidate), executor, store).run()
            }

            assertTrue(store.events.isEmpty())
            assertTrue(executor.prompts.isEmpty())
        }
    }

    private fun runner(
        tasks: List<BenchmarkTask>,
        executor: ScriptedPromptExecutor,
        store: RunStore,
        specification: BenchmarkRunSpecification = runTestSpecification(),
        assessSubmission: (BenchmarkTask, String) -> EvaluationResult = { _, _ ->
            EvaluationResult.Accepted
        },
    ): BenchmarkRunner {
        val repository = object : TaskRepository {
            override fun findAll(): List<BenchmarkTask> = tasks

            override fun findById(taskId: TaskId): BenchmarkTask? =
                tasks.singleOrNull { it.manifest.taskId == taskId }
        }

        return BenchmarkRunner(
            repository = repository,
            selector = TaskSelector(setOf(EvaluationKind("test_evaluation"))),
            engine = createRunEngine(
                root = temporaryDirectory,
                executor = executor,
                specification = specification,
                assessSubmission = assessSubmission,
            ),
            store = store,
        )
    }

    private class RecordingRunStore : RunStore {
        val events = mutableListOf<String>()
        val records = mutableListOf<TaskRunRecord>()
        var taskIds: List<TaskId> = emptyList()
            private set

        override suspend fun begin(
            specification: BenchmarkRunSpecification,
            taskIds: List<TaskId>,
        ) {
            events += "begin"
            this.taskIds = taskIds
        }

        override suspend fun save(record: TaskRunRecord) {
            events += "save:${record.taskId}:${record.sampleIndex}"
            records += record
        }

        override suspend fun complete() {
            events += "complete"
        }
    }
}
