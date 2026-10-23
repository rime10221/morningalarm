package com.rime.morningalarm.mission

import kotlin.random.Random

/**
 * 암산 미션. 계산 같은 인지 과제는 전전두엽을 강제로 가동시켜 수면 관성
 * (깨어난 직후의 멍함, 다시 잠드는 경향)을 끊어낸다 (Tassi & Muzet, Sleep Med Rev 2000).
 */
data class MathProblem(val text: String, val answer: Int)

object MathMission {
    fun generate(difficulty: Int, rnd: Random = Random.Default): MathProblem = when (difficulty) {
        0 -> {
            val a = rnd.nextInt(12, 60)
            val b = rnd.nextInt(12, 40)
            MathProblem("$a + $b", a + b)
        }
        1 -> {
            val a = rnd.nextInt(12, 50)
            val b = rnd.nextInt(3, 10)
            val c = rnd.nextInt(10, 100)
            // 키패드에 음수 입력이 없으므로 결과가 양수일 때만 뺄셈
            if (rnd.nextBoolean() || a * b <= c) MathProblem("$a × $b + $c", a * b + c)
            else MathProblem("$a × $b − $c", a * b - c)
        }
        else -> {
            val a = rnd.nextInt(13, 50)
            val b = rnd.nextInt(13, 30)
            val c = rnd.nextInt(10, 100)
            MathProblem("$a × $b + $c", a * b + c)
        }
    }
}
