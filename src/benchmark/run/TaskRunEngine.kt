package benchmark.run

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.MessagePart
import benchmark.dataset.TaskPackageVerifier
import benchmark.domain.BenchmarkTask
import benchmark.domain.EvaluationResult
import benchmark.domain.TaskStatus
import benchmark.evaluation.TaskEvaluator
import benchmark.evaluation.TaskEvaluatorRegistry
import benchmark.protocol.PromptRenderer
import benchmark.protocol.TaskProtocol
import benchmark.protocol.TaskProtocolRegistry
import kotlin.coroutines.cancellation.CancellationException

/** Runs one independent task sample with bounded compiler-feedback repairs. */
class TaskRunEngine(
    private val protocols: TaskProtocolRegistry,
    private val evaluators: TaskEvaluatorRegistry,
    private val promptExecutor: PromptExecutor,
    val specification: BenchmarkRunSpecification,
    private val systemPromptRenderer: PromptRenderer,
    private val repairPromptRenderer: PromptRenderer,
    private val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private val packageVerifier: TaskPackageVerifier = TaskPackageVerifier(),
) {
    suspend fun run(task: BenchmarkTask, sampleIndex: Int): TaskRunRecord {
        require(sampleIndex > 0) { "sample index must be positive" }
        require(task.manifest.status == TaskStatus.FROZEN) {
            "task ${task.manifest.taskId} is not frozen"
        }

        // Preparation failures are run errors, not model samples.
        packageVerifier.verify(task)
        val protocol = protocols.findFor(task)
        val evaluator = evaluators.findFor(task)
        val taskPrompt = protocol.renderPrompt(task)
        val systemText = systemPromptRenderer.render(emptyMap())

        val turns = mutableListOf<TaskRunRecord.Turn>()

        val strategy = functionalStrategy<String, String> { initialPrompt ->
            var message = initialPrompt

            for (repairTurn in 0..specification.repairPolicy.maxRepairTurns) {
                var reply = requestLLM(message)
                var toolCalls =
                    reply.parts.filterIsInstance<MessagePart.Tool.Call>()
                var usedToolCalls = 0

                while (toolCalls.isNotEmpty()) {
                    if (toolCalls.size > specification.maxToolCallsPerAttempt - usedToolCalls) {
                        error("tool call limit exceeded")
                    }

                    usedToolCalls += toolCalls.size
                    val results = executeTools(toolCalls)
                    reply = sendToolResults(results)
                    toolCalls =
                        reply.parts.filterIsInstance<MessagePart.Tool.Call>()
                }

                val response = reply.parts
                    .filterIsInstance<MessagePart.Text>()
                    .joinToString("\n") { it.text }

                val (submission, result) =
                    assess(task, protocol, evaluator, response)

                turns += TaskRunRecord.Turn(
                    repairTurn = repairTurn,
                    userMessage = message,
                    response = response,
                    submission = submission,
                    result = result,
                )

                if (!specification.repairPolicy.shouldRepair(repairTurn, result)) break

                val rejection = result as EvaluationResult.Rejected
                message = repairPromptRenderer.render(
                    mapOf("DIAGNOSTIC" to rejection.reason),
                )
            }

            turns.last().response
        }

        val finalResult = try {
            val config = AIAgentConfig(
                prompt = prompt(
                    "benchmark-${task.manifest.taskId.value}-$sampleIndex",
                ) {
                    system(systemText)
                },
                model = specification.model,
                maxAgentIterations = specification.maxAgentIterations,
            )

            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = config,
                strategy = strategy,
                toolRegistry = toolRegistry,
            ).run(taskPrompt)

            turns.last().result
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            EvaluationResult.InfrastructureFailure(
                "agent execution failed: ${failure.javaClass.simpleName}",
            )
        }

        return TaskRunRecord(
            taskId = task.manifest.taskId,
            sampleIndex = sampleIndex,
            systemPrompt = systemText,
            taskPrompt = taskPrompt,
            turns = turns.toList(),
            finalResult = finalResult,
        )
    }

    private fun assess(
        task: BenchmarkTask,
        protocol: TaskProtocol,
        evaluator: TaskEvaluator,
        response: String,
    ): Pair<String?, EvaluationResult> {
        val submission = try {
            protocol.extractSubmission(response)
        } catch (failure: IllegalArgumentException) {
            return null to EvaluationResult.Rejected(
                "invalid response: ${failure.message}",
            )
        }

        return submission to evaluator.evaluate(task, submission)
    }
}