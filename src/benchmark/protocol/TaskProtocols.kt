package benchmark.protocol

import java.nio.file.Path

/** Registers the protocol implementations supported by the runner. */
object TaskProtocols {
    fun create(promptDirectory: Path): TaskProtocolRegistry {
        return TaskProtocolRegistry(
            listOf(
                LeanProofProtocol(PromptRenderer(promptDirectory.resolve("lean-proof.md"))),
                LeanDefinitionProtocol(PromptRenderer(promptDirectory.resolve("lean-definition.md"))),
                LeanDeclarationProtocol(PromptRenderer(promptDirectory.resolve("lean-declaration.md"))),
                LeanStatementProtocol(PromptRenderer(promptDirectory.resolve("lean-statement.md"))),
                InformalProofProtocol(PromptRenderer(promptDirectory.resolve("informal-proof.md"))),
            ),
        )
    }
}
