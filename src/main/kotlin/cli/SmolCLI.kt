package cli

import business.UserManager
import business.VmParamsManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SmolCLI(
    private val userManager: UserManager,
    private val vmParamsManager: VmParamsManager
) {
    val chainOfCommand = Smol()
        .subcommands(
            Help(),
            ModProfileList(),
            ModProfileCreate(),
            ModProfileSet(),
            ModProfileRemove(),
            SetRam(),
        )

    fun parse(command: String) {
        kotlin.runCatching {
            chainOfCommand
                .parse(
                    command
                        .removePrefix("smol")
                        .trim()
                        .split(" ")
                )
        }
            .onFailure {
                TermUi.echo(it)
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
            echo(userManager.getUserProfile().modProfiles.joinToString(separator = "\n") { it.toString() })
        }
    }

    inner class ModProfileCreate : CliktCommand(name = "modprofile-create", help = "Create a new mod profile") {
        val name by option().required()
        val description by option()
        val sortOrder by option().int()

        override fun run() {
            val profile = userManager.createModProfile(name = name, description = description, sortOrder = sortOrder)
            echo("Created mod profile $profile (not yet active).")
        }
    }

    inner class ModProfileSet : CliktCommand(name = "modprofile-set", help = "Set active mod profile") {
        val profileId by option().int().required()

        override fun run() {
            GlobalScope.launch {
                userManager.switchModProfile(profileId)
                val profile = userManager.getUserProfile().modProfiles.single { it.id == profileId }
                echo("Changed mod profile to $profile.")
            }
        }
    }

    inner class ModProfileRemove : CliktCommand(name = "modprofile-remove", help = "Remove a mod profile") {
        val profileId by option().int().required()

        override fun run() {
            userManager.removeModProfile(profileId)
            echo("Removed mod profile $profileId.")
        }
    }

    inner class SetRam : CliktCommand(name = "set-ram", help = "Set vmparams RAM (MB).") {
        val megabytes by argument().int()

        override fun run() {
            vmParamsManager.update { it?.withMb(megabytes) }
            echo("Set vmparams to use $megabytes MB.")
        }
    }
}