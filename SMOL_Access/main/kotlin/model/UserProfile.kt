package model

data class UserProfile(
    val id: String,
    val username: String,
    val activeModProfileId: String,
    val modProfiles: List<EnabledMods>,
    val profileVersion: Int
) {
    val activeModProfile: EnabledMods
        get() = modProfiles.firstOrNull { it.id == activeModProfileId } ?: modProfiles.first()


    data class EnabledMods(
        val id: String,
        val name: String,
        val description: String,
        val sortOrder: Int,
        val enabledModVariants: List<EnabledModVariant>
    ) {
        data class EnabledModVariant(
            val modId: String,
            val smolVariantId: Int
        )
    }
}