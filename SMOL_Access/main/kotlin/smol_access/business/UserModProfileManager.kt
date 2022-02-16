package smol_access.business

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.model.UserProfile
import timber.ktx.Timber
import utilities.diff
import utilities.trace

class UserModProfileManager internal constructor(
    private val userManager: UserManager,
    private val access: Access,
    private val modsCache: ModsCache
) {
    /**
     * Prevent profiles updating while we're switching mod profiles.
     */
    var isModProfileSwitching = false

    init {
        GlobalScope.launch {
            modsCache.mods.collect { newMods ->
                newMods ?: return@collect
                if (isModProfileSwitching) return@collect

                userManager.updateUserProfile { oldProfile ->
                    oldProfile.copy(
                        modProfiles = oldProfile.modProfiles
                            .map { profile ->
                                if (profile.id == oldProfile.activeModProfileId) {
                                    profile.copy(enabledModVariants = newMods.mods
                                        .flatMap { it.enabledVariants }
                                        .map {
                                            UserProfile.ModProfile.ShallowModVariant(
                                                modId = it.mod(modsCache).id,
                                                modName = it.modInfo.name ?: "",
                                                smolVariantId = it.smolId,
                                                version = it.modInfo.version
                                            )
                                        })
                                } else
                                    profile
                            }
                    )
                }
            }
        }
    }

    suspend fun switchModProfile(newModProfileId: String) {
        try {
            isModProfileSwitching = true
            val newModProfile = userManager.activeProfile.value.modProfiles.firstOrNull { it.id == newModProfileId }
                ?: throw NullPointerException("Unable to find mod profile $newModProfileId.")
            Timber.i { "Changing mod profile to ${newModProfile.name}" }
            trace(onFinished = { _, millis: Long -> Timber.i { "Changed mod profile to ${newModProfile.name} in ${millis}ms." } }) {
                val diff =
                    userManager.activeProfile.value.activeModProfile.enabledModVariants.diff(newModProfile.enabledModVariants) { it.smolVariantId }

                Timber.i { "Mod profile diff for switching: $diff." }
                val variantsToDisable = diff.removed
                val variantsToEnable = diff.added
                val allKnownVariants = modsCache.mods.value?.mods?.flatMap { it.variants } ?: emptyList()

                variantsToDisable
                    .mapNotNull { varToDisable ->
                        allKnownVariants.firstOrNull { knownVar -> knownVar.smolId == varToDisable.smolVariantId }
                            .also {
                                if (it == null) {
                                    // Just log as info, not an issue if we can't disable something that doesn't exist anyway.
                                    Timber.i { "Cannot disable variant $varToDisable, as it cannot be found." }
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
                Timber.i { "Changed mod profile to $newModProfile" }
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