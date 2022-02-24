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