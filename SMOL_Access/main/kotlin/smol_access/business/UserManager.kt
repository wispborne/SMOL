package smol_access.business

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.config.AppConfig
import smol_access.model.UserProfile
import timber.ktx.Timber
import utilities.diff

class UserManager internal constructor(
    private val appConfig: AppConfig,
) {

    fun getUserProfile(): UserProfile {
        return appConfig.userProfile ?: kotlin.run {
            val defaultModProfile = UserProfile.ModProfile(
                id = 0,
                name = "default",
                description = "Default profile",
                sortOrder = 0,
                enabledModVariants = emptyList()
            )

            return@run UserProfile(
                id = 0,
                username = "default",
                activeModProfileId = defaultModProfile.id,
                versionCheckerIntervalMillis = VersionChecker.DEFAULT_CHECK_INTERVAL_MILLIS,
                modProfiles = listOf(defaultModProfile),
                profileVersion = 0,
                theme = "kemet"
            )
        }
    }

    fun updateUserProfile(mutator: (oldProfile: UserProfile) -> UserProfile): UserProfile {
        val newProfile = mutator(getUserProfile())
        appConfig.userProfile = newProfile
        Timber.d { "Updated active profile ${newProfile.username} to $newProfile" }
        return newProfile
    }

    fun createModProfile(
        name: String,
        description: String?,
        sortOrder: Int?,
    ): UserProfile {
        return updateUserProfile { userProfile ->
            val newModProfile = UserProfile.ModProfile(
                id = userProfile.modProfiles.maxOf { it.id } + 1, // New id is the previous highest id +1
                name = name,
                description = description ?: "",
                sortOrder = sortOrder ?: (userProfile.modProfiles.maxOf { it.sortOrder } + 1),
                enabledModVariants = emptyList()
            )
            userProfile.copy(modProfiles = userProfile.modProfiles + newModProfile)
                .also { Timber.d { "Created mod profile $newModProfile" } }
        }
    }

    fun removeModProfile(modProfileId: Int) {
        updateUserProfile { oldProfile ->
            val profileToRemove = oldProfile.modProfiles.firstOrNull { it.id == modProfileId }

            if (profileToRemove == null) {
                throw RuntimeException("Profile $modProfileId not found.")
            } else {
                return@updateUserProfile oldProfile.copy(modProfiles = oldProfile.modProfiles.filterNot { it.id == modProfileId })
                    .also { Timber.d { "Removed mod profile $profileToRemove" } }
            }
        }
    }
}