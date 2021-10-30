package model

data class UserProfile(
    val id: Int,
    val username: String,
    val activeModProfileId: Int,
    val modProfiles: List<ModProfile>,
    val profileVersion: Int
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
            val smolVariantId: String
        )
    }
}