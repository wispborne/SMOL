package smol_access.model

data class UserProfile(
    val id: Int,
    val username: String,
    val activeModProfileId: Int,
    val versionCheckerIntervalMillis: Long?,
    val modProfiles: List<ModProfile>,
    val profileVersion: Int,
    val theme: String?,
    val favoriteMods: List<ModId>,
    val modGridPrefs: ModGridPrefs
) {
    val activeModProfile: ModProfile
        get() = modProfiles.firstOrNull { it.id == activeModProfileId } ?: modProfiles.first()


    data class ModProfile(
        val id: Int,
        val name: String,
        val description: String,
        val sortOrder: Int,
        val enabledModVariants: List<EnabledModVariant>
    ) {
        data class EnabledModVariant(
            val modId: String,
            val smolVariantId: String,
            val version: Version?
        )
    }

    data class ModGridPrefs(
        val sortField: String?,
        val isSortDescending: Boolean = true
    )
}