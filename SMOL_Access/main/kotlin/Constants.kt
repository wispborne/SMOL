import java.io.File
import kotlin.io.path.Path


const val APP_NAME = "SMOL"
const val APP_NAME_LONG = "Starsector Mod Organizer and Launcher"

const val MOD_INFO_FILE = "mod_info.json"
const val VERSION_CHECKER_CSV_PATH = "data/config/version/version_files.csv"
val VERSION_CHECKER_FILE_PATTERN = Regex(".*\\.version")
const val FORUM_PAGE_URL = "https://fractalsoftworks.com/forum/index.php?topic="

val ARCHIVES_FOLDER_DEFAULT = File(System.getProperty("user.home"), "SMOL/archives")
val STAGING_FOLDER_DEFAULT = File(System.getProperty("user.home"), "SMOL/staging")
val CONFIG_FOLDER_DEFAULT = Path(System.getProperty("user.home"), "SMOL/")
val UICONFIG_PATH = CONFIG_FOLDER_DEFAULT.resolve("SMOL_UIConfig.json")
val APPCONFIG_PATH = CONFIG_FOLDER_DEFAULT.resolve("SMOL_AppConfig.json")
val VERCHECK_CACHE_PATH = CONFIG_FOLDER_DEFAULT.resolve("SMOL_VerCheckCache.json")
val VRAM_CHECKER_RESULTS_PATH = CONFIG_FOLDER_DEFAULT.resolve("SMOL_VRAMCheckResults.json")