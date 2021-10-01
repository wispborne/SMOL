package util

import kotlinx.coroutines.flow.MutableSharedFlow

class ManualReloadTrigger {
    val trigger = MutableSharedFlow<String>()
}