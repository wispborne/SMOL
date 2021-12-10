package smol_access.business

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.model.UserProfile
import timber.ktx.Timber
import utilities.diff
import utilities.trace

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
                                        .map { UserProfile.ModProfile.EnabledModVariant(it.mod(modLoader).id, it.smolId) })
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
            val newModProfile = userManager.activeProfile.value.modProfiles.firstOrNull { it.id == newModProfileId }
                ?: throw NullPointerException("Unable to find mod profile $newModProfileId.")
            Timber.i { "Changing mod profile to ${newModProfile.name}" }
            trace(onFinished = { _, millis: Long -> Timber.d { "Changed mod profile to ${newModProfile.name} in ${millis}ms." } }) {
                val diff =
                    userManager.activeProfile.value.activeModProfile.enabledModVariants.diff(newModProfile.enabledModVariants) { it.smolVariantId }

                Timber.d { "Mod profile diff for switching: $diff." }
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
                        access.disableModVariant(variant)
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
                        access.enableModVariant(variant)
                    }

                userManager.updateUserProfile { it.copy(activeModProfileId = newModProfileId) }
                Timber.d { "Changed mod profile to $newModProfile" }
            }
        } catch (e: Exception) {
            Timber.e(e) {
                "Failed to switch mod profile,"
            }
            throw e
        } finally {
            isModProfileSwitching = false
        }
    }
}