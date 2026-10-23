package com.rime.morningalarm.mission

import kotlin.random.Random

/**
 * 캡챠 미션: 3x3 칸 중 "빨간 동그라미" 처럼 색과 모양이 모두 맞는 칸을 전부 고른다.
 * 오답 칸은 일부러 색만 같거나 모양만 같게 섞어서, 반쯤 잠든 채로 대충 누르면 틀리게 만든다.
 */
enum class CaptchaShape(val label: String) { CIRCLE("동그라미"), SQUARE("네모"), TRIANGLE("세모") }

enum class CaptchaColor(val label: String, val argb: Long) {
    RED("빨간", 0xFFFF3B30),
    BLUE("파란", 0xFF007AFF),
    YELLOW("노란", 0xFFFFCC00),
    PURPLE("보라", 0xFFAF52DE),
}

data class CaptchaTile(val shape: CaptchaShape, val color: CaptchaColor)

data class CaptchaPuzzle(val target: CaptchaTile, val tiles: List<CaptchaTile>) {
    val answer: Set<Int> = tiles.indices.filter { tiles[it] == target }.toSet()
    val prompt get() = "${target.color.label} ${target.shape.label}"
}

object Captcha {
    fun generate(rnd: Random = Random.Default): CaptchaPuzzle {
        val target = CaptchaTile(CaptchaShape.entries.random(rnd), CaptchaColor.entries.random(rnd))
        val count = rnd.nextInt(2, 5) // 정답 2~4칸
        val tiles = MutableList(9) { i ->
            if (i < count) target else decoy(target, rnd)
        }
        tiles.shuffle(rnd)
        return CaptchaPuzzle(target, tiles)
    }

    private fun decoy(t: CaptchaTile, rnd: Random): CaptchaTile {
        val otherShape = CaptchaShape.entries.filter { it != t.shape }.random(rnd)
        val otherColor = CaptchaColor.entries.filter { it != t.color }.random(rnd)
        return when (rnd.nextInt(3)) {
            0 -> CaptchaTile(t.shape, otherColor) // 모양만 같음
            1 -> CaptchaTile(otherShape, t.color) // 색만 같음
            else -> CaptchaTile(otherShape, otherColor)
        }
    }
}
