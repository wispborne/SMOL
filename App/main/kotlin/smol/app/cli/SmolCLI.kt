/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.app.cli

import smol.GraphicsLibConfig
import smol.VramChecker
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
import smol.access.business.UserManager
import smol.access.business.UserModProfileManager
import smol.access.business.VmParamsManager
import smol.access.config.GamePathManager

class SmolCLI(
    private val userManager: UserManager,
    private val userModProfileManager: UserModProfileManager,
    private val vmParamsManager: VmParamsManager,
    private val access: smol.access.Access,
    private val gamePathManager: GamePathManager,
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
            val profile = userManager.createModProfile(name = name, description = description ?: "", sortOrder = sortOrder, enabledModVariants = emptyList())
            echo("Created mod profile $profile (not yet active).")
        }
    }

    inner class ModProfileSet : CliktCommand(name = "modprofile-set", help = "Set active mod profile") {
        val profileId by option().required()

        override fun run() {
            GlobalScope.launch {
                userModProfileManager.switchModProfile(profileId)
                val profile = userManager.activeProfile.value.modProfiles.single { it.id == profileId }
                echo("Changed mod profile to $profile.")
            }
        }
    }

    inner class ModProfileRemove : CliktCommand(name = "modprofile-remove", help = "Remove a mod profile") {
        val profileId by option().required()

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
                    enabledModIds = access.reload()?.mods?.filter { it.hasEnabledVariant }?.map { it.id },
                    modIdsToCheck = null,
                    foldersToCheck = listOfNotNull(gamePathManager.getModsPath()),
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