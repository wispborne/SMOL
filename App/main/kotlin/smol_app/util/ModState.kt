package smol_app.util

import smol_access.model.Mod

enum class ModState {
    Enabled,
    Disabled,
    Uninstalled
}


val Mod.uiEnabled: Boolean
    get() = this.findFirstEnabled != null


val Mod.state: ModState
    get() = when {
        this.findFirstEnabled != null -> ModState.Enabled
        this.findFirstEnabled == null -> ModState.Disabled
        else -> ModState.Uninstalled
    }
