package smol_access.model

import smol_access.Constants
import utilities.equalsAny
import java.nio.file.Path
import kotlin.io.path.name

data class ModInfo(
    val id: String,
    val name: String?,
    val author: String?,
    val description: String?,
    val requiredMemoryMB: String?,
    val gameVersion: String?,
    val jars: List<String>,
    val modPlugin: String,
    val version: Version,
    val dependencies: List<Dependency>,
    val isUtilityMod: Boolean,
)

data class Dependency(
    val id: String? = null,
    val name: String? = null,
    val version: Version?
)

fun Path.isModInfoFile() =
    this.name.equalsAny(Constants.MOD_INFO_FILE, Constants.MOD_INFO_FILE_DISABLED, ignoreCase = true)