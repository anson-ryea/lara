package benchmark.run

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

/** Returns scripted replies without making provider calls. */
internal class ScriptedPromptExecutor(replies: List<String>) : PromptExecutor() {
    private val remainingReplies = ArrayDeque(replies)

    val prompts = mutableListOf<Prompt>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        prompts += prompt
        check(remainingReplies.isNotEmpty()) {
            "no scripted model reply remains"
        }

        return Message.Assistant(
            content = remainingReplies.removeFirst(),
            metaInfo = ResponseMetaInfo.Empty,
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        error("streaming is not expected in these tests")

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult =
        error("moderation is not expected in these tests")

    override fun close() = Unit
}
