package model

import java.io.File
import java.util.*

/**
 * @param stagingInfo null if not installed, not null otherwise
 */
data class Mod(
    val modInfo: ModInfo,
    val isEnabledInSmol: Boolean,
    val isEnabledInGame: Boolean,
    val modsFolderInfo: ModsFolderInfo?,
    val stagingInfo: StagingInfo?,
    val archiveInfo: ArchiveInfo?
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: Int = Objects.hash(modInfo.id, modInfo.version.toString())

    val isEnabled = isEnabledInGame && isEnabledInSmol

    data class ModsFolderInfo(
        val folder: File
    )

    data class ArchiveInfo(
        val folder: File
    )

    data class StagingInfo(
        val folder: File
    )

    val exists = stagingInfo != null || archiveInfo != null
}