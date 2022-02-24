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

data class UserProfile(
    val id: Int,
    val username: String,
    val activeModProfileId: String,
    val versionCheckerIntervalMillis: Long?,
    val modProfiles: List<ModProfile>,
    val profileVersion: Int,
    val theme: String?,
    val favoriteMods: List<ModId>,
    val modGridPrefs: ModGridPrefs
) {
    val activeModProfile: ModProfile
        get() = modProfiles.firstOrNull { it.id == activeModProfileId } ?: modProfiles.first()


    data class ModProfile(
        val id: String,
        val name: String,
        val description: String,
        val sortOrder: Int,
        val enabledModVariants: List<ShallowModVariant>
    ) {
        data class ShallowModVariant(
            val modId: String,
            val modName: String?,
            val smolVariantId: SmolId,
            val version: Version?
        )
    }

    data class ModGridPrefs(
        val sortField: String?,
        val isSortDescending: Boolean = true
    )
}