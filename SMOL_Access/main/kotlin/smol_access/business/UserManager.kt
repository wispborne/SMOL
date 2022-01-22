package smol_access.business

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import smol_access.config.AppConfig
import smol_access.model.UserProfile
import timber.ktx.Timber
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
            enabledModVariants = emptyList()
        )

        val defaultProfile = UserProfile(
            id = 0,
            username = "default",
            activeModProfileId = defaultModProfile.id,
            versionCheckerIntervalMillis = VersionChecker.DEFAULT_CHECK_INTERVAL_MILLIS,
            modProfiles = listOf(defaultModProfile),
            profileVersion = 0,
            theme = "kemet",
            favoriteMods = emptyList(),
            modGridPrefs = UserProfile.ModGridPrefs(sortField = null, isSortDescending = true)
        )
    }

    private val activeProfileInner = MutableStateFlow(appConfig.userProfile ?: defaultProfile)

    val activeProfile = activeProfileInner.asStateFlow()

    init {
        // Update config whenever value changes.
        CoroutineScope(Dispatchers.Default).launch {
            activeProfile.collect {
                appConfig.userProfile = it
            }
        }
    }

    fun updateUserProfile(mutator: (oldProfile: UserProfile) -> UserProfile): UserProfile {
        val newProfile = mutator(activeProfile.value)
        activeProfileInner.value = newProfile
        Timber.i { "Updated active profile ${newProfile.username} to $newProfile" }
        return newProfile
    }

    fun createModProfile(
        name: String,
        description: String = "",
        sortOrder: Int?,
        enabledModVariants: List<UserProfile.ModProfile.ShallowModVariant> = emptyList()
    ): UserProfile {
        return updateUserProfile { userProfile ->
            val newModProfile = UserProfile.ModProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                sortOrder = sortOrder ?: ((userProfile.modProfiles.maxOfOrNull { it.sortOrder } ?: 0) + 1),
                enabledModVariants = enabledModVariants
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
        activeProfileInner.value = appConfig.userProfile ?: return
    }
}