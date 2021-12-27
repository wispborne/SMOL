package smol_access.model

import smol_access.Access
import smol_access.business.ModLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.math.absoluteValue

data class Mod(
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
        isEnabledInGame && modVariant.modsFolderInfo != null

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
        get() = variants.maxByOrNull { it.modInfo.version }

    val findFirstEnabledOrHighestVersion: ModVariant?
        get() = findFirstEnabled ?: findHighestVersion

    val hasEnabledVariant: Boolean
        get() = findFirstEnabled != null

    companion object {
        val MOCK = Mod(
            id = "mock",
            isEnabledInGame = true,
            variants = listOf(
                ModVariant(
                    modInfo = ModInfo.v095(
                        _id = "mock",
                        _name = "Mock Mod",
                        _author = "Wisp",
                        _utilityString = "false",
                        _description = "An isolationist authoritarian Theocracy thrust into Persean politics by necessity, this high-tech faction utilises flexible pulse-based energy weapons and unique solar shielding.",
                        _gameVersion = "0.95.1-RC15",
                        _jars = listOf("jar.jar"),
                        _modPlugin = "mod/plugin",
                        _dependencies = listOf(Dependency(_id = "lw_lazylib", name = null, versionString = null)),
                        versionString = Version(raw = "1.0.0")
                    ),
                    versionCheckerInfo = null,
                    modsFolderInfo = null,
                    stagingInfo = null,
                    archiveInfo = null
                )
            )
        )
    }
}

/**
 * @param stagingInfo null if not installed, not null otherwise
 */
data class ModVariant(
    val modInfo: ModInfo,
    val versionCheckerInfo: VersionCheckerInfo?,
    val modsFolderInfo: Mod.ModsFolderInfo?,
    val stagingInfo: StagingInfo?,
    val archiveInfo: ArchiveInfo?,
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: SmolId
        get() = createSmolId(modInfo)

    companion object {
        private val filter = Regex("""[^0-9a-zA-Z\\.\-_]""")
        fun createSmolId(id: String, version: Version) =
            buildString {
                append(id.replace(filter, "").take(6))
                append("-")
                append(version.toString().replace(filter, "").take(9))
                append("-")
                append(
                    Objects.hash(
                        id,
                        version.toString()
                    )
                        .absoluteValue // Increases chance of a collision but ids look less confusing.
                )
            }

        fun createSmolId(modInfo: ModInfo) = createSmolId(modInfo.id, modInfo.version)

        val MOCK: ModVariant
            get() = Mod.MOCK.variants.first()
    }

    fun mod(modLoader: ModLoader) = modLoader.mods.value?.mods!!.first { it.id == modInfo.id }
    fun mod(access: Access) = access.mods.value?.mods!!.first { it.id == modInfo.id }

    val exists: Boolean
        get() = (stagingInfo != null && stagingInfo.folder.exists())
                || (archiveInfo != null && archiveInfo.folder.exists())
                || (modsFolderInfo != null && modsFolderInfo.folder.exists())

    data class ArchiveInfo(
        val folder: Path
    )

    data class StagingInfo(
        val folder: Path
    )

    fun generateVariantFolderName() = "${modInfo.name}_${smolId}"
}

typealias SmolId = String
typealias ModId = String