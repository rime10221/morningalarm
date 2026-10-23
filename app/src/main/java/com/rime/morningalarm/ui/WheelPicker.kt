package com.rime.morningalarm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

val WheelItemHeight = 40.dp
private const val VISIBLE = 5

/**
 * iOS UIDatePicker 같은 원통형 휠. [cyclic] 이면 무한 반복.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    width: Dp,
    cyclic: Boolean = false,
    alignment: TextAlign = TextAlign.Center,
) {
    val repeat = if (cyclic) 2000 else 1
    val count = items.size * repeat
    val start = if (cyclic) items.size * (repeat / 2) + initialIndex else initialIndex
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val haptic = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { WheelItemHeight.toPx() }

    val centered by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelected(it % items.size)
        }
    }

    // 스크롤이 멈추면 가장 가까운 항목을 중앙에 맞춘다
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling && state.firstVisibleItemScrollOffset != 0) {
                state.animateScrollToItem(centered)
            }
        }
    }

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(vertical = WheelItemHeight * (VISIBLE / 2)),
        modifier = Modifier
            .width(width)
            .height(WheelItemHeight * VISIBLE),
    ) {
        items(count) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .graphicsLayer {
                        // 중심으로부터의 거리 → 원통 회전/투명도
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (item != null) {
                            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = item.offset + item.size / 2f
                            val d = (itemCenter - viewportCenter) / itemPx // -2.5..2.5
                            val norm = min(1f, abs(d) / 2.6f)
                            rotationX = -d * 22f
                            alpha = 1f - norm * 0.75f
                            val s = cos(norm * 0.9f)
                            scaleX = 0.85f + 0.15f * s
                            scaleY = 0.85f + 0.15f * s
                        }
                    },
                contentAlignment = when (alignment) {
                    TextAlign.End -> Alignment.CenterEnd
                    TextAlign.Start -> Alignment.CenterStart
                    else -> Alignment.Center
                },
            ) {
                Text(items[index % items.size], style = ios(23), textAlign = alignment)
            }
        }
    }
}
