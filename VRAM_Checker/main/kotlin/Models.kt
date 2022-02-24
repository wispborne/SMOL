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

import com.fasterxml.jackson.annotation.JsonProperty

internal data class EnabledModsJsonModel(@JsonProperty("enabledMods") val enabledMods: List<String>)

internal data class ModInfoJsonModel_091a(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: String,
)

internal data class ModInfoJsonModel_095a(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: Version_095a,
) {
    data class Version_095a(
        @JsonProperty("major") val major: String,
        @JsonProperty("minor") val minor: String,
        @JsonProperty("patch") val patch: String
    )
}