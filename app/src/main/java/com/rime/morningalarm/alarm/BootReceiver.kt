package com.rime.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.rime.morningalarm.data.AlarmStore

/** 재부팅·시간 변경·앱 업데이트 시 AlarmManager 예약이 사라지므로 전부 다시 건다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmStore.init(context)
        // 알람이 울리는 도중 꺼졌다면 최대로 올려둔 볼륨을 원래대로
        val saved = AlarmStore.savedVolume
        val booted = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (booted && saved >= 0) {
            try {
                context.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
            } catch (_: SecurityException) {
            }
            AlarmStore.savedVolume = -1
        }
        AlarmScheduler.rescheduleAll(context)
    }
}
