package com.example.metrofirhook

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface MainActivityGraph {

    fun inject(activity: MainActivity)

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides activity: MainActivity): MainActivityGraph
    }
}