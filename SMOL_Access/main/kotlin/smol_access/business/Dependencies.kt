package smol_access.business

import smol_access.model.Dependency
import smol_access.model.Mod
import smol_access.model.ModVariant
import org.tinylog.Logger


fun ModVariant.findDependencies(mods: List<Mod>): List<Pair<Dependency, Mod?>> =
    this.modInfo.dependencies
        .map { dep -> dep to mods.firstOrNull { it.id == dep.id } }

fun ModVariant.findDependencyStates(mods: List<Mod>): List<DependencyState> =
    (mod.findFirstEnabled?.findDependencies(mods) ?: emptyList())
        .map { (dependency, foundDependencyMod) ->
            // Mod not found or has no variants, it's missing.
            if (foundDependencyMod == null || foundDependencyMod.variants.isEmpty())
                return@map smol_access.business.DependencyState.Missing(
                    dependency = dependency,
                    outdatedModIfFound = null
                )

            return@map if (dependency.version == null) {
                if (foundDependencyMod.hasEnabledVariant) {
                    // No specific version needed, one is enabled, all good.
                    smol_access.business.DependencyState.Enabled(dependency, foundDependencyMod.findFirstEnabled!!)
                } else {
                    // No specific version needed, none are enabled but one exists, return highest version available.
                    smol_access.business.DependencyState.Disabled(dependency, foundDependencyMod.findHighestVersion!!)
                }
            } else {
                val variantsMeetingVersionReq =
                    foundDependencyMod.variants.filter { it.modInfo.version >= dependency.version!! }

                if (variantsMeetingVersionReq.isEmpty()) {
                    // No variants found will work.
                    smol_access.business.DependencyState.Missing(dependency, foundDependencyMod)
                } else {
                    val alreadyEnabledAndValidDependency = variantsMeetingVersionReq
                        .filter { foundDependencyMod.isEnabled(it) }
                        .maxByOrNull { it.modInfo.version }

                    if (alreadyEnabledAndValidDependency != null) {
                        smol_access.business.DependencyState.Enabled(dependency, alreadyEnabledAndValidDependency)
                    } else {
                        val validButDisabledDependency = variantsMeetingVersionReq
                            .maxByOrNull { it.modInfo.version }
                        if (validButDisabledDependency == null) {
                            Logger.warn { "Unexpected scenario finding dependency for mod ${mod.id} with dependency: $dependency." }
                            smol_access.business.DependencyState.Missing(dependency, foundDependencyMod)
                        } else {
                            smol_access.business.DependencyState.Disabled(dependency, validButDisabledDependency)
                        }
                    }
                }
            }
        }
        .onEach {
            when (it) {
                is DependencyState.Disabled -> Logger.debug { "Dependency disabled: $it" }
                is DependencyState.Enabled -> Logger.trace { "Dependency enabled: $it" }
                is DependencyState.Missing -> Logger.debug { "Dependency missing: $it" }
            }
        }

sealed class DependencyState {
    abstract val dependency: Dependency

    data class Missing(override val dependency: Dependency, val outdatedModIfFound: Mod?) : DependencyState()
    data class Disabled(override val dependency: Dependency, val variant: ModVariant) : DependencyState()
    data class Enabled(override val dependency: Dependency, val variant: ModVariant) : DependencyState()
}