import VramChecker.Companion.VANILLA_BACKGROUND_TEXTURE_SIZE_IN_BYTES
import java.nio.file.Path
import kotlin.math.ceil

/**
 * @param textureHeight Next highest power of two
 * @param textureWidth Next highest power of two
 */
internal data class ModImage(
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