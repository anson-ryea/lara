package benchmark.run

import benchmark.domain.TaskId

/** Persists run metadata and each completed sample. */
interface RunStore {
    suspend fun begin(
        specification: BenchmarkRunSpecification,
        taskIds: List<TaskId>
    )

    suspend fun save(record: TaskRunRecord)

    suspend fun complete()
}