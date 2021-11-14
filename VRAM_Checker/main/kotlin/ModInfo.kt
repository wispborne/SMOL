import java.nio.file.Path

data class ModInfo(
    val id: String,
    val folder: Path,
    val name: String,
    val version: String
) {
    val formattedName = "$name $version ($id)"
}