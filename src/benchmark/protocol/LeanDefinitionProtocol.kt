package benchmark.protocol

import benchmark.domain.ProtocolId
import benchmark.domain.SubmissionKind

/** Requests a Lean term for a paper definition. */
class LeanDefinitionProtocol(
    promptRenderer: PromptRenderer,
    submissionExtractor: SubmissionExtractor = SubmissionExtractor(),
) : LeanFormalisationProtocol(
    id = ProtocolId("lean_definition"),
    submissionKind = SubmissionKind("lean_term"),
    marker = "__BENCHMARK_TERM__",
    promptRenderer = promptRenderer,
    submissionExtractor = submissionExtractor,
)
