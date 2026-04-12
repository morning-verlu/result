package cn.verlu.cloud.di

import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private var started = false

fun ensureKoinStarted() {
    if (started) return
    startKoin {
        modules(
            module {
                singleOf(::AppGraph)
            },
        )
    }
    started = true
}
