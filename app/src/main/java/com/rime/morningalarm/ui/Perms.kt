package com.rime.morningalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.rime.morningalarm.alarm.AlarmScheduler

/** 알람이 "반드시" 울리고 화면을 장악하기 위해 필요한 권한들. */
data class PermStatus(
    val notifications: Boolean,
    val fullScreen: Boolean,
    val exactAlarm: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
) {
    val criticalOk get() = notifications && fullScreen && exactAlarm
    val allOk get() = criticalOk && overlay && battery
}

object Perms {
    fun status(ctx: Context) = PermStatus(
        notifications = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fullScreen = Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        exactAlarm = AlarmScheduler.canScheduleExact(ctx),
        overlay = Settings.canDrawOverlays(ctx),
        battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
    )

    private fun pkg(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    fun notificationSettings(ctx: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun fullScreenSettings(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(ctx))
        else notificationSettings(ctx)

    fun exactAlarmSettings(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(ctx))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx))

    fun overlaySettings(ctx: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg(ctx))

    @android.annotation.SuppressLint("BatteryLife")
    fun batterySettings(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(ctx))
}
