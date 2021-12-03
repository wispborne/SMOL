package smol_access

import java.nio.file.Path
import kotlin.io.path.Path

object Constants {
    const val APP_NAME = "SMOL"
    const val APP_NAME_LONG = "Starsector Mod Organizer and Launcher"

    const val MOD_INFO_FILE = "mod_info.json"
    const val VERSION_CHECKER_CSV_PATH = "data/config/version/version_files.csv"
    val VERSION_CHECKER_FILE_PATTERN = Regex(".*\\.version")
    const val FORUM_PAGE_URL = "https://fractalsoftworks.com/forum/index.php?topic="
    const val FORUM_MOD_INDEX_URL = FORUM_PAGE_URL + "177"
    const val FORUM_HOSTNAME = "fractalsoftworks.com"

    const val APP_FOLDER_NAME = "SMOL"
    val APP_FOLDER_DEFAULT: Path = Path(System.getProperty("user.home"), APP_FOLDER_NAME)
    val ARCHIVES_FOLDER_DEFAULT: Path = APP_FOLDER_DEFAULT.resolve("archives")
    val STAGING_FOLDER_DEFAULT: Path = APP_FOLDER_DEFAULT.resolve("staging")
    val UI_CONFIG_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_UIConfig.json")
    val APP_CONFIG_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_AppConfig.json")
    val THEME_CONFIG_PATH: Path = Path.of("SMOL_Themes.json")
    val VERCHECK_CACHE_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_VerCheckCache.json")
    val VRAM_CHECKER_RESULTS_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_VRAMCheckResults.json")

    const val TAG_TRACE = "trace"
}