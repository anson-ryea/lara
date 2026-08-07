import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import command.EntryCommand
import io.github.cdimascio.dotenv.dotenv

private val env = dotenv {
    directory = "."
}

suspend fun main(args: Array<String>) = EntryCommand()
    .subcommands(

    )
    .main(args)