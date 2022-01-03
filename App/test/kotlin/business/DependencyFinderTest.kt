//package business
//
//import smol_access.model.*
//import org.junit.jupiter.api.Test
//import smol_access.business.DependencyFinder
//import java.nio.file.Path
//import kotlin.test.assertTrue
//
//internal class DependencyFinderTest {
//
//    @Test
//    fun lazyLibIsFoundDisabledForUngp() {
//        val deps = mods.single { it.id == "ungp" }.variants.first().findDependencyStates(mods)
//
//        println(deps)
//        val dep = deps.single()
//        assertTrue { dep is DependencyFinder.DependencyState.Disabled }
//        assertTrue {
//            val variant = (dep as DependencyFinder.DependencyState.Disabled).variant
//            variant == mods.single { it.id == "lw_lazylib" }.variants.first()
//        }
//    }
//
//    val mods = listOf(
//        Mod(
//            id = "ungp",
//            isEnabledInGame = true,
//            variants = listOf(
//                ModVariant(
//                    modInfo = ModInfo.v095(
//                        _id = "ungp",
//                        _name = "Unofficial New Game Plus",
//                        _author = "Originem\n" +
//                                "Translated By RUA,Fax\n" +
//                                "Sprites by Originem, Light of Shadow, Helai\n" +
//                                "Ideas from Haixian Group\n" +
//                                "Special Thanks: Game-icons.net",
//                        _utilityString = "false",
//                        versionString = Version.parse("1.3.0"),
//                        _description = "Requires Lazylib\n" +
//                                "A mod with a similar 'new game plus' function.\n" +
//                                " Left ctrl+P to call the menu",
//                        _gameVersion = "",
//                        _jars = emptyList(),
//                        _modPlugin = "",
//                        _dependencies = listOf(Dependency(_id = "lw_lazylib", name = "LazyLib", versionString = "2.6"))
//                    ),
//                    versionCheckerInfo = VersionCheckerInfo(
//                        masterVersionFile = "https://raw.githubusercontent.com/TruthOriginem/UNGP/master/UNGP.version",
//                        modThreadId = "16680",
//                        modVersion = VersionCheckerInfo.Version("1", "3", "0")
//                    ),
//                    modsFolderInfo = Mod.ModsFolderInfo(folder = Path.of("""C:\Program Files (x86)\Fractal Softworks\Starsector - Playground\mods\UnofficialNewGamePlus""")),
//                    stagingInfo = null,
//                    archiveInfo = ModVariant.ArchiveInfo(folder = Path.of("""C:\Users\whitm\SMOL\archives\ungp-1.3.0.7z"""))
//                )
//            )
//        ),
//        Mod(
//            id = "lw_lazylib",
//            isEnabledInGame = false,
//            variants = listOf(
//                ModVariant(
//                    modInfo = ModInfo.v095(
//                        _id = "lw_lazylib",
//                        _name = "LazyLib",
//                        _author = "LazyWizard",
//                        _utilityString = "true",
//                        versionString = Version.parse("2.6"),
//                        _description = "",
//                        _gameVersion = "",
//                        _jars = emptyList(),
//                        _modPlugin = "",
//                        _dependencies = emptyList()
//                    ),
//                    versionCheckerInfo = VersionCheckerInfo(
//                        masterVersionFile = "",
//                        modThreadId = "0",
//                        modVersion = VersionCheckerInfo.Version("2", "6", "0")
//                    ),
//                    modsFolderInfo = Mod.ModsFolderInfo(folder = Path.of("""C:\Program Files (x86)\Fractal Softworks\Starsector - Playground\mods\UnofficialNewGamePlus""")),
//                    stagingInfo = null,
//                    archiveInfo = ModVariant.ArchiveInfo(Path.of("""C:\Users\whitm\SMOL\archives\ungp-1.3.0.7z"""))
//                )
//            )
//        )
//    )
//        .onEach { mod -> mod.variants.onEach { it.mod = mod } }
//}