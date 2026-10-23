package com.rime.morningalarm

import com.rime.morningalarm.data.Alarm
import com.rime.morningalarm.alarm.AlarmScheduler
import com.rime.morningalarm.mission.MathMission
import com.rime.morningalarm.sound.WakeSoundEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class WakeSoundEngineTest {

    private fun rms(s: ShortArray, from: Int, to: Int): Double {
        var acc = 0.0
        for (i in from until to) acc += (s[i] / 32768.0).let { it * it }
        return sqrt(acc / (to - from))
    }

    @Test
    fun alarmRendersLoudWithoutDeadAir() {
        val pcm = WakeSoundEngine(seed = 1).render(90.0)
        val sr = 44100
        // 4초 창마다 소리가 반드시 있어야 하고 (T3 주기 = 4초), 충분히 커야 한다
        for (w in 0 until 22) {
            val r = rms(pcm, w * 4 * sr, (w + 1) * 4 * sr)
            assertTrue("window $w rms=$r", r > 0.2)
        }
        // 6초 이후 피크는 거의 풀스케일
        val peak = (6 * sr until pcm.size).maxOf { abs(pcm[it].toInt()) }
        assertTrue("peak=$peak", peak > 30000)
        writeWav(File("build/preview_alarm.wav"), pcm)
    }

    @Test
    fun soundDiffersEachDay() {
        val a = WakeSoundEngine(seed = 100).render(3.0)
        val b = WakeSoundEngine(seed = 101).render(3.0)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun chimeIsGentle() {
        val pcm = WakeSoundEngine(0).render(5.0, chime = true)
        assertTrue(pcm.maxOf { abs(it.toInt()) } < 16000)
    }

    @Test
    fun nextTriggerRespectsDays() {
        // 2026-10-24 토요일 08:00 기준
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 24, 8, 0, 0); set(Calendar.MILLISECOND, 0) }
        val weekday7 = Alarm(1, 7, 0, Alarm.WEEKDAYS)
        val next = Calendar.getInstance().apply { timeInMillis = AlarmScheduler.nextTrigger(weekday7, now.timeInMillis) }
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(26, next.get(Calendar.DAY_OF_MONTH))

        val once9 = Alarm(2, 9, 30)
        val n2 = Calendar.getInstance().apply { timeInMillis = AlarmScheduler.nextTrigger(once9, now.timeInMillis) }
        assertEquals(24, n2.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, n2.get(Calendar.HOUR_OF_DAY))

        val once7 = Alarm(3, 7, 0)
        val n3 = Calendar.getInstance().apply { timeInMillis = AlarmScheduler.nextTrigger(once7, now.timeInMillis) }
        assertEquals(25, n3.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun mathAnswersArePositiveAndCorrect() {
        val rnd = Random(7)
        repeat(3000) {
            for (d in 0..2) {
                val p = MathMission.generate(d, rnd)
                assertTrue(p.answer > 0)
                val expr = p.text.replace("×", "*").replace("−", "-").split(" ")
                var v = expr[0].toInt()
                var i = 1
                // 곱셈 우선
                val terms = mutableListOf<Int>()
                val ops = mutableListOf<String>()
                while (i < expr.size) {
                    val op = expr[i]; val n = expr[i + 1].toInt()
                    if (op == "*") v *= n else { terms += v; ops += op; v = n }
                    i += 2
                }
                terms += v
                var res = terms[0]
                ops.forEachIndexed { k, op -> res = if (op == "+") res + terms[k + 1] else res - terms[k + 1] }
                assertEquals(p.text, res, p.answer)
            }
        }
        assertNotEquals(MathMission.generate(1, Random(1)), MathMission.generate(1, Random(2)))
    }

    private fun writeWav(f: File, pcm: ShortArray, sr: Int = 44100) {
        f.parentFile?.mkdirs()
        RandomAccessFile(f, "rw").use { out ->
            out.setLength(0)
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); le32(36 + pcm.size * 2); out.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(sr); le32(sr * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(pcm.size * 2)
            val bytes = ByteArray(pcm.size * 2)
            pcm.forEachIndexed { i, s -> bytes[2 * i] = s.toInt().toByte(); bytes[2 * i + 1] = (s.toInt() shr 8).toByte() }
            out.write(bytes)
        }
    }
}
