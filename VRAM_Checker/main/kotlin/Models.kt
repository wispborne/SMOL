import com.fasterxml.jackson.annotation.JsonProperty

internal data class EnabledModsJsonModel(@JsonProperty("enabledMods") val enabledMods: List<String>)

internal data class ModInfoJsonModel_091a(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: String,
)

internal data class ModInfoJsonModel_095a(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: Version_095a,
) {
    data class Version_095a(
        @JsonProperty("major") val major: String,
        @JsonProperty("minor") val minor: String,
        @JsonProperty("patch") val patch: String
    )
}