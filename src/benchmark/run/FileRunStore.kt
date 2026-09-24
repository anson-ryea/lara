package benchmark.run

import benchmark.domain.EvaluationResult
import benchmark.domain.TaskId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** Saves one run and its samples in a new directory. */
class FileRunStore(runDirectory: Path) : RunStore {
    private val runDirectory = runDirectory.toAbsolutePath().normalize()
    private val json = Json { prettyPrint = true }

    private var activeSpecification: BenchmarkRunSpecification? = null
    private var selectedTaskIds: List<TaskId> = emptyList()
    private val savedSamples = mutableSetOf<Pair<TaskId, Int>>()
    private var completed = false

    override suspend fun begin(
        specification: BenchmarkRunSpecification,
        taskIds: List<TaskId>,
    ) {
        check(activeSpecification == null) { "run has already begun" }
        require(taskIds.isNotEmpty()) { "run must select at least one task" }
        require(taskIds.distinct().size == taskIds.size) {
            "selected task IDs must be unique"
        }
        require(specification.maxToolCallsPerAttempt == 0) {
            "tool-enabled runs need tool exchanges in the stored record"
        }

        val parent = requireNotNull(runDirectory.parent) {
            "run directory must have a parent"
        }
        check(Files.isDirectory(parent)) {
            "run directory parent does not exist: $parent"
        }

        // createDirectory fails if the run ID has already been used.
        withContext(Dispatchers.IO) {
            Files.createDirectory(runDirectory)
        }
        writeAtomically(
            target = runDirectory.resolve("run.json"),
            value = runJson(specification, taskIds, "running"),
            replace = false,
        )

        activeSpecification = specification
        selectedTaskIds = taskIds.toList()
    }

    override suspend fun save(record: TaskRunRecord) {
        val specification = checkNotNull(activeSpecification) {
            "run has not begun"
        }
        check(!completed) { "run is already complete" }
        check(record.taskId in selectedTaskIds) {
            "task was not selected: ${record.taskId}"
        }
        check(record.sampleIndex in 1..specification.samplesPerTask) {
            "sample index is outside the configured range"
        }

        val key = record.taskId to record.sampleIndex
        check(key !in savedSamples) {
            "sample has already been saved: $key"
        }

        val sampleDirectory = runDirectory
            .resolve("tasks")
            .resolve(record.taskId.value)
            .resolve("samples")
            .resolve(record.sampleIndex.toString().padStart(3, '0'))

        withContext(Dispatchers.IO) {
            Files.createDirectories(sampleDirectory)
        }
        writeAtomically(
            target = sampleDirectory.resolve("record.json"),
            value = sampleJson(record),
            replace = false,
        )
        savedSamples += key
    }

    override suspend fun complete() {
        val specification = checkNotNull(activeSpecification) {
            "run has not begun"
        }
        check(!completed) { "run is already complete" }

        val expected =
            selectedTaskIds.size.toLong() * specification.samplesPerTask
        check(savedSamples.size.toLong() == expected) {
            "run has ${savedSamples.size} of $expected samples"
        }

        writeAtomically(
            target = runDirectory.resolve("run.json"),
            value = runJson(specification, selectedTaskIds, "complete"),
            replace = true,
        )
        completed = true
    }

    private fun runJson(
        specification: BenchmarkRunSpecification,
        taskIds: List<TaskId>,
        status: String,
    ): JsonObject = buildJsonObject {
        put("schema_version", JsonPrimitive(1))
        put("status", JsonPrimitive(status))
        put("model", json.encodeToJsonElement(specification.model))
        put("samples_per_task", JsonPrimitive(specification.samplesPerTask))
        put(
            "max_repair_turns",
            JsonPrimitive(specification.repairPolicy.maxRepairTurns),
        )
        put(
            "max_tool_calls_per_attempt",
            JsonPrimitive(specification.maxToolCallsPerAttempt),
        )
        put(
            "max_agent_iterations",
            JsonPrimitive(specification.maxAgentIterations),
        )
        put(
            "task_ids",
            JsonArray(taskIds.map { JsonPrimitive(it.value) }),
        )
    }

    private fun sampleJson(record: TaskRunRecord): JsonObject =
        buildJsonObject {
            put("schema_version", JsonPrimitive(1))
            put("task_id", JsonPrimitive(record.taskId.value))
            put("sample_index", JsonPrimitive(record.sampleIndex))
            put("system_prompt", JsonPrimitive(record.systemPrompt))
            put("task_prompt", JsonPrimitive(record.taskPrompt))
            put("turns", JsonArray(record.turns.map(::turnJson)))
            put("final_result", resultJson(record.finalResult))
        }

    private fun turnJson(turn: TaskRunRecord.Turn): JsonObject =
        buildJsonObject {
            put("repair_turn", JsonPrimitive(turn.repairTurn))
            put("user_message", JsonPrimitive(turn.userMessage))
            put("response", JsonPrimitive(turn.response))
            put(
                "submission",
                turn.submission?.let { JsonPrimitive(it) } ?: JsonNull,
            )
            put("result", resultJson(turn.result))
        }

    private fun resultJson(result: EvaluationResult): JsonObject =
        buildJsonObject {
            when (result) {
                EvaluationResult.Accepted ->
                    put("kind", JsonPrimitive("accepted"))

                is EvaluationResult.Rejected -> {
                    put("kind", JsonPrimitive("rejected"))
                    put("reason", JsonPrimitive(result.reason))
                }

                is EvaluationResult.NeedsReview -> {
                    put("kind", JsonPrimitive("needs_review"))
                    put("reason", JsonPrimitive(result.reason))
                }

                is EvaluationResult.InfrastructureFailure -> {
                    put("kind", JsonPrimitive("infrastructure_failure"))
                    put("reason", JsonPrimitive(result.reason))
                }
            }
        }

    private fun writeAtomically(
        target: Path,
        value: JsonElement,
        replace: Boolean,
    ) {
        if (!replace) {
            check(Files.notExists(target)) {
                "run file already exists: $target"
            }
        }

        val temporary = Files.createTempFile(
            target.parent,
            ".${target.fileName}.",
            ".tmp",
        )

        try {
            val contents =
                json.encodeToString(JsonElement.serializer(), value) + "\n"
            Files.writeString(temporary, contents, StandardCharsets.UTF_8)

            if (replace) {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } else {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}