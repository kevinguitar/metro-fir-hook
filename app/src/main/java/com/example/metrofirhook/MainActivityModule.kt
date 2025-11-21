package com.example.metrofirhook

import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
interface MainActivityModule {

    @Provides
    fun provideCoroutineScope(activity: MainActivity): CoroutineScope {
        return activity.lifecycleScope
    }
}

@ContributesConfig
object StringConfig : Config<String> {
    override val key: String get() = "string"
    override val value: String get() = "Hello Metro!"
}

@ContributesConfig
object IntConfig : Config<Int> {
    override val key: String get() = "int"
    override val value: Int get() = 777
}