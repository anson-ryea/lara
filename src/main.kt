import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import command.EntryCommand
import command.Ge05rCheckCommand
import io.github.cdimascio.dotenv.dotenv

private val env = dotenv {
    directory = "."
}

suspend fun main(args: Array<String>) = EntryCommand()
    .subcommands(
        Ge05rCheckCommand(),
    )
    .main(args)