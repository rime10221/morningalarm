package com.rime.morningalarm.sound

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Calendar
import java.util.Locale

/**
 * TTS 로 이름을 부른다. 자기 이름은 수면 중에도 다른 이름보다 큰 뇌 반응(P3)을 일으키고
 * (Perrin et al., Clin Neurophysiol 1999), 낯선 목소리가 익숙한 목소리보다 더 큰 각성 반응을
 * 유발한다 (Blume et al., NeuroImage 2018). TTS 는 정확히 "낯선 목소리"다.
 */
class VoiceCaller(context: Context, private val onSpeaking: (Boolean) -> Unit) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) setup()
    }

    private fun setup() {
        tts.language = Locale.KOREAN
        tts.setSpeechRate(1.05f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onSpeaking(true)
            override fun onDone(utteranceId: String?) = onSpeaking(false)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onSpeaking(false)
        })
    }

    fun call(name: String) {
        if (!ready) return
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        val time = if (m == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${m}분"
        val who = name.trim()
        val lines = if (who.isNotEmpty()) {
            listOf(
                "$who! 일어나! 지금 ${time}이야!",
                "$who! $who! 일어나야 돼! 벌써 ${time}이야!",
                "$who, 눈 떠! 지금 일어나!",
            )
        } else {
            listOf(
                "일어나세요! 지금 ${time}입니다!",
                "기상! 기상! 지금 ${time}입니다!",
                "지금 당장 일어나세요!",
            )
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        tts.speak(lines.random(), TextToSpeech.QUEUE_FLUSH, params, "wake")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        onSpeaking(false)
    }
}
