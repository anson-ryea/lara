package benchmark.run

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BenchmarkRunSpecificationTest {
    private val model = LLModel(LLMProvider.OpenRouter, "test-model")

    @Test
    fun acceptsPositiveLimitsWithoutTools() {
        BenchmarkRunSpecification(
            model = model,
            samplesPerTask = 2,
            repairPolicy = RepairPolicy(1),
            maxToolCallsPerAttempt = 0,
            maxAgentIterations = 4,
        )
    }

    @Test
    fun rejectsInvalidLimits() {
        for (samples in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                specification(samplesPerTask = samples)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            specification(maxToolCallsPerAttempt = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            specification(maxAgentIterations = 0)
        }
    }

    @Test
    fun rejectsToolCallsForModelWithoutToolCapability() {
        assertFailsWith<IllegalArgumentException> {
            specification(maxToolCallsPerAttempt = 1)
        }
    }

    private fun specification(
        samplesPerTask: Int = 1,
        maxToolCallsPerAttempt: Int = 0,
        maxAgentIterations: Int = 1,
    ) = BenchmarkRunSpecification(
        model = model,
        samplesPerTask = samplesPerTask,
        repairPolicy = RepairPolicy(0),
        maxToolCallsPerAttempt = maxToolCallsPerAttempt,
        maxAgentIterations = maxAgentIterations,
    )
}
