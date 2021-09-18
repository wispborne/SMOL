package model

import java.io.File
import java.util.*
import kotlin.random.Random

/**
 * @param staged null if not installed, not null otherwise
 */
data class Mod(
    val modInfo: ModInfo,
    val isEnabled: Boolean,
    val staged: Staged?,
    val archived: Archived?
) {
    val smolId: Int = Objects.hash(modInfo.id, modInfo.version.toString())

    data class Archived(
        val file: File
    )

    data class Staged(
        val folder: File
    )

    val exists = staged != null || archived != null
}