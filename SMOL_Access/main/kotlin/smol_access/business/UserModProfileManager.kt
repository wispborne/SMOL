package smol_access.business

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.model.UserProfile
import timber.ktx.Timber
import utilities.diff

class UserModProfileManager internal constructor(
    private val userManager: UserManager,
    private val access: Access,
    private val modLoader: ModLoader
) {
    /**
     * Prevent profiles updating while we're switching mod profiles.
     */
    var isModProfileSwitching = false

    init {
        GlobalScope.launch {
            modLoader.mods.collect { newMods ->
                newMods ?: return@collect
                if (isModProfileSwitching) return@collect

                userManager.updateUserProfile { oldProfile ->
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

    suspend fun switchModProfile(newModProfileId: Int) {
        try {
            isModProfileSwitching = true
            val newModProfile = userManager.getUserProfile().modProfiles.firstOrNull { it.id == newModProfileId }
                ?: throw NullPointerException("Unable to find mod profile $newModProfileId.")

            val diff =
                userManager.getUserProfile().activeModProfile.enabledModVariants.diff(newModProfile.enabledModVariants) { it }

            val variantsToDisable = diff.removed
            val variantsToEnable = diff.added
            val allKnownVariants = modLoader.mods.value?.flatMap { it.variants } ?: emptyList()

            variantsToDisable
                .mapNotNull { varToDisable ->
                    allKnownVariants.firstOrNull { knownVar -> knownVar.smolId == varToDisable.smolVariantId }
                        .also {
                            if (it == null) {
                                // Just log as debug, not an issue if we can't disable something that doesn't exist anyway.
                                Timber.d { "Cannot disable variant $varToDisable, as it cannot be found." }
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
                                Timber.e { "Cannot enable variant $varToEnable, as it cannot be found." }
                            }
                        }
                }
                .forEach { variant ->
                    access.enable(variant)
                }

            userManager.updateUserProfile { it.copy(activeModProfileId = newModProfileId) }
            Timber.d { "Changed mod profile to $newModProfile" }
        } finally {
            isModProfileSwitching = false
        }
    }
}