package smol_app

import kotlinx.coroutines.*

suspend fun main() {
    val scope = CoroutineScope(Job())
//    val scope2 = CoroutineScope(Job())
//    scope.launch(Dispatchers.IO) { println("launch") }.join()
    scope.launch(Dispatchers.Default) { println("launch") }.join()
//    scope2.launch { println("launch") }
}

private suspend fun launchy() {
//    coroutineScope {
//        withContext(Dispatchers.Main) {
            println("launch")
//        }
//    }
}