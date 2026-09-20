package benchmark.domain

/** Constraints used when evaluating a task submission. */
data class EvaluationSpecification(
    val kind: EvaluationKind,
    val timeoutSeconds: Int,
    val allowedAxioms: Set<String>,
    val forbiddenMechanisms: Set<String>,
    val forbiddenIdentifiers: Set<String>,
) {
    init {
        require(timeoutSeconds > 0) {
            "evaluation timeout must be positive: $timeoutSeconds"
        }
        require(allowedAxioms.none(String::isBlank)) {
            "allowed axiom names must not be blank"
        }
        require(forbiddenMechanisms.all(mechanismPattern::matches)) {
            "forbidden mechanisms must use lower_snake_case"
        }
        require(forbiddenIdentifiers.none(String::isBlank)) {
            "forbidden identifiers must not be blank"
        }
    }

    companion object {
        private val mechanismPattern = Regex("[a-z][a-z0-9_]*")
    }
}
