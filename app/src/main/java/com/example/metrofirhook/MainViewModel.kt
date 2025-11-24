package com.example.metrofirhook

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

@Inject
internal class MainViewModel(
    coroutineScope: CoroutineScope,
    configs: Set<Config<*>>,
) {
    val greeting = configs.joinToString("\n") { it.key + ": " + it.value }

    init {
        coroutineScope.launch {
            log("Hello from MainViewModel!")
        }
    }

    private fun log(msg: String) {
        Logger.getLogger("MainActivity").log(Level.INFO, msg)
    }
}