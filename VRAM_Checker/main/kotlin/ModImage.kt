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

import VramChecker.Companion.VANILLA_BACKGROUND_TEXTURE_SIZE_IN_BYTES
import java.nio.file.Path
import kotlin.math.ceil

/**
 * @param textureHeight Next highest power of two
 * @param textureWidth Next highest power of two
 */
data class ModImage(
    val file: Path,
    val textureHeight: Int,
    val textureWidth: Int,
    val bitsInAllChannels: List<Int>,
    val imageType: ImageType
) {
    /**
     * Textures are mipmapped and therefore use 125% memory. Backgrounds are not.
     */
    val multiplier = if (imageType == ImageType.Background) 1f else 4f / 3f
    val bytesUsed by lazy {
        ceil( // Round up
            (textureHeight *
                    textureWidth *
                    (bitsInAllChannels.sum() / 8) *
                    multiplier) -
                    // Number of bytes in a vanilla background image
                    // Only count any excess toward the mod's VRAM hit
                    if (imageType == ImageType.Background) VANILLA_BACKGROUND_TEXTURE_SIZE_IN_BYTES else 0f
        )
            .toLong()
    }

    enum class ImageType {
        Texture,
        Background,
        Unused
    }
}