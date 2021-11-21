package smol_access.util

import kotlinx.coroutines.flow.MutableSharedFlow

class ManualReloadTrigger {
    val trigger = MutableSharedFlow<String>()
}