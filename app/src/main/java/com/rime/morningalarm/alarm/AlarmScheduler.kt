package com.rime.morningalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rime.morningalarm.data.Alarm
import com.rime.morningalarm.data.AlarmStore
import com.rime.morningalarm.ui.MainActivity
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val WAKE_CHECK_REQUEST = 900_001
    private const val SNOOZE_REQUEST = 900_002
    const val TEST_ALARM_ID = -1

    /** 다음 울릴 시각(epoch ms). */
    fun nextTrigger(alarm: Alarm, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) { i ->
            if (i > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
            if (cal.timeInMillis > from) {
                val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = 일요일
                if (alarm.days == 0 || alarm.isOn(dow)) return cal.timeInMillis
            }
        }
        return cal.timeInMillis
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        setAlarmClock(context, nextTrigger(alarm), firePendingIntent(context, alarm.id))
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId))
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.alarms.value.forEach { schedule(context, it) }
    }

    /** 알람을 끈 뒤 N분 후 "아직 깨어 있나요?" 확인. */
    fun scheduleWakeCheck(context: Context, minutes: Int) {
        setAlarmClock(context, System.currentTimeMillis() + minutes * 60_000L, wakeCheckPendingIntent(context))
    }

    fun cancelWakeCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(wakeCheckPendingIntent(context))
    }

    /** 테스트용: 지금부터 N초 후 울림. */
    fun scheduleTest(context: Context, seconds: Int) {
        setAlarmClock(context, System.currentTimeMillis() + seconds * 1000L, firePendingIntent(context, TEST_ALARM_ID))
    }

    /** 다시 울림: N분 후 같은 레이블로 다시 울린다. 울릴 시각을 반환. */
    fun scheduleSnooze(context: Context, minutes: Int, label: String, used: Int): Long {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_SNOOZE_FIRE)
                .putExtra(AlarmService.EXTRA_LABEL, label)
                .putExtra(AlarmService.EXTRA_SNOOZE_USED, used),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarmClock(context, at, pi)
        return at
    }

    private fun wakeCheckPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, WAKE_CHECK_REQUEST,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun firePendingIntent(context: Context, alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, alarmId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * setAlarmClock: Doze 상태에서도 정확히 울리고, 상태바에 알람 아이콘이 뜨며,
     * 발생 시 포그라운드 서비스 시작이 허용된다.
     */
    private fun setAlarmClock(context: Context, at: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } catch (e: SecurityException) {
            Log.e(TAG, "exact alarm not permitted, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }
}
