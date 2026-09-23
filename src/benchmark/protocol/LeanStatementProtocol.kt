package benchmark.protocol

import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Requests a Lean statement whose source meaning still needs review. */
class LeanStatementProtocol(
    promptRenderer: PromptRenderer,
    submissionExtractor: SubmissionExtractor = SubmissionExtractor(),
) : LeanFormalisationProtocol(
    id = ProtocolId("lean_statement"),
    submissionKind = SubmissionKind("lean_statement"),
    marker = "__BENCHMARK_STATEMENT__",
    promptRenderer = promptRenderer,
    submissionExtractor = submissionExtractor,
)
