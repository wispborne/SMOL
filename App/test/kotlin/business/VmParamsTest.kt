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

package business

import smol_access.model.Vmparams
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class VmParamsTest {

    @Test
    fun vmParamsChange() {
        val value =
            "java.exe -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -XX:CompilerThreadPriority=1 -XX:+CompilerThreadHintNoPreempt -Djava.library.path=native\\\\windows -XX:PermSize=256m -XX:MaxPermSize=256m -Xms6g -Xms6g -Xmx6g -Xss2048k -classpath janino.jar;commons-compiler.jar;commons-compiler-jdk.jar;starfarer.api.jar;starfarer_obf.jar;jogg-0.0.7.jar;jorbis-0.0.15.jar;json.jar;lwjgl.jar;jinput.jar;log4j-1.2.9.jar;lwjgl_util.jar;fs.sound_obf.jar;fs.common_obf.jar;xstream-1.4.10.jar -Dcom.fs.starfarer.settings.paths.saves=..\\\\saves -Dcom.fs.starfarer.settings.paths.screenshots=..\\\\screenshots -Dcom.fs.starfarer.settings.paths.mods=..\\\\mods -Dcom.fs.starfarer.settings.paths.logs=. com.fs.starfarer.StarfarerLauncher"

        assertTrue { Vmparams(value) != Vmparams(value).withMb(20) }
        assertTrue { Vmparams(value) != Vmparams(value).withGb(20) }
        assertTrue { Vmparams(value) != Vmparams(value).withRam("20g") }
    }
}