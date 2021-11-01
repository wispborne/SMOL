package business

import Access
import config.AppConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import model.UserProfile
import org.tinylog.kotlin.Logger
import util.diff

class UserManager internal constructor(
    private val appConfig: AppConfig,
    private val access: Access,
    private val modLoader: ModLoader
) {
    /**
     * Prevent profiles updating while we're switching mod profiles.
     */
    var isModProfileSwitching = false

    init {
        GlobalScope.launch {
            modLoader.onModsReloaded.collect { newMods ->
                newMods ?: return@collect
                if (isModProfileSwitching) return@collect

                updateUserProfile { oldProfile ->
                    oldProfile.copy(
                        modProfiles = oldProfile.modProfiles
                            .map { profile ->
                                if (profile.id == oldProfile.activeModProfileId) {
                                    profile.copy(enabledModVariants = newMods
                                        .flatMap { it.enabledVariants }
                                        .map { UserProfile.ModProfile.EnabledModVariant(it.mod.id, it.smolId) })
                                } else
                                    profile
                            }
                    )
                }
            }
        }
    }

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
                modProfiles = listOf(defaultModProfile),
                profileVersion = 0
            )
        }
    }

    fun updateUserProfile(mutator: (oldProfile: UserProfile) -> UserProfile): UserProfile {
        val newProfile = mutator(getUserProfile())
        appConfig.userProfile = newProfile
        Logger.debug { "Updated active profile ${newProfile.username} to $newProfile" }
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
                .also { Logger.debug { "Created mod profile $newModProfile" } }
        }
    }

    fun removeModProfile(modProfileId: Int) {
        updateUserProfile { oldProfile ->
            val profileToRemove = oldProfile.modProfiles.firstOrNull { it.id == modProfileId }

            if (profileToRemove == null) {
                throw RuntimeException("Profile $modProfileId not found.")
            } else {
                return@updateUserProfile oldProfile.copy(modProfiles = oldProfile.modProfiles.filterNot { it.id == modProfileId })
                    .also { Logger.debug { "Removed mod profile $profileToRemove" } }
            }
        }
    }

    suspend fun switchModProfile(newModProfileId: Int) {
        try {
            isModProfileSwitching = true
            val newModProfile = getUserProfile().modProfiles.firstOrNull { it.id == newModProfileId }
                ?: throw NullPointerException("Unable to find mod profile $newModProfileId.")

            val diff =
                getUserProfile().activeModProfile.enabledModVariants.diff(newModProfile.enabledModVariants) { it }

            val variantsToDisable = diff.removed
            val variantsToEnable = diff.added
            val allKnownVariants = modLoader.getMods(noCache = false).flatMap { it.variants }

            variantsToDisable
                .mapNotNull { varToDisable ->
                    allKnownVariants.firstOrNull { knownVar -> knownVar.smolId == varToDisable.smolVariantId }
                        .also {
                            if (it == null) {
                                // Just log as debug, not an issue if we can't disable something that doesn't exist anyway.
                                Logger.debug { "Cannot disable variant $varToDisable, as it cannot be found." }
                            }
                        }
                }
                .forEach { variant ->
                    access.disable(variant)
                }

            variantsToEnable
                .mapNotNull { varToEnable ->
                    allKnownVariants.firstOrNull { knownVar -> knownVar.smolId == varToEnable.smolVariantId }
                        .also {
                            if (it == null) {
                                Logger.error { "Cannot enable variant $varToEnable, as it cannot be found." }
                            }
                        }
                }
                .forEach { variant ->
                    access.enable(variant)
                }

            updateUserProfile { it.copy(activeModProfileId = newModProfileId) }
            Logger.debug { "Changed mod profile to $newModProfile" }
        } finally {
            isModProfileSwitching = false
        }
    }
}