package benchmark.run

import benchmark.domain.EvaluationResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RepairPolicyTest {
    @Test
    fun repairsRejectionsOnlyWhileTurnsRemain() {
        val policy = RepairPolicy(maxRepairTurns = 2)
        val rejection = EvaluationResult.Rejected("Lean rejected the proof")

        assertTrue(policy.shouldRepair(0, rejection))
        assertTrue(policy.shouldRepair(1, rejection))
        assertFalse(policy.shouldRepair(2, rejection))
        assertFalse(policy.shouldRepair(0, EvaluationResult.Accepted))
        assertFalse(policy.shouldRepair(0, EvaluationResult.NeedsReview("review needed")))
        assertFalse(
            policy.shouldRepair(0, EvaluationResult.InfrastructureFailure("Lean unavailable")),
        )
    }

    @Test
    fun rejectsNegativeRepairLimit() {
        assertFailsWith<IllegalArgumentException> {
            RepairPolicy(maxRepairTurns = -1)
        }
    }
}
