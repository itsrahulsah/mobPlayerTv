package com.mobplayer.tv

import android.app.Application
import com.mobplayer.tv.youtube.SmartTubePlayerEngine
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MobPlayerTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SmartTubePlayerEngine.init(this)
    }
}
