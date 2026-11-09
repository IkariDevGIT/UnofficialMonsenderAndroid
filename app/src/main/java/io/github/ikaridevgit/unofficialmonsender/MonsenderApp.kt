package io.github.ikaridevgit.unofficialmonsender

import android.app.Application
import android.content.Context

class MonsenderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MonsenderApp).container
