package benchmark.evaluation

/** Runs Lean on complete source text, independently of any task protocol. */
fun interface LeanProcess {
    fun run(source: String, timeoutSeconds: Int): LeanProcessResult
}