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
    init {
        GlobalScope.launch {
            modLoader.onModsReloaded.collect { newMods ->
                newMods ?: return@collect
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

    fun updateUserProfile(mutator: (oldProfile: UserProfile) -> UserProfile) {
        val newProfile = mutator(getUserProfile())
        appConfig.userProfile = newProfile
        Logger.debug { "Updated active profile ${newProfile.activeModProfile.name} to ${newProfile.activeModProfile}" }
    }

    suspend fun changeModProfile(newModProfile: UserProfile.ModProfile) {
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

        Logger.debug { "Changed mod profile to $newModProfile" }
    }

    fun removeModProfile(profileId: Int) {
        updateUserProfile { oldProfile ->
            val profileToRemove = oldProfile.modProfiles.firstOrNull { it.id == profileId }

            if (profileToRemove == null) {
                throw RuntimeException("Profile $profileId not found.")
            } else {
                return@updateUserProfile oldProfile.copy(modProfiles = oldProfile.modProfiles.filterNot { it.id == profileId })
                    .also {
                        Logger.debug { "Removed mod profile $profileToRemove" }
                    }
            }
        }
    }
}