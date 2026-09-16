package command

import com.github.ajalt.clikt.command.SuspendingCliktCommand

class EntryCommand : SuspendingCliktCommand() {
    override suspend fun run() = Unit
}
