package business

import model.Vmparams
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