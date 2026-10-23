package com.rime.morningalarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

data class PatternColors(val dot: Color, val active: Color, val error: Color)

/**
 * 3x3 잠금 패턴. 점 인덱스는 0..8 (왼쪽 위부터 행 순서).
 * 그리다가 건너뛴 가운데 점은 안드로이드 잠금화면처럼 자동으로 포함한다.
 *
 * @param error true 면 그려진 선을 빨간색으로 보여준다 (틀렸을 때).
 * @param onComplete 손을 뗐을 때 그려진 점 목록. 선을 지우려면 호출자가 key() 로 다시 구성한다.
 */
@Composable
fun PatternLock(
    colors: PatternColors,
    error: Boolean,
    onStart: () -> Unit,
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val path = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                val cell = size.width / 3f
                fun center(i: Int) = Offset(cell * (i % 3 + 0.5f), cell * (i / 3 + 0.5f))
                fun hit(p: Offset): Int? = (0..8).firstOrNull {
                    val c = center(it)
                    hypot(p.x - c.x, p.y - c.y) < cell * 0.32f
                }
                fun add(i: Int) {
                    if (i in path) return
                    path.lastOrNull()?.let { last ->
                        // 1→3 처럼 가운데를 건너뛰면 가운데 점(2)도 포함
                        val (r1, c1) = last / 3 to last % 3
                        val (r2, c2) = i / 3 to i % 3
                        if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                            val mid = (r1 + r2) / 2 * 3 + (c1 + c2) / 2
                            if (mid !in path) path.add(mid)
                        }
                    }
                    path.add(i)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                detectDragGestures(
                    onDragStart = { p ->
                        path.clear()
                        onStart()
                        finger = p
                        hit(p)?.let(::add)
                    },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)?.let(::add)
                    },
                    onDragEnd = {
                        finger = null
                        val result = path.toList()
                        if (result.isNotEmpty()) onComplete(result)
                    },
                    onDragCancel = { finger = null },
                )
            },
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3 + 0.5f), cell * (i / 3 + 0.5f))
        val lineColor = if (error) colors.error else colors.active
        val stroke = 6.dp.toPx()

        for (k in 1 until path.size) {
            drawLine(lineColor, center(path[k - 1]), center(path[k]), stroke, StrokeCap.Round)
        }
        val f = finger
        if (f != null && path.isNotEmpty()) {
            drawLine(lineColor.copy(alpha = 0.6f), center(path.last()), f, stroke, StrokeCap.Round)
        }
        for (i in 0..8) {
            val on = i in path
            val c = center(i)
            if (on) drawCircle(lineColor.copy(alpha = 0.22f), cell * 0.26f, c)
            drawCircle(if (on) lineColor else colors.dot, if (on) 11.dp.toPx() else 8.dp.toPx(), c)
        }
    }
}
