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

package smol.access.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test

internal class VersionCheckerInfoTest {

//    @Test
//    fun versionComparisonTemplate() {
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isLessThan(0)
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isGreaterThan(0)
//
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isLessThan(0)
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isGreaterThan(0)
//
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isLessThan(0)
//        assertThat(
//            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
//                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
//        ).isGreaterThan(0)
//    }

    @Test
    fun versionComparisonSimpleMajor() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "4", patch = "2")
                .compareTo(VersionCheckerInfo.Version(major = "2", minor = "4", patch = "2"))
        )
            .isLessThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "2", minor = "4", patch = "2")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "4", patch = "2"))
        ).isGreaterThan(0)
    }


    @Test
    fun versionComparisonSimpleMinor() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "0", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "2", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonSimplePatch() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "0")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "2")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonEqual() {
        val local = VersionCheckerInfo.Version(major = "0", minor = "2", patch = "3")
        val remote = VersionCheckerInfo.Version(major = "0", minor = "2", patch = "3")
        assertThat(local.compareTo(remote)).isEqualTo(0)
    }

    @Test
    fun versionComparisonMissingSegments() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = null)
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = null))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = null, patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = null, patch = "1"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = null, minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = null, minor = "1", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonLettersAndNumbers() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1a"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1a")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1b", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1b", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1b", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1b", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonLetterReplacement() {
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "a")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "b"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "b")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "a"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "d", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "e", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "g", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "f", patch = "1"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "V", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "X", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "z", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "G", minor = "1", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonFuzzy() {
        assertThat(
            VersionCheckerInfo.Version(major = "1-1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1-2", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1-1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "-", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "-"))
        ).isGreaterThan(0)

        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1-5")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1", patch = "1-56"))
        ).isLessThan(0)
        assertThat(
            VersionCheckerInfo.Version(major = "1", minor = "15-6", patch = "1")
                .compareTo(VersionCheckerInfo.Version(major = "1", minor = "1-6", patch = "1"))
        ).isGreaterThan(0)
    }

    @Test
    fun versionComparisonAlfonzo() {
        assertThat(
            VersionCheckerInfo.Version(major = "0", minor = "9", patch = "1z")
                .compareTo(VersionCheckerInfo.Version(major = "0", minor = "9", patch = "1aa"))
        ).isLessThan(0)
    }

    @Test
    fun versionComparisonTimid() {
        assertThat(
            VersionCheckerInfo.Version(major = "0", minor = "6", patch = "99999gggg")
                .compareTo(VersionCheckerInfo.Version(major = "0", minor = "6", patch = "99999gggggggggggg"))
        ).isLessThan(0)
    }

    @Test
    fun versionComparisonCy() {
        val versions = listOf(
            VersionCheckerInfo.Version(major = "0", minor = "7", patch = "2f3a"),
            VersionCheckerInfo.Version(major = "0", minor = "7", patch = "2g1dc"),
            VersionCheckerInfo.Version(major = "0", minor = "7", patch = "20"),
        )

        assertThat(versions.sorted()).isEqualTo(listOf(versions[0], versions[1], versions[2]))
    }
}