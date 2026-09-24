package benchmark.run

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import benchmark.dataset.HashCalculator
import benchmark.domain.ArtefactPath
import benchmark.domain.ArtefactRole
import benchmark.domain.BenchmarkTask
import benchmark.domain.ConditionId
import benchmark.domain.ContextId
import benchmark.domain.EvaluationKind
import benchmark.domain.EvaluationResult
import benchmark.domain.EvaluationSpecification
import benchmark.domain.PaperId
import benchmark.domain.ProtocolId
import benchmark.domain.SourceResultId
import benchmark.domain.SubmissionKind
import benchmark.domain.SubmissionSpecification
import benchmark.domain.TaskArtefact
import benchmark.domain.TaskId
import benchmark.domain.TaskManifest
import benchmark.domain.TaskStatus
import benchmark.domain.TaskTypeId
import benchmark.evaluation.TaskEvaluator
import benchmark.evaluation.TaskEvaluatorRegistry
import benchmark.protocol.PromptRenderer
import benchmark.protocol.TaskProtocol
import benchmark.protocol.TaskProtocolRegistry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Creates a verified synthetic package for run tests. */
internal fun createRunTask(
    root: Path,
    id: String = "task_1.1_theorem_11",
    status: TaskStatus = TaskStatus.FROZEN,
): BenchmarkTask {
    val taskId = TaskId(id)
    val packageDirectory = root.resolve(id).toAbsolutePath().normalize()
    Files.createDirectories(packageDirectory)

    // The runner uses the manifest object; the verifier checks that the file exists.
    Files.writeString(packageDirectory.resolve("manifest.json"), "{}")
    val artefactPath = packageDirectory.resolve("Context.lean")
    Files.writeString(artefactPath, "-- synthetic context\n")

    val manifest = TaskManifest(
        schemaVersion = TaskManifest.SCHEMA_VERSION,
        taskId = taskId,
        paperId = PaperId("paper_1"),
        sourceResultId = SourceResultId("theorem_11"),
        taskType = TaskTypeId(2),
        protocol = ProtocolId("test_protocol"),
        status = status,
        condition = ConditionId("statement_only"),
        contextId = ContextId("theorem_11"),
        artefacts = listOf(
            TaskArtefact(
                role = ArtefactRole("lean_context"),
                path = ArtefactPath(Path.of("Context.lean")),
                sha256Digest = HashCalculator().sha256(artefactPath),
            ),
        ),
        submission = SubmissionSpecification(
            kind = SubmissionKind("lean_proof_body"),
            allowLocalHelpers = false,
            allowTopLevelDeclarations = false,
        ),
        evaluation = EvaluationSpecification(
            kind = EvaluationKind("test_evaluation"),
            timeout = 10.seconds,
            allowedAxioms = emptySet(),
            forbiddenMechanisms = emptySet(),
            forbiddenIdentifiers = emptySet(),
        ),
    )

    return BenchmarkTask(manifest, packageDirectory)
}

internal fun runTestSpecification(
    samplesPerTask: Int = 1,
    maxRepairTurns: Int = 1,
): BenchmarkRunSpecification = BenchmarkRunSpecification(
    model = LLModel(LLMProvider.OpenRouter, "test-model"),
    samplesPerTask = samplesPerTask,
    repairPolicy = RepairPolicy(maxRepairTurns),
    maxToolCallsPerAttempt = 0,
    maxAgentIterations = 20,
)

internal fun createRunEngine(
    root: Path,
    executor: PromptExecutor,
    specification: BenchmarkRunSpecification = runTestSpecification(),
    assessSubmission: (BenchmarkTask, String) -> EvaluationResult = { _, _ ->
        EvaluationResult.Accepted
    },
): TaskRunEngine {
    val promptDirectory = root.resolve("prompts")
    Files.createDirectories(promptDirectory)
    Files.writeString(promptDirectory.resolve("system.md"), "System instructions.")
    Files.writeString(promptDirectory.resolve("repair.md"), "Repair: {{DIAGNOSTIC}}")

    val protocol = object : TaskProtocol {
        override val id = ProtocolId("test_protocol")

        override fun renderPrompt(task: BenchmarkTask): String =
            "Task: ${task.manifest.taskId.value}"

        override fun extractSubmission(response: String): String {
            require(response.isNotBlank()) { "empty submission" }
            return response
        }
    }
    val evaluator = object : TaskEvaluator {
        override val kind = EvaluationKind("test_evaluation")

        override fun evaluate(
            task: BenchmarkTask,
            submission: String,
        ): EvaluationResult = assessSubmission(task, submission)
    }

    return TaskRunEngine(
        protocols = TaskProtocolRegistry(listOf(protocol)),
        evaluators = TaskEvaluatorRegistry(listOf(evaluator)),
        promptExecutor = executor,
        specification = specification,
        systemPromptRenderer = PromptRenderer(promptDirectory.resolve("system.md")),
        repairPromptRenderer = PromptRenderer(promptDirectory.resolve("repair.md")),
    )
}
