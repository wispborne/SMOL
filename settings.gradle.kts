pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

}
rootProject.name = "SMOL"
include("App", "SMOL_Access", "VRAM_Checker", "Utilities")
