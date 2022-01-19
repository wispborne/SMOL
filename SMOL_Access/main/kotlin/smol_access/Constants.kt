package smol_access

import java.nio.file.Path
import kotlin.io.path.Path

object Constants {
    const val APP_NAME = "SMOL"
    const val APP_NAME_LONG = "Starsector Mod Organizer and Launcher"

    const val MOD_INFO_FILE = "mod_info.json"
    const val ENABLED_MODS_FILENAME = "enabled_mods.json"
    const val VERSION_CHECKER_CSV_PATH = "data/config/version/version_files.csv"
    const val VERSION_CHECKER_FILE_ENDING = ".version"
    const val FORUM_URL = "https://fractalsoftworks.com/forum/index.php"
    const val FORUM_MOD_PAGE_URL = "$FORUM_URL?topic="
    const val FORUM_MOD_INDEX_URL = FORUM_MOD_PAGE_URL + "177"
    const val FORUM_MODDING_SUBFORUM_URL = "$FORUM_URL?board=3.0"
    const val FORUM_HOSTNAME = "fractalsoftworks.com"

    const val APP_FOLDER_NAME = "SMOL-data"
    val APP_FOLDER_DEFAULT: Path = Path("", APP_FOLDER_NAME)
    val ARCHIVES_FOLDER_DEFAULT: Path = APP_FOLDER_DEFAULT.resolve("archives")
    val STAGING_FOLDER_DEFAULT: Path = APP_FOLDER_DEFAULT.resolve("staging")
    val UI_CONFIG_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_UIConfig.json")
    val APP_CONFIG_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_AppConfig.json")
    val THEME_CONFIG_PATH: Path = Path.of("SMOL_Themes.json")
    val VERCHECK_CACHE_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_VerCheckCache.json")
    val VRAM_CHECKER_RESULTS_PATH: Path = APP_FOLDER_DEFAULT.resolve("SMOL_VRAMCheckResults.json")

    // Mod Repo
    const val modRepoUrl = "https://raw.githubusercontent.com/davidwhitman/StarsectorModRepo/main/ModRepo.json"

    // Updater
    private const val baseUpdateUrl = "https://raw.githubusercontent.com/davidwhitman/SMOL_Dist"
    const val UPDATE_URL_UNSTABLE = "$baseUpdateUrl/unstable/"
    const val UPDATE_URL_STABLE = "$baseUpdateUrl/main/"

    val TEMP_DIR = System.getProperty("java.io.tmpdir")?.let { Path.of(it) } ?: APP_FOLDER_DEFAULT

    const val TAG_TRACE = "trace"
}