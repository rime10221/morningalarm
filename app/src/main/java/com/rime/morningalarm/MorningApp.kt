package com.rime.morningalarm

import android.app.Application
import com.rime.morningalarm.alarm.AlarmService
import com.rime.morningalarm.data.AlarmStore

class MorningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmStore.init(this)
        AlarmService.createChannels(this)
    }
}
