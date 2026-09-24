package benchmark.model

import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

class OpenRouterConnection(apiKey: String): AutoCloseable {
    init {
        require(apiKey.isNotBlank()) { "OpenRouter API key must not be blank" }
    }

    private val client = OpenRouterLLMClient(apiKey)

    val promptExecutor: PromptExecutor = MultiLLMPromptExecutor(client)

    /** Checks the model ID without replacing its supplied capability metadata. */
    suspend fun requireAvailable(model: LLModel) {
        require(model.provider == LLMProvider.OpenRouter) {
            "model must use the OpenRouter provider"
        }

        require(model.id.isNotBlank()) { "model ID must not be blank" }

        check(client.models().any { it.id == model.id }) {
            "OpenRouter model is unavailable: ${model.id}"
        }
    }

    override fun close() {
        promptExecutor.close()
    }
}