package com.rime.morningalarm.ui

import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rime.morningalarm.alarm.AlarmState

/**
 * 잠금화면 위로 뜨는 알람 화면. 뒤로가기·볼륨 키가 먹히지 않고,
 * 벗어나면 서비스가 다시 끌어온다.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 최대 밝기: 밝은 빛은 멜라토닌을 억제하고 각성도를 올린다
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit // 뒤로가기로 도망 불가
        })

        setContent { MorningTheme { RingRoot(onFinish = { finishAndRemoveTask() }) } }
    }

    override fun onResume() {
        super.onResume()
        AlarmState.ringVisible = true
    }

    override fun onPause() {
        AlarmState.ringVisible = false
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        // 볼륨 키로 소리 줄이기 불가
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyDown(keyCode, event)
    }
}
