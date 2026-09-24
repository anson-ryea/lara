package benchmark.run

import benchmark.dataset.TaskPackageVerifier
import benchmark.dataset.TaskRepository
import benchmark.domain.EvaluationResult

/** Runs selected tasks sequentially and saves each sample before continuing. */
class BenchmarkRunner(
    private val repository: TaskRepository,
    private val selector: TaskSelector,
    private val engine: TaskRunEngine,
    private val store: RunStore,
    private val packageVerifier: TaskPackageVerifier = TaskPackageVerifier(),
) {
    suspend fun run() {
        val tasks = selector.select(repository.findAll())
        check(tasks.isNotEmpty()) {
            "no frozen tasks match the selected evaluation kinds"
        }

        // Fail before any model calls if a selected package is invalid.
        tasks.forEach(packageVerifier::verify)

        store.begin(
            specification = engine.specification,
            taskIds = tasks.map { it.manifest.taskId },
        )

        for (task in tasks) {
            for (sampleIndex in 1..engine.specification.samplesPerTask) {
                val record = engine.run(task, sampleIndex)
                store.save(record)

                when (record.finalResult) {
                    is EvaluationResult.InfrastructureFailure ->
                        error("run stopped after infrastructure failure in ${record.taskId}")

                    is EvaluationResult.NeedsReview ->
                        error("run stopped because evaluation requires review: ${record.taskId}")

                    else -> Unit
                }
            }
        }

        store.complete()
    }
}