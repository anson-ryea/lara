package benchmark.run

import benchmark.domain.EvaluationResult

/** Limits compiler-feedback repairs within one sample. */
data class RepairPolicy(val maxRepairTurns: Int) {
    init {
        require(maxRepairTurns >= 0) {
            "maximum repair turns must not be negative"
        }
    }

    fun shouldRepair(repairTurn: Int, result: EvaluationResult): Boolean =
        repairTurn < maxRepairTurns && result is EvaluationResult.Rejected
}
