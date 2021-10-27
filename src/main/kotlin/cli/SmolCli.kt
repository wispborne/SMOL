package cli

import business.Staging
import business.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

class SmolCli(
    private val staging: Staging,
    private val userManager: UserManager
) {
    val chainOfCommand = Smol()
        .subcommands(
            Help(),
            ProfileList(),
            ProfileSet(),
            ProfileRemove()
        )

    fun parse(args: List<String>) {
        kotlin.runCatching {
            chainOfCommand
                .parse(args)
        }
            .onFailure {
                TermUi.echo(it.message)
                TermUi.echo(chainOfCommand.getFormattedHelp())
            }
    }

    inner class Smol : CliktCommand() {
        override fun run() = Unit
    }

    inner class Help : CliktCommand(help = "Show this message and exit") {
        override fun run() {
            TermUi.echo(chainOfCommand.getFormattedHelp())
        }
    }

    inner class ProfileList : CliktCommand(help = "List all profiles") {
        override fun run() {
            echo(userManager.getUserProfile().modProfiles.joinToString { it.toString() })
        }
    }

    inner class ProfileSet : CliktCommand(help = "Set active profile") {
        val name by option().required()

        override fun run() {

        }
    }

    inner class ProfileRemove : CliktCommand(help = "Remove a profile") {
        val name by option().required()

        override fun run() {

        }
    }
}