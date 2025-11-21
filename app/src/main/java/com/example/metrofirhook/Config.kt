package com.example.metrofirhook

interface Config<T : Any> {
    val key: String
    val value: T
}

// @ContributesIntoSet(AppScope::class, binding = binding<Config<*>>())
annotation class ContributesConfig