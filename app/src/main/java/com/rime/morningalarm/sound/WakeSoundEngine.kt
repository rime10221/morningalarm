package com.rime.morningalarm.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 실시간 합성 알람 엔진. 녹음된 음원 대신 매번 새로 합성하기 때문에
 * 매일 음높이·리듬·질감이 조금씩 달라져 "익숙해져서 못 듣는" 습관화를 막는다.
 *
 * 설계 근거 (자세한 내용은 README):
 *  1. 520Hz 사각파 T3 패턴 — 수면 중 각성 역치가 가장 낮았던 신호
 *     (Bruck, Ball, Thomas & Rouillard, J Sleep Res 2009)
 *  2. 30–150Hz 진폭변조 "거칠기(roughness)" — 비명/경보음 고유의 음향 특성,
 *     편도체를 자극하고 회피 반응을 유발 (Arnal et al., Curr Biol 2015; Nat Commun 2019)
 *  3. 예측 불가능한 패턴 + 침묵 뒤 갑작스런 시작 — 습관화 방지, 탈습관화 유도
 *     (Rankin et al., Neurobiol Learn Mem 2009)
 *  4. 시간이 지날수록 강도가 단계적으로 상승
 */
class WakeSoundEngine(seed: Long) {

    private val rnd = Random(seed)

    // 오늘의 음색: 520Hz 근처 ±8% (저주파 대역 유지), 거칠기 50–90Hz
    private val baseFreq = 520.0 * (0.92 + rnd.nextDouble() * 0.16)
    private val roughHz = 50.0 + rnd.nextDouble() * 40.0

    @Volatile private var running = false
    private var thread: Thread? = null

    /** TTS 음성이 나올 때 배경 소리를 낮추는 계수 (0..1). */
    @Volatile var duck = 1f

    /** 경과 시간(초). 서비스가 단계 판단에 사용. */
    @Volatile var elapsedSeconds = 0.0
        private set

    fun startAlarm(startAtSeconds: Double = 0.0) = start(chime = false, startAtSeconds)

    /** "깨어 있나요?" 확인용 부드러운 차임. */
    fun startChime() = start(chime = true, 0.0)

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun start(chime: Boolean, startAtSeconds: Double) {
        stop()
        running = true
        thread = Thread({ run(chime, startAtSeconds) }, "WakeSoundEngine").apply { start() }
    }

