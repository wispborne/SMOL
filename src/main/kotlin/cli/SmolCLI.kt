package cli

import business.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

class SmolCLI(
    private val userManager: UserManager
) {
    val chainOfCommand = Smol()
        .subcommands(
            Help(),
            ModProfileList(),
            ModProfileSet(),
            ModProfileRemove()
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

    inner class ModProfileList : CliktCommand(name = "modprofile-list", help = "List all mod profiles") {
        override fun run() {
            echo(userManager.getUserProfile().modProfiles.joinToString { it.toString() })
        }
    }

    inner class ModProfileSet : CliktCommand(name = "modprofile-set", help = "Set active mod profile") {
        val profileId by option().int().required()

        override fun run() {
            val profile = userManager.getUserProfile().modProfiles.single { it.id == profileId }
            runBlocking { userManager.changeModProfile(profile) }
            echo("Changed mod profile to $profile")
        }
    }

    inner class ModProfileRemove : CliktCommand(name = "modprofile-remove", help = "Remove a mod profile") {
        val profileId by option().int().required()

        override fun run() {
            runBlocking { userManager.removeModProfile(profileId) }
            echo("Removed mod profile $profileId")
        }
    }
}