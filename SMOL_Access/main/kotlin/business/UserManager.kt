package business

import Access
import config.AppConfig
import model.UserProfile
import org.tinylog.kotlin.Logger
import util.diff
import java.util.*

class UserManager internal constructor(
    private val appConfig: AppConfig,
    private val access: Access,
    private val modLoader: ModLoader
) {
    fun getUserProfile(): UserProfile {
        return appConfig.userProfile ?: kotlin.run {
            val defaultModProfile = UserProfile.EnabledMods(
                id = UUID.randomUUID().toString(),
                name = "default",
                description = "Default profile",
                sortOrder = 0,
                enabledModVariants = emptyList()
            )

            return@run UserProfile(
                id = UUID.randomUUID().toString(),
                username = "default",
                activeModProfileId = defaultModProfile.id,
                modProfiles = listOf(defaultModProfile),
                profileVersion = 0
            )
        }
    }

    fun updateUserProfile(newProfile: UserProfile) {
        appConfig.userProfile = newProfile
    }

    suspend fun changeUserProfile(newProfile: UserProfile) {
        val diff =
            getUserProfile().activeModProfile.enabledModVariants.diff(newProfile.activeModProfile.enabledModVariants) { it }

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
    }
}