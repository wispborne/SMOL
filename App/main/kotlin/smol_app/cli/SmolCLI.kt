package smol_app.cli

import GraphicsLibConfig
import VramChecker
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.Constants
import smol_access.business.UserManager
import smol_access.business.UserModProfileManager
import smol_access.business.VmParamsManager
import smol_access.config.GamePath

class SmolCLI(
    private val userManager: UserManager,
    private val userModProfileManager: UserModProfileManager,
    private val vmParamsManager: VmParamsManager,
    private val access: Access,
    private val gamePath: GamePath,
) {
    val chainOfCommand = Smol()
        .subcommands(
            Help(),
            ModProfileList(),
            ModProfileCreate(),
            ModProfileSet(),
            ModProfileRemove(),
            SetRam(),
            CheckVram(),
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
            echo(userManager.activeProfile.value.modProfiles.joinToString(separator = "\n") { it.toString() })
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
                userModProfileManager.switchModProfile(profileId)
                val profile = userManager.activeProfile.value.modProfiles.single { it.id == profileId }
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

    inner class CheckVram : CliktCommand(name = "check-vram", help = "Run Version Checker") {
        val areGfxLibNormalMapsEnabled by argument().int()
            .help("areGfxLibNormalMapsEnabled: 1 for true, false otherwise.").default(1)
        val areGfxLibMaterialMapsEnabled by argument().int()
            .help("areGfxLibMaterialMapsEnabled: 1 for true, false otherwise.").default(1)
        val areGfxLibSurfaceMapsEnabled by argument().int()
            .help("areGfxLibSurfaceMapsEnabled: 1 for true, false otherwise.").default(1)

        override fun run() {
            GlobalScope.launch {
                VramChecker(
                    enabledModIds = access.reload()?.filter { it.hasEnabledVariant }?.map { it.id },
                    modIdsToCheck = null,
                    foldersToCheck = listOf(gamePath.getModsPath(), Constants.STAGING_FOLDER_DEFAULT),
                    showGfxLibDebugOutput = false,
                    showPerformance = false,
                    showSkippedFiles = false,
                    showCountedFiles = true,
                    graphicsLibConfig = GraphicsLibConfig(
                        areGfxLibNormalMapsEnabled = areGfxLibNormalMapsEnabled == 1,
                        areGfxLibMaterialMapsEnabled = areGfxLibMaterialMapsEnabled == 1,
                        areGfxLibSurfaceMapsEnabled = areGfxLibSurfaceMapsEnabled == 1
                    ),
                    traceOut = { echo(it) },
                    debugOut = { echo(it) }
                )
                    .check()
            }
        }
    }
}