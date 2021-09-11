import kotlin.random.Random

data class Mod(
    val modInfo: ModInfo,
    val isEnabled: Boolean = Random.nextBoolean(),
    val path: String = ""
)