package model

import java.io.File
import java.util.*
import kotlin.random.Random

/**
 * @param staged null if not installed, not null otherwise
 */
data class Mod(
    val modInfo: ModInfo,
    val isEnabledInSmol: Boolean,
    val isEnabledInGame: Boolean,
    val staged: Staged?,
    val archived: Archived?
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: Int = Objects.hash(modInfo.id, modInfo.version.toString())

    val isEnabled = isEnabledInGame && isEnabledInSmol

    data class Archived(
        val file: File
    )

    data class Staged(
        val folder: File
    )

    val exists = staged != null || archived != null
}