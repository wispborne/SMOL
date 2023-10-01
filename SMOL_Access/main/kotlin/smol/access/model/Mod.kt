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

package smol.access.model

import smol.access.Constants
import smol.access.business.ModsCache
import smol.utilities.isMissingAdmin
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.math.absoluteValue

data class Mod constructor(
    val id: String,
    val isEnabledInGame: Boolean,
    val variants: List<ModVariant>,
) {

    /**
     * A mod is enabled if:
     * 1. It's in enabled_mods.json.
     * 2. Its mod folder is in the /mods folder.
     */
    fun isEnabled(modVariant: ModVariant) =
        isEnabledInGame && modVariant.doesModInfoFileExist
    // Moved to `ModLoader.reload` and cached, as calling File.exists constantly is expensive.
    //&& modVariant.modsFolderInfo.folder.resolve(Constants.MOD_INFO_FILE).exists()

    /**
     * Path of a specific mod's folder.
     */
    data class ModsFolderInfo(
        val folder: Path
    )

    val enabledVariants: List<ModVariant>
        get() = variants.filter { isEnabled(it) }

    val findFirstEnabled: ModVariant?
        get() = variants.firstOrNull { isEnabled(it) }

    val findFirstDisabled: ModVariant?
        get() = variants.firstOrNull { !isEnabled(it) }

    val findHighestVersion: ModVariant?
        get() = variants.maxByOrNull { it.bestVersion }

    val findFirstEnabledOrHighestVersion: ModVariant?
        get() = findFirstEnabled ?: findHighestVersion

    val hasEnabledVariant: Boolean
        get() = findFirstEnabled != null

    val hasDisabledVariant: Boolean
        get() = findFirstDisabled != null

    companion object {
        val MOCK = Mod(
            id = "mock",
            isEnabledInGame = true,
            variants = listOf(
                ModVariant(
                    modInfo = ModInfo(
                        id = "mock",
                        name = "Mock Mod",
                        author = "Wisp",
                        isUtilityMod = true,
                        description = "An isolationist authoritarian Theocracy thrust into Persean politics by necessity, this high-tech faction utilises flexible pulse-based energy weapons and unique solar shielding.",
                        gameVersion = "0.95.1-RC15",
                        jars = listOf("jar.jar"),
                        modPlugin = "mod/plugin",
                        dependencies = listOf(Dependency(id = "lw_lazylib", name = null, version = null)),
                        version = Version(raw = "1.0.0"),
                        requiredMemoryMB = null,
                        isTotalConversion = false
                    ),
                    backupFile = null,
                    versionCheckerInfo = null,
                    modsFolderInfo = ModsFolderInfo(Path.of("")),
                    doesModInfoFileExist = true,
                )
            )
        )
    }
}

/**
 * @param modsFolderInfo The location of the mod variant; its folder.
 */
data class ModVariant(
    val modInfo: ModInfo,
    val versionCheckerInfo: VersionCheckerInfo?,
    val modsFolderInfo: Mod.ModsFolderInfo,
    val backupFile: Path?,
    val doesModInfoFileExist: Boolean,
) {
    companion object {
        private val smolIdAllowedChars = Regex("""[^0-9a-zA-Z\\.\-_]""")
        fun createSmolId(id: String, version: Version) =
            buildString {
                append(id.replace(smolIdAllowedChars, "").take(6))
                append("-")
                append(version.toString().replace(smolIdAllowedChars, "").take(9))
                append("-")
                append(
                    Objects.hash(
                        id,
                        version.toString()
                    )
                        .absoluteValue // Increases chance of a collision but ids look less confusing.
                )
            }

        private val systemFolderNameAllowedChars = Regex("""[^0-9a-zA-Z\\.\-_ ]""")
        fun createSmolId(modInfo: ModInfo) = createSmolId(modInfo.id, modInfo.version)
        fun generateVariantFolderName(modInfo: ModInfo) =
            "${modInfo.name?.replace(systemFolderNameAllowedChars, "")?.take(100)}-${modInfo.version}"

        fun generateBackupFileName(modInfo: ModInfo, extension: String = "7z") =
            "${generateVariantFolderName(modInfo)}.$extension"

        val MOCK: ModVariant
            get() = Mod.MOCK.variants.first()
    }

    /**
     * Composite key: mod id + mod version.
     */
    val smolId: SmolId
        get() = createSmolId(modInfo)

    internal fun mod(modsCache: ModsCache) = modsCache.mods.value?.mods!!.firstOrNull() { it.id == modInfo.id }
    fun mod(access: smol.access.Access) = access.modsFlow.value?.mods!!.firstOrNull() { it.id == modInfo.id }

    val isModInfoEnabled: Boolean
        get() = modsFolderInfo.folder.resolve(Constants.UNBRICKED_MOD_INFO_FILE).exists()

    fun generateVariantFolderName() = Companion.generateVariantFolderName(this.modInfo)
    fun generateBackupFileName(extension: String = "7z") = Companion.generateBackupFileName(this.modInfo, extension)

    /**
     * Use the version in VersionChecker if possible (authors sometimes will do 0.35 in ModInfo but 0.3.5 in Version Checker).
     */
    val bestVersion: Version =
        versionCheckerInfo?.modVersion?.toString()?.let { Version.parse(it) }
            ?: modInfo.version

    fun isMissingAdmin() = modsFolderInfo.folder.isMissingAdmin()
            || modsFolderInfo.folder.resolve(Constants.UNBRICKED_MOD_INFO_FILE).isMissingAdmin()
            || Constants.MOD_INFO_FILE_DISABLED_NAMES.any { modsFolderInfo.folder.resolve(it).isMissingAdmin() }
}

typealias SmolId = String
typealias ModId = String