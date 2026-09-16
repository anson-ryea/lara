package benchmark.domain

import java.nio.file.Path

data class BenchmarkTask(
    val manifest: TaskManifest,
    val packageDirectory: Path,
) {
    init {
        require(packageDirectory.isAbsolute) {
            "task package directory must be absolute: $packageDirectory"
        }
        require(packageDirectory == packageDirectory.normalize()) {
            "task package directory must be normalised: $packageDirectory"
        }
        require(
            packageDirectory.fileName?.toString() ==
                    manifest.taskId.value,
        ) {
            "task package directory name must match task identifier: " +
                    "${manifest.taskId}"
        }
    }

    fun pathFor(artefact: TaskArtefact): Path {
        require(artefact in manifest.artefacts) {
            "artefact is not declared by task ${manifest.taskId}"
        }

        return packageDirectory.resolve(artefact.path.value)
    }
}