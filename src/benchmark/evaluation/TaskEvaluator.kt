package benchmark.evaluation

import benchmark.domain.BenchmarkTask
import benchmark.domain.EvaluationKind
import benchmark.domain.EvaluationResult

/** Evaluates submissions for one kind of task evaluation. */
interface TaskEvaluator {
    val kind: EvaluationKind

    /** Returns a verdict for an extracted submission. */
    fun evaluate(task: BenchmarkTask, submission: String): EvaluationResult
}