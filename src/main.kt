import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking

private val env = dotenv {
    directory = "."
}

fun main() = runBlocking {
    // Get the OpenRouter API key from the OPENROUTER_API_KEY environment variable
    val apiKey = env["OPENROUTER_API_KEY"]
        ?: error("The API key is not set.")

    val testModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "qwen/qwen3.6-27b",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.Completion,
            LLMCapability.ToolChoice,
            LLMCapability.Thinking,
        ),
        contextLength = 262_000
    )

    // Create an agent
    val agent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OpenRouterLLMClient(apiKey)),
        llmModel = testModel
    )

    // Run the agent
    val result = agent.run("Hello! who are you?")
    println(result)
}