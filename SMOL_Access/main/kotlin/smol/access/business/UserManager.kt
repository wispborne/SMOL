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

package smol.access.business

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import smol.access.config.AppConfig
import smol.access.model.UserProfile
import smol.access.themes.ThemeManager
import smol.timber.ktx.Timber
import smol.utilities.mapState
import smol.utilities.merge
import java.time.ZonedDateTime
import java.util.*

class UserManager internal constructor(
    private val appConfig: AppConfig,
) {
    companion object {
        val defaultModProfile = UserProfile.ModProfile(
            id = UUID.randomUUID().toString(),
            name = "default",
            description = "Default profile",
            sortOrder = 0,
            enabledModVariants = emptyList(),
            dateCreated = ZonedDateTime.now(),
            dateModified = ZonedDateTime.now(),
        )

        val defaultProfile = UserProfile(
            id = 0,
            username = "default",
            activeModProfileId = defaultModProfile.id,
            versionCheckerIntervalMillis = VersionChecker.DEFAULT_CHECK_INTERVAL_MILLIS,
            modProfiles = listOf(defaultModProfile),
            profileVersion = 0,
            theme = ThemeManager.defaultTheme.first,
            favoriteMods = emptyList(),
            modGridPrefs = UserProfile.ModGridPrefs(
                sortField = null,
                isSortDescending = true,
                columnSettings = mapOf(
                    UserProfile.ModGridHeader.Favorites to UserProfile.ModGridColumnSetting(
                        position = 0
                    ),
                    UserProfile.ModGridHeader.ChangeVariantButton to UserProfile.ModGridColumnSetting(
                        position = 1
                    ),
                    UserProfile.ModGridHeader.Name to UserProfile.ModGridColumnSetting(
                        position = 2
                    ),
                    UserProfile.ModGridHeader.Author to UserProfile.ModGridColumnSetting(
                        position = 3
                    ),
                    UserProfile.ModGridHeader.Version to UserProfile.ModGridColumnSetting(
                        position = 4
                    ),
                    UserProfile.ModGridHeader.VramImpact to UserProfile.ModGridColumnSetting(
                        position = 5
                    ),
                    UserProfile.ModGridHeader.Icons to UserProfile.ModGridColumnSetting(
                        position = 6
                    ),
                    UserProfile.ModGridHeader.GameVersion to UserProfile.ModGridColumnSetting(
                        position = 7
                    ),
                    UserProfile.ModGridHeader.Category to UserProfile.ModGridColumnSetting(
                        position = 8
                    ),
                ),
                groupingSetting = UserProfile.GroupingSetting(
                    grouping = UserProfile.ModGridGroupEnum.EnabledState,
                    isSortDescending = false
                )
            ),
            showGameLauncherWarning = true,
            launchButtonAction = UserProfile.LaunchButtonAction.OpenFolder,
            useOrbitronNameFont = false,
            warnAboutOneClickUpdates = true,
            removedTipHashcodes = emptySet()
        )
    }

    private val scope = CoroutineScope(Job())

    /**
     * The active profile, which has defaults as a base with user overrides merged on top.
     */
    val activeProfile: StateFlow<UserProfile> = appConfig.userProfile.asStateFlow()
        .mapState(scope = scope) {
            defaultProfile.merge(preferredObj = it)
        }

    init {
        // Update config whenever value changes.
        CoroutineScope(Dispatchers.Default).launch {
            activeProfile.collect {
                appConfig.userProfile.value = it
            }
        }
    }

    fun updateUserProfile(mutator: (oldProfile: UserProfile) -> UserProfile): UserProfile {
        val newProfile = mutator(activeProfile.value)
        appConfig.userProfile.value = newProfile
        Timber.i { "Updated active profile '${newProfile.username}'." }
        Timber.d { newProfile.toString() }
        return newProfile
    }

    fun createModProfile(
        name: String,
        description: String = "",
        sortOrder: Int?,
        enabledModVariants: List<UserProfile.ModProfile.ShallowModVariant>
    ): UserProfile {
        return updateUserProfile { userProfile ->
            val newModProfile = UserProfile.ModProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                sortOrder = sortOrder ?: ((userProfile.modProfiles.maxOfOrNull { it.sortOrder } ?: 0) + 1),
                enabledModVariants = enabledModVariants,
                dateCreated = ZonedDateTime.now(),
                dateModified = ZonedDateTime.now(),
            )
            userProfile.copy(modProfiles = userProfile.modProfiles + newModProfile)
                .also { Timber.i { "Created mod profile $newModProfile" } }
        }
    }

    fun removeModProfile(modProfileId: String) {
        updateUserProfile { oldProfile ->
            val profileToRemove = oldProfile.modProfiles.firstOrNull { it.id == modProfileId }

            if (profileToRemove == null) {
                throw RuntimeException("Profile $modProfileId not found.")
            } else {
                return@updateUserProfile oldProfile.copy(modProfiles = oldProfile.modProfiles.filterNot { it.id == modProfileId })
                    .also { Timber.i { "Removed mod profile $profileToRemove" } }
            }
        }
    }

    fun setModFavorited(modId: String, newFavoriteValue: Boolean) {
        updateUserProfile { profile ->
            profile.copy(favoriteMods =
            if (newFavoriteValue) (profile.favoriteMods + modId).distinct()
            else profile.favoriteMods.filter { it != modId })
        }
    }

    fun reloadUser() {
        appConfig.reload()
//        appConfig.userProfile.value = appConfig.userProfile.value ?: return
    }
}