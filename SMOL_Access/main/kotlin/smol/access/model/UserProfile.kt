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

package smol.access.model

import java.time.ZonedDateTime

data class UserProfile(
    val id: Int,
    val username: String,
    val activeModProfileId: String,
    val versionCheckerIntervalMillis: Long?,
    val modProfiles: List<ModProfile>,
    val profileVersion: Int,
    val theme: String?,
    val favoriteMods: List<ModId>,
    val modGridPrefs: ModGridPrefs,
    val showGameLauncherWarning: Boolean?,
    val launchButtonAction: LaunchButtonAction?,
    val useOrbitronNameFont: Boolean?,
    val warnAboutOneClickUpdates: Boolean?,
) {
    val activeModProfile: ModProfile
        get() = modProfiles.firstOrNull { it.id == activeModProfileId } ?: modProfiles.first()


    data class ModProfile(
        val id: String,
        val name: String,
        val description: String,
        val sortOrder: Int,
        val enabledModVariants: List<ShallowModVariant>,
        val dateCreated: ZonedDateTime?,
        val dateModified: ZonedDateTime?
    ) {
        data class ShallowModVariant(
            val modId: String,
            val modName: String?,
            val smolVariantId: SmolId,
            val version: Version?
        ) {
            constructor(variant: ModVariant) : this(
                variant.modInfo.id,
                variant.modInfo.name,
                variant.smolId,
                variant.modInfo.version
            )
        }
    }

    enum class ModGridHeader {
        Favorites,
        ChangeVariantButton,
        Name,
        Author,
        Version,
        VramImpact,
        Icons,
        GameVersion,
        Category
    }

    enum class ModGridGroupEnum {
        EnabledState,
        Author,
        Category,
        ModType,
        GameVersion,
    }

    data class ModGridPrefs(
        val sortField: String?,
        val isSortDescending: Boolean = true,
        val columnSettings: Map<ModGridHeader, ModGridColumnSetting>?,
        val groupingSetting: GroupingSetting?,
    )

    data class GroupingSetting(
        val grouping: ModGridGroupEnum,
        val isSortDescending: Boolean = false,
    )

    data class ModGridColumnSetting(
        val position: Int,
        val isVisible: Boolean = true
    )

    enum class LaunchButtonAction {
        @Deprecated("Direct launch is just fucked, don't let users do it anymore, it keeps wasting their time with chaos bugs.")
        DirectLaunch,
        OpenFolder
    }
}