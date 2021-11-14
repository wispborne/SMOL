data class GraphicsLibInfo(
    val mapType: MapType,
    val relativeFilePath: String
) {
    enum class MapType {
        Normal,
        Material,
        Surface
    }
}