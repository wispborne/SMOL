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

package smol.app.util

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