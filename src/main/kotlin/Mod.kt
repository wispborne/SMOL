import kotlin.random.Random

data class Mod(
    val modInfo: ModInfo,
    val isEnabled: Boolean = Random.nextBoolean(),
    val path: String = ""
)

data class ModInfo(
    val name: String,
    val version: String
)

data class Version(
    val raw: String
) {
    override fun toString() = raw
}