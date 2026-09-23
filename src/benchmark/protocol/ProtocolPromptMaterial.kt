package benchmark.protocol

import benchmark.domain.ArtefactRole
import benchmark.domain.BenchmarkTask

internal fun leanPromptValues(
    task: BenchmarkTask,
    reader: TaskMaterialReader,
    marker: String,
    allowedEvidenceRoles: Set<ArtefactRole>,
    evidenceRequired: Boolean,
): Map<String, String> {
    val context = reader.readRequired(leanContextRole)
    val target = reader.readRequired(leanTargetRole)
    require(target.split(marker).size == 2) {
        "task ${task.manifest.taskId} target must contain exactly one $marker marker"
    }

    return mapOf(
        "LEAN_CONTEXT" to context,
        "LEAN_TARGET" to target,
        "EVIDENCE_SECTION" to evidenceSection(
            task = task,
            reader = reader,
            allowedRoles = allowedEvidenceRoles,
            required = evidenceRequired,
        ),
    )
}

internal fun evidenceSection(
    task: BenchmarkTask,
    reader: TaskMaterialReader,
    allowedRoles: Set<ArtefactRole>,
    required: Boolean,
): String {
    val evidence = reader.readAtMostOneOf(allEvidenceRoles)
    require(evidence == null || evidence.first in allowedRoles) {
        "task ${task.manifest.taskId} declares unsupported evidence for ${task.manifest.protocol}"
    }
    require(!required || evidence != null) {
        "task ${task.manifest.taskId} is missing paper evidence"
    }

    return evidence?.let { (_, contents) ->
        "## Evidence\n\n$contents\n"
    }.orEmpty()
}

internal val paperEvidenceRole = ArtefactRole("paper_evidence")
internal val enrichedEvidenceRole = ArtefactRole("enriched_evidence")
private val leanContextRole = ArtefactRole("lean_context")
private val leanTargetRole = ArtefactRole("lean_target")
private val allEvidenceRoles = setOf(paperEvidenceRole, enrichedEvidenceRole)
