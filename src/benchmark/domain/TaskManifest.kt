package benchmark.domain

data class TaskManifest(
    val schemaVersion: Int,
    val taskId: TaskId,
    val taskType: TaskTypeId,
    val protocol: ProtocolId,
    val status: TaskStatus,
    val condition: ConditionId,
    val artefacts: List<TaskArtefact>,
) {
    init {
        require(schemaVersion > 0) {
            "schema version must be positive: $schemaVersion"
        }
        require(artefacts.isNotEmpty()) {
            "task manifest must contain at least one artefact"
        }

        val duplicatePaths = artefacts
            .groupingBy(TaskArtefact::path)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        require(duplicatePaths.isEmpty()) {
            "task artefact paths must be unique: " +
                    duplicatePaths
                        .sortedBy(ArtefactPath::toString)
                        .joinToString()
        }
    }
}
