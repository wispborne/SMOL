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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.access.model.UserProfile
import smol.timber.ktx.Timber
import smol.utilities.diff
import smol.utilities.parallelMap
import smol.utilities.trace
import java.time.ZonedDateTime

class UserModProfileManager internal constructor(
    private val userManager: UserManager,
    private val access: smol.access.Access,
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
                                    val newEnabledModVariants = newMods.mods
                                        .flatMap { it.enabledVariants }
                                        .mapNotNull {
                                            val mod = it.mod(modsCache) ?: return@mapNotNull null
                                            UserProfile.ModProfile.ShallowModVariant(
                                                modId = mod.id,
                                                modName = it.modInfo.name ?: "",
                                                smolVariantId = it.smolId,
                                                version = it.modInfo.version
                                            )
                                        }

                                    // If mods haven't changed, don't update the profile.
                                    if (newEnabledModVariants != oldProfile.activeModProfile.enabledModVariants) {
                                        profile.copy(
                                            enabledModVariants = newEnabledModVariants,
                                            dateModified = ZonedDateTime.now()
                                        )
                                    } else profile
                                } else profile
                            }
                    )
                }
            }
        }
    }

    /**
     * @param modVariantOverrides List of mod variants to use in the mod profile, irrespective of what the mod profile uses for those mods.
     */
    suspend fun switchModProfile(
        newModProfileId: String,
        modVariantOverrides: List<UserProfile.ModProfile.ShallowModVariant> = emptyList()
    ) {
        try {
            isModProfileSwitching = true
            val newModProfile = userManager.activeProfile.value.modProfiles.firstOrNull { it.id == newModProfileId }
                ?: throw NullPointerException("Unable to find mod profile $newModProfileId.")
            Timber.i { "Changing mod profile to ${newModProfile.name}" }
            trace(onFinished = { _, millis: Long -> Timber.i { "Changed mod profile to ${newModProfile.name} in ${millis}ms." } }) {
                // Remove any mods that are overridden by the modVariantOverrides list, then add the overrides.
                val newListOfMods = newModProfile.enabledModVariants
                    .filter { profileMod -> profileMod.modId !in modVariantOverrides.map { override -> override.modId } }
                    .let { it + modVariantOverrides }

                val currentlyEnabledMods = access.mods.value?.mods.orEmpty()
                    .mapNotNull {
                        it.findFirstEnabled?.let { firstEnableVariant ->
                            UserProfile.ModProfile.ShallowModVariant(firstEnableVariant)
                        }
                    }
                val diff =
                    currentlyEnabledMods.diff(newListOfMods) { it.smolVariantId }

                Timber.i { "Mod profile diff for switching: $diff." }
                val variantsToDisable = diff.removed
                val variantsToEnable = diff.added
                val allKnownVariants = modsCache.mods.value?.mods?.flatMap { it.variants } ?: emptyList()

                val previouslyDisabledVariants = allKnownVariants
                    .filter { it.mod(access)?.isEnabled(it) == false }
                    .map { UserProfile.ModProfile.ShallowModVariant(it) }
                val fullyDisabledModsAfterSwitch = (previouslyDisabledVariants - variantsToEnable.toSet())
                    .associateBy { it.modId }
                    .values
                    .map { it.modId }

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
                        // When disabling a mod entirely, we want to leave the highest version of the mod visible in the launcher.
                        // Other variants should be bricked so they don't show up.
                        // If the mod isn't disabled entirely, the enabled variant will show up in the launcher.
                        val isThisHighestVersionOfFullyDisabledMod =
                            variant.modInfo.id in fullyDisabledModsAfterSwitch && variant.mod(access)?.findHighestVersion == variant

                        access.disableModVariant(
                            modVariant = variant,
                            changeFileExtension = true//!isThisHighestVersionOfFullyDisabledMod
                        )
                    }

                // Need to reload mods that we're about to enable in case they were just disabled.
                // If they were disabled, the cached enabled_mods.json could be out of date and we wouldn't know to enable them again.
                access.reload((variantsToEnable + variantsToDisable).map { it.modId })

                variantsToEnable
                    .mapNotNull { varToEnable ->
                        allKnownVariants.firstOrNull { knownVar -> knownVar.smolId == varToEnable.smolVariantId }
                            .also {
                                if (it == null) {
                                    Timber.e { "Cannot enable variant $varToEnable, as it cannot be found." }
                                }
                            }
                    }
                    .parallelMap { variant ->
                        variant.mod(access)?.let { mod ->
                            access.changeActiveVariant(mod, variant)
                        }
                    }

                access.reload()
                access.ensureLatestDisabledVariantIsVisibleInVanillaLauncher(access.mods.value?.mods.orEmpty())

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