package com.rime.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rime.morningalarm.data.AlarmStore

/** 재부팅·시간 변경·앱 업데이트 시 AlarmManager 예약이 사라지므로 전부 다시 건다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmStore.init(context)
        AlarmScheduler.rescheduleAll(context)
    }
}