    private fun run(chime: Boolean, startAt: Double) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf, SR / 5 * 2))
            .build()

        val synth: Synth = if (chime) ChimeSynth() else AlarmSynth(startAt)
        val buf = ShortArray(1024)
        var duckCur = 1.0
        try {
            track.play()
            while (running) {
                for (i in buf.indices) {
                    duckCur += (duck - duckCur) * 0.0008
                    val s = synth.next() * duckCur
                    buf[i] = (s.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
                }
                elapsedSeconds = synth.seconds
                track.write(buf, 0, buf.size)
            }
        } finally {
            try { track.stop() } catch (_: IllegalStateException) {}
            track.release()
        }
    }

    /** 오프라인 렌더링 (테스트·미리듣기 용). AudioTrack 없이 샘플만 만든다. */
    fun render(seconds: Double, startAt: Double = 0.0, chime: Boolean = false): ShortArray {
        val synth: Synth = if (chime) ChimeSynth() else AlarmSynth(startAt)
        return ShortArray((seconds * SR).toInt()) {
            (synth.next().coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
    }

    private interface Synth {
        val seconds: Double
        fun next(): Double
    }

    // ------------------------------------------------------------------ alarm

    private enum class Kind { T3, SWEEP, STUTTER, CLUSTER, GAP }

    private class Segment(
        val kind: Kind,
        val length: Int,
        val f: DoubleArray,
        val rough: Double,
        val amHz: Double,
        val rate: Double = 0.0,
    )

    private inner class AlarmSynth(startAt: Double) : Synth {
        private var n = (startAt * SR).toLong()
        override val seconds get() = n.toDouble() / SR

        private var lastKind = Kind.GAP
        private var seg = nextSegment()
        private var pos = 0
        private val ph = DoubleArray(3)
        private var amPh = 0.0

        override fun next(): Double {
            if (pos >= seg.length) {
                lastKind = seg.kind
                seg = nextSegment()
                pos = 0
            }
            val t = pos.toDouble() / SR
            val raw = when (seg.kind) {
                Kind.GAP -> 0.0
                Kind.T3 -> t3(t)
                Kind.SWEEP -> sweep(t)
                Kind.STUTTER -> stutter(t)
                Kind.CLUSTER -> cluster(t)
            }
            // 거칠기: 진폭을 30–150Hz 로 빠르게 흔든다
            amPh = (amPh + seg.amHz / SR) % 1.0
            val am = 1.0 - seg.rough * (0.5 - 0.5 * cos(2 * PI * amPh))
            val x = raw * am

            // 처음 6초 동안 50% → 100% 로 상승 후 최대 유지
            val e = seconds
            val ramp = 0.5 + 0.5 * min(1.0, e / 6.0)
            pos++
            n++
            // 소프트 클리핑: 체감 음량(RMS)을 최대한 끌어올림
            return tanh(2.2 * x) / tanh(2.2) * ramp
        }

        /** 0.5s 켜짐 / 0.5s 꺼짐 ×3 후 1.5s 정지 (ISO 8201 Temporal-3). */
        private fun t3(t: Double): Double {
            val k = (t / 1.0).toInt()
            val inPulse = t - k
            if (k > 2 || inPulse >= 0.5) return 0.0
            val f = seg.f[k % seg.f.size]
            return square(0, f) * gate(inPulse, 0.5)
        }

        /** 사이렌처럼 위로 쓸어 올리는 소리 반복. */
        private fun sweep(t: Double): Double {
            val period = seg.rate
            val local = t % period
            val sweepLen = period - 0.06
            if (local >= sweepLen) return 0.0
            val p = local / sweepLen
            val f = seg.f[0] * (seg.f[1] / seg.f[0]).pow(p)
            return square(0, f) * gate(local, sweepLen)
        }

        /** 8–14Hz 로 끊어지는 빠른 연타음. */
        private fun stutter(t: Double): Double {
            val period = 1.0 / seg.rate
            val local = t % period
            if (local >= period * 0.5) return 0.0
            return square(0, seg.f[0]) * gate(local, period * 0.5)
        }

        /** 반음 차이 두 음 + 5도 음: 맥놀이로 불협화·거칠기 극대화. */
        private fun cluster(t: Double): Double {
            val total = seg.length.toDouble() / SR
            val g = gate(t, total)
            return (square(0, seg.f[0]) + square(1, seg.f[1]) + 0.6 * square(2, seg.f[2])) / 2.6 * g
        }

        /** PolyBLEP 사각파 (앨리어싱 억제). 홀수배음이 넓게 퍼져 다양한 청력 대역을 자극. */
        private fun square(voice: Int, f: Double): Double {
            val dt = f / SR
            var p = ph[voice] + dt
            if (p >= 1.0) p -= 1.0
            ph[voice] = p
            var v = if (p < 0.5) 1.0 else -1.0
            v += polyBlep(p, dt)
            v -= polyBlep((p + 0.5) % 1.0, dt)
            return v
        }

        /** 3ms 어택/릴리즈 — 클릭 노이즈 없이 날카로운 시작은 유지. */
        private fun gate(t: Double, len: Double): Double {
            val a = 0.003
            return when {
                t < a -> t / a
                t > len - a -> max(0.0, (len - t) / a)
                else -> 1.0
            }
        }

        private fun len(sec: Double) = (sec * SR).toInt()

        private fun nextSegment(): Segment {
            val e = seconds
            // 단계 0 (0–20초): 순수 520Hz T3
            if (e < 20) {
                return Segment(Kind.T3, len(4.0), doubleArrayOf(baseFreq), 0.0, roughHz)
            }
            // 단계 1 (20–45초): 저/고 교차 T3 + 거칠기
            if (e < 45) {
                return if (rnd.nextDouble() < 0.8) {
                    Segment(
                        Kind.T3, len(4.0),
                        doubleArrayOf(baseFreq, baseFreq * 1.5, baseFreq),
                        0.7, roughHz,
                    )
                } else {
                    Segment(Kind.STUTTER, len(1.5), doubleArrayOf(baseFreq * 1.26), 0.5, roughHz, rate = 10.0)
                }
            }
            // 단계 2 (45초~): 예측 불가능한 카오스. 직전에 소리가 났으면 짧은 침묵을 끼워
            // 다음 소리가 "갑자기" 터지게 만든다 (놀람/정위 반응).
            if (lastKind != Kind.GAP && rnd.nextDouble() < 0.4) {
                return Segment(Kind.GAP, len(0.25 + rnd.nextDouble() * 0.6), doubleArrayOf(0.0), 0.0, roughHz)
            }
            val f = baseFreq * (0.8 + rnd.nextDouble() * 0.9) // 416–884Hz 근처
            val am = 35.0 + rnd.nextDouble() * 80.0
            return when (rnd.nextInt(4)) {
                0 -> Segment(
                    Kind.T3, len(4.0),
                    doubleArrayOf(f, f * 1.5, f * 0.75), 0.85, am,
                )
                1 -> Segment(
                    Kind.SWEEP, len(2.0 + rnd.nextDouble() * 2.0),
                    doubleArrayOf(f * 0.8, f * 2.4), 0.4, am,
                    rate = 0.25 + rnd.nextDouble() * 0.6,
                )
                2 -> Segment(
                    Kind.STUTTER, len(1.0 + rnd.nextDouble() * 1.5),
                    doubleArrayOf(f * 1.2), 0.5, am,
                    rate = 8.0 + rnd.nextDouble() * 6.0,
                )
                else -> Segment(
                    Kind.CLUSTER, len(1.2 + rnd.nextDouble() * 1.5),
                    doubleArrayOf(f, f * 1.0595, f * 1.5), 0.9, am,
                )
            }
        }
    }

    // ------------------------------------------------------------------ chime

    private class ChimeSynth : Synth {
        private var n = 0L
        override val seconds get() = n.toDouble() / SR

        override fun next(): Double {
            val t = (n % (SR * 5 / 2)).toDouble() / SR // 2.5초마다
            n++
            val env = exp(-t / 0.35)
            return 0.35 * env * (sin(2 * PI * 1046.5 * t) + 0.5 * sin(2 * PI * 1568.0 * t)) / 1.5
        }
    }

    private companion object {
        const val SR = 44100

        fun polyBlep(t: Double, dt: Double): Double = when {
            t < dt -> {
                val x = t / dt
                x + x - x * x - 1.0
            }
            t > 1.0 - dt -> {
                val x = (t - 1.0) / dt
                x * x + x + x + 1.0
            }
            else -> 0.0
        }
    }
}
