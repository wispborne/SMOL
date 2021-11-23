package smol_app.util

data class SmolWindowState(
    val placement: String,
    val isMinimized: Boolean,
    val position: SmolPair<Float, Float>,
    val size: SmolPair<Float, Float>
)

/**
 * Because Moshi won't serialize Kotlin's Pair.
 */
data class SmolPair<T, K>(val first: T, val second: K)