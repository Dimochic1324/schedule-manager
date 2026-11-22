package com.prilozhuha.app

import android.app.Application
import com.prilozhuha.app.alarm.Notifications
import com.prilozhuha.app.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifications.createChannels(this)
    }
}
