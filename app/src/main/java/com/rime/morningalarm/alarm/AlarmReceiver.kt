package com.rime.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rime.morningalarm.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmStore.init(context)
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getIntExtra(EXTRA_ALARM_ID, AlarmScheduler.TEST_ALARM_ID)
                val alarm = AlarmStore.get(id)
                if (alarm != null) {
                    if (alarm.days == 0) AlarmStore.upsert(alarm.copy(enabled = false))
                    else AlarmScheduler.schedule(context, alarm)
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_RING)
                        .putExtra(EXTRA_ALARM_ID, id)
                        .putExtra(AlarmService.EXTRA_LABEL, alarm?.label?.ifBlank { "알람" } ?: "테스트 알람"),
                )
            }
            ACTION_WAKE_CHECK -> ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_CHECK),
            )
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rime.morningalarm.FIRE"
        const val ACTION_WAKE_CHECK = "com.rime.morningalarm.WAKE_CHECK"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
