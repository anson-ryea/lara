package benchmark.domain

data class TaskArtefact(
    val role: ArtefactRole,
    val path: ArtefactPath,
    val sha256Digest: Sha256Digest,
)
