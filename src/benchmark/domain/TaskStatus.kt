package benchmark.domain

enum class TaskStatus(
    val manifestValue: String,
) {
    CANDIDATE_PENDING_INDEPENDENT_REVIEW(
        "candidate_pending_independent_review",
    ),
    FROZEN("frozen");

    override fun toString(): String = manifestValue

    companion object {
        fun fromManifestValue(value: String): TaskStatus {
            return entries.singleOrNull { status ->
                status.manifestValue == value
            } ?: throw IllegalArgumentException(
                "unknown task status: $value",
            )
        }
    }
}