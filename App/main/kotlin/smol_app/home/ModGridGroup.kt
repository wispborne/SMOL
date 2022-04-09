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

package smol_app.home

import smol_access.business.ModMetadataManager
import smol_access.business.metadata
import smol_access.model.Mod
import smol_access.model.UserProfile
import smol_app.util.uiEnabled

sealed class ModGridGroup {
    abstract fun getGroupName(mod: Mod): String?
    abstract fun getGroupSortValue(mod: Mod): Comparable<*>?
//    open fun getGroupComparator(): Comparator<K?> = compareBy<K> { it }

    object EnabledStateModGridGroup : ModGridGroup() {
        override fun getGroupName(mod: Mod): String =
            when (mod.uiEnabled) {
                true -> "Enabled"
                false -> "Disabled"
            }

        override fun getGroupSortValue(mod: Mod) = !mod.uiEnabled
    }

    class CategoryModGridGroup(private val modMetadataManager: ModMetadataManager) : ModGridGroup() {
        override fun getGroupName(mod: Mod): String = mod.metadata(modMetadataManager)?.category ?: "No Category"
        override fun getGroupSortValue(mod: Mod) = mod.metadata(modMetadataManager)?.category?.lowercase() ?: "zzzzzzzzzzzzzzzzzzzz"
    }

    object AuthorModGridGroup : ModGridGroup() {
        override fun getGroupName(mod: Mod): String =
            mod.findFirstEnabledOrHighestVersion?.modInfo?.author ?: "No Author"

        override fun getGroupSortValue(mod: Mod) =
            mod.findFirstEnabledOrHighestVersion?.modInfo?.author?.lowercase()
    }

    object ModTypeModGridGroup : ModGridGroup() {
        override fun getGroupName(mod: Mod): String {
            val modInfo = mod.findFirstEnabledOrHighestVersion?.modInfo
            return when {
                modInfo?.isUtilityMod == true -> "Utility"
                modInfo?.isTotalConversion == true -> "Total Conversion"
                else -> "Other"
            }
        }

        override fun getGroupSortValue(mod: Mod): Comparable<*> {
            val modInfo = mod.findFirstEnabledOrHighestVersion?.modInfo
            return when {
                modInfo?.isUtilityMod == true -> "Utility"
                modInfo?.isTotalConversion == true -> "Total Conversion"
                else -> "zzzzzzzzz"
            }
        }
    }

    object GameVersionModGridGroup : ModGridGroup() {
        override fun getGroupName(mod: Mod): String =
            mod.findFirstEnabledOrHighestVersion?.modInfo?.gameVersion ?: "Unknown"

        override fun getGroupSortValue(mod: Mod) = getGroupName(mod).lowercase()
    }
}

fun UserProfile.ModGridGroupEnum.mapToGroup(modMetadataManager: ModMetadataManager) =
    when (this) {
        UserProfile.ModGridGroupEnum.EnabledState -> ModGridGroup.EnabledStateModGridGroup
        UserProfile.ModGridGroupEnum.Author -> ModGridGroup.AuthorModGridGroup
        UserProfile.ModGridGroupEnum.Category -> ModGridGroup.CategoryModGridGroup(modMetadataManager)
        UserProfile.ModGridGroupEnum.ModType -> ModGridGroup.ModTypeModGridGroup
        UserProfile.ModGridGroupEnum.GameVersion -> ModGridGroup.GameVersionModGridGroup
    }