package smol_access.model

data class UserProfile(
    val id: Int,
    val username: String,
    val activeModProfileId: String,
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
        val id: String,
        val name: String,
        val description: String,
        val sortOrder: Int,
        val enabledModVariants: List<ShallowModVariant>
    ) {
        data class ShallowModVariant(
            val modId: String,
            val modName: String?,
            val smolVariantId: SmolId,
            val version: Version?
        )
    }

    data class ModGridPrefs(
        val sortField: String?,
        val isSortDescending: Boolean = true
    )
}