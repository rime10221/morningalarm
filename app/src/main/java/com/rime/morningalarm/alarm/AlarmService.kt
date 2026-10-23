package com.rime.morningalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rime.morningalarm.R
import com.rime.morningalarm.data.AlarmStore
import com.rime.morningalarm.sound.VoiceCaller
import com.rime.morningalarm.sound.WakeSoundEngine
import com.rime.morningalarm.ui.RingActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 알람을 실제로 울리는 포그라운드 서비스.
 * 소리·진동·음성·플래시를 관리하고, 볼륨을 최대로 고정하며,
 * 사용자가 알람 화면을 벗어나면 계속 다시 끌어온다.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    private var engine: WakeSoundEngine? = null
    private var voice: VoiceCaller? = null
    private var torch: Torch? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var savedAlarmVolume = -1
    private var ttsSpeaking = false
    private var lastVoiceAt = 0L
    private var hiddenTicks = 0
    private var tickCount = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlarmStore.init(this)
        scope.launch { AlarmState.inMission.collect { updateDuck() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> startRinging(intent.getStringExtra(EXTRA_LABEL) ?: "알람", 0.0)
            ACTION_CHECK -> {
                // 울리는 중에 기상 확인이 끼어들면 그게 탈출구가 되므로 무시
                if (AlarmState.session.value?.mode == RingMode.RINGING) return START_REDELIVER_INTENT
                startCheck()
            }
            ACTION_DISMISS -> if (AlarmState.session.value?.mode == RingMode.RINGING) dismiss()
            ACTION_CHECK_OK -> if (AlarmState.session.value?.mode == RingMode.CHECK) finish()
            else -> if (AlarmState.session.value == null) {
                // 시스템이 서비스를 재시작했는데 상태가 없음 → 안전하게 다시 울린다
                startRinging("알람", 20.0)
            }
        }
        if (AlarmState.session.value == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_REDELIVER_INTENT
    }

    // ---------------------------------------------------------------- modes

    private fun startRinging(label: String, startAt: Double) {
        AlarmScheduler.cancelWakeCheck(this)
        goForeground("⏰ $label", "문제를 풀어야 꺼집니다")
        AlarmState.session.value = RingSession(RingMode.RINGING, label)
        acquireWakeLock()
        forceMaxVolume()

        val settings = AlarmStore.settings.value
        val seed = System.currentTimeMillis() / 86_400_000L * 7919L + label.hashCode() + (startAt * 13).toLong()
        engine?.stop()
        engine = WakeSoundEngine(seed).also { it.startAlarm(startAt) }
        updateDuck()

        if (settings.vibrate) vibrate(longArrayOf(0, 900, 250, 250, 250, 250, 400))
        if (settings.voice && voice == null) {
            voice = VoiceCaller(this) { speaking ->
                handler.post {
                    ttsSpeaking = speaking
                    updateDuck()
                }
            }
        }
        lastVoiceAt = System.currentTimeMillis() - 10_000L
        torch = if (settings.torchStrobe) Torch(this) else null

        launchRingScreen()
        restartTicker()
    }

    private fun startCheck() {
        goForeground("깨어 있나요?", "60초 안에 확인하지 않으면 알람이 다시 울립니다")
        AlarmState.session.value = RingSession(
            RingMode.CHECK, "기상 확인",
            checkDeadline = System.currentTimeMillis() + CHECK_WINDOW_MS,
        )
        acquireWakeLock()
        forceMaxVolume()
        engine?.stop()
        engine = WakeSoundEngine(0).also { it.startChime() }
        vibrate(longArrayOf(0, 200, 1800))
        launchRingScreen()
        restartTicker()
    }

    /** 미션 성공. */
    private fun dismiss() {
        val s = AlarmStore.settings.value
        stopEverything()
        if (s.wakeCheck) AlarmScheduler.scheduleWakeCheck(this, s.wakeCheckMinutes)
        stopSelf()
    }

    private fun finish() {
        stopEverything()
        stopSelf()
    }

    // ---------------------------------------------------------------- ticker

    private val tick = object : Runnable {
        override fun run() {
            val session = AlarmState.session.value ?: return
            tickCount++
            val now = System.currentTimeMillis()

            if (tickCount % 2 == 0L) forceMaxVolume()

            if (session.mode == RingMode.RINGING) {
                torch?.toggle() // 500ms 마다 → 1Hz 점멸 (광과민성 발작 위험 대역 3Hz 미만)
                val e = engine?.elapsedSeconds ?: 0.0
                val settings = AlarmStore.settings.value
                if (settings.voice && e >= 20 && !AlarmState.inMission.value &&
                    now - lastVoiceAt > VOICE_INTERVAL_MS
                ) {
                    voice?.call(settings.name)
                    lastVoiceAt = now
                }
            } else if (now > session.checkDeadline) {
                // 확인하지 않음 = 다시 잠듦 → 처음부터가 아니라 최고 강도로 바로 울린다
                startRinging("다시 잠들었나요?", 45.0)
                return
            }

            // 알람 화면에서 도망쳤다면(홈 버튼, 화면 끄기 등) 1.5초 후 다시 끌어온다
            if (!AlarmState.ringVisible) {
                if (++hiddenTicks >= 3) {
                    hiddenTicks = 0
                    launchRingScreen()
                }
            } else {
                hiddenTicks = 0
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun restartTicker() {
        handler.removeCallbacks(tick)
        hiddenTicks = 0
        handler.postDelayed(tick, TICK_MS)
    }

    // ---------------------------------------------------------------- helpers

    private fun updateDuck() {
        engine?.duck = when {
            ttsSpeaking -> 0.18f
            AlarmState.inMission.value -> 0.6f
            else -> 1f
        }
    }

    private fun launchRingScreen() {
        try {
            startActivity(
                Intent(this, RingActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                ),
            )
        } catch (_: Exception) {
            // 백그라운드 실행 제한 → 전체화면 알림이 대신 화면을 띄운다
        }
    }

    private fun goForeground(title: String, text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(title, text), type)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val ring = PendingIntent.getActivity(
            this, 1,
            Intent(this, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(ring)
            .setFullScreenIntent(ring, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MorningAlarm:ring")
            .apply { acquire(3 * 60 * 60 * 1000L) }
    }

    /** 알람 볼륨을 최대로 고정. 사용자가 볼륨을 줄여도 1초 안에 원상복구. */
    private fun forceMaxVolume() {
        val am = getSystemService(AudioManager::class.java)
        try {
            if (savedAlarmVolume < 0) savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (_: SecurityException) {
            // 방해금지 정책상 변경 불가한 기기
        }
    }

    private fun restoreVolume() {
        if (savedAlarmVolume < 0) return
        try {
            getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
        } catch (_: SecurityException) {
        }
        savedAlarmVolume = -1
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator()
        v.cancel()
        val effect = VibrationEffect.createWaveform(pattern, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun stopEverything() {
        handler.removeCallbacks(tick)
        engine?.stop()
        engine = null
        voice?.shutdown()
        voice = null
        ttsSpeaking = false
        vibrator().cancel()
        torch?.set(false)
        torch = null
        restoreVolume()
        AlarmState.inMission.value = false
        AlarmState.session.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        if (AlarmState.session.value != null) stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.rime.morningalarm.RING"
        const val ACTION_CHECK = "com.rime.morningalarm.CHECK"
        const val ACTION_DISMISS = "com.rime.morningalarm.DISMISS"
        const val ACTION_CHECK_OK = "com.rime.morningalarm.CHECK_OK"
        const val EXTRA_LABEL = "label"

        const val CHECK_WINDOW_MS = 60_000L
        private const val VOICE_INTERVAL_MS = 22_000L
        private const val TICK_MS = 500L
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_RING = "ring"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_RING, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "울리는 알람"
                setSound(null, null) // 소리는 엔진이 직접 낸다
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(ch)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
