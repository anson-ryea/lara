package benchmark.protocol

import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Requests Lean declarations for a paper grammar or judgement. */
class LeanDeclarationProtocol(
    promptRenderer: PromptRenderer,
    submissionExtractor: SubmissionExtractor = SubmissionExtractor(),
) : LeanFormalisationProtocol(
    id = ProtocolId("lean_declaration"),
    submissionKind = SubmissionKind("lean_declaration_block"),
    marker = "__BENCHMARK_DECLARATION__",
    promptRenderer = promptRenderer,
    submissionExtractor = submissionExtractor,
)
