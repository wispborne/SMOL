package model

import java.io.File
import java.util.*

data class Mod(
    val id: String,
    val isEnabledInGame: Boolean,
    val modsFolderInfo: ModsFolderInfo?,
    val modVersions: Map<Int, ModVersion>,
) {

    fun isEnabled(modVersion: ModVersion) = isEnabledInGame && modVersion.isEnabledInSmol

    data class ModsFolderInfo(
        val folder: File
    )
}

/**
 * @param stagingInfo null if not installed, not null otherwise
 */
data class ModVersion(
    val modInfo: ModInfo,
    val isEnabledInSmol: Boolean,
    val stagingInfo: StagingInfo?,
    val archiveInfo: ArchiveInfo?,
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: Int = Objects.hash(modInfo.id, modInfo.version.toString())

    // incredibly inelegant way of doing a parent-child relationship
    @Transient lateinit var mod: Mod

    val exists = stagingInfo != null || archiveInfo != null

    data class ArchiveInfo(
        val folder: File
    )

    data class StagingInfo(
        val folder: File
    )
}