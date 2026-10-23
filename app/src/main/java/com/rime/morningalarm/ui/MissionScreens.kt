package com.rime.morningalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rime.morningalarm.data.AlarmStore
import com.rime.morningalarm.data.WakeSettings
import com.rime.morningalarm.mission.Captcha
import com.rime.morningalarm.mission.CaptchaShape
import com.rime.morningalarm.mission.CaptchaTile
import com.rime.morningalarm.mission.MathMission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MISSION_IDLE_MS = 30_000L

/**
 * 끄기 미션. 설정에 따라 암산 / 패턴 / 캡챠 중 하나.
 * 30초 동안 손을 놓으면 = 다시 잠들고 있다 → [onIdle] 로 처음부터.
 */
@Composable
fun MissionScreen(onSolved: () -> Unit, onIdle: () -> Unit) {
    val settings = remember { AlarmStore.settings.value }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInput) {
        delay(MISSION_IDLE_MS)
        onIdle()
    }
    val touch = { lastInput = System.currentTimeMillis() }

    Column(
        Modifier
            .fillMaxSize()
            .background(IOS.LightBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IdleBar(lastInput)
        when (settings.effectiveMission) {
            1 -> PatternMission(settings, touch, onSolved)
            2 -> CaptchaMission(settings, touch, onSolved)
            else -> MathMissionContent(settings, touch, onSolved)
        }
    }
}

@Composable
private fun IdleBar(lastInput: Long) {
    val now = rememberNow(100)
    val left = (1f - (now - lastInput).toFloat() / MISSION_IDLE_MS).coerceIn(0f, 1f)
    val color by animateColorAsState(if (left < 0.3f) IOS.LightRed else IOS.LightOrange, label = "idle")
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(IOS.LightKey),
    ) {
        Box(Modifier.fillMaxWidth(left).height(4.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun Progress(title: String, solved: Int, total: Int) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = ios(17, FontWeight.SemiBold, IOS.LightSecondary))
    if (total > 1) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i < solved) IOS.LightOrange else IOS.LightKey),
                )
            }
        }
    }
}

/** 틀렸을 때 좌우로 흔들기. */
private suspend fun Animatable<Float, AnimationVector1D>.shake() {
    for (dx in listOf(18f, -16f, 12f, -8f, 4f, 0f)) animateTo(dx, tween(45))
}

// ------------------------------------------------------------------ 암산

@Composable
private fun ColumnScope.MathMissionContent(settings: WakeSettings, touch: () -> Unit, onSolved: () -> Unit) {
    val total = settings.missionCount.coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathMission.generate(settings.difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun press(key: String) {
        touch()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        wrong = false
        when (key) {
            "⌫" -> input = input.dropLast(1)
            "OK" -> {
                if (input.isEmpty()) return
                if (input.toIntOrNull() == problem.answer) {
                    solved++
                    if (solved >= total) {
                        onSolved()
                        return
                    }
                } else {
                    wrong = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { shake.shake() }
                }
                input = ""
                problem = MathMission.generate(settings.difficulty)
            }
            else -> if (input.length < 6) input += key
        }
    }

    Progress("문제 ${solved + 1} / $total", solved, total)
    Spacer(Modifier.weight(1f))
    Column(
        Modifier
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(IOS.LightCard)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(problem.text, style = ios(40, FontWeight.SemiBold, IOS.LightLabel, tracking = -0.5f))
        Spacer(Modifier.height(14.dp))
        Text(
            input.ifEmpty { "?" },
            style = ios(
                52, FontWeight.Bold,
                when {
                    wrong -> IOS.LightRed
                    input.isEmpty() -> IOS.LightKeyPressed
                    else -> IOS.LightLabel
                },
            ),
        )
        Text(if (wrong) "틀렸어요. 새 문제입니다." else " ", style = ios(14, color = IOS.LightRed))
    }
    Spacer(Modifier.weight(1f))

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "OK"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { key -> KeypadKey(key) { press(key) } }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun KeypadKey(key: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val base = when (key) {
        "OK" -> IOS.LightOrange
        "⌫" -> Color.Transparent
        else -> IOS.LightKey
    }
    val color = if (pressed) {
        if (key == "OK") IOS.LightOrange.copy(alpha = 0.7f) else IOS.LightKeyPressed
    } else base
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "OK" -> Icon(Icons.Filled.Check, "확인", tint = Color.White, modifier = Modifier.size(34.dp))
            "⌫" -> Text("지움", style = ios(18, FontWeight.Normal, IOS.LightLabel))
            else -> Text(key, style = ios(34, FontWeight.Normal, IOS.LightLabel))
        }
    }
}

// ------------------------------------------------------------------ 패턴

@Composable
private fun ColumnScope.PatternMission(settings: WakeSettings, touch: () -> Unit, onSolved: () -> Unit) {
    val answer = settings.patternDots
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    Progress("등록한 패턴을 그리세요", 0, 1)
    Spacer(Modifier.height(6.dp))
    Text(
        if (error) "패턴이 틀렸어요" else " ",
        style = ios(15, color = IOS.LightRed),
    )
    Spacer(Modifier.weight(1f))
    key(attempt) {
        PatternLock(
            colors = PatternColors(IOS.LightKeyPressed, IOS.LightOrange, IOS.LightRed),
            error = error,
            onStart = {
                touch()
                error = false
            },
            onComplete = { drawn ->
                touch()
                if (drawn == answer) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSolved()
                } else {
                    error = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        shake.shake()
                        delay(500)
                        attempt++
                    }
                }
            },
            modifier = Modifier
                .offset { IntOffset(shake.value.roundToInt(), 0) }
                .padding(horizontal = 40.dp),
        )
    }
    Spacer(Modifier.weight(1.3f))
}

// ------------------------------------------------------------------ 캡챠

@Composable
private fun ColumnScope.CaptchaMission(settings: WakeSettings, touch: () -> Unit, onSolved: () -> Unit) {
    val total = settings.missionCount.coerceIn(1, 5)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var solved by remember { mutableIntStateOf(0) }
    var puzzle by remember { mutableStateOf(Captcha.generate()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    Progress("${solved + 1} / $total", solved, total)
    Spacer(Modifier.weight(1f))

    Column(
        Modifier
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(IOS.LightCard)
            .padding(16.dp),
    ) {
        Text("다음을 모두 선택하세요", style = ios(15, color = IOS.LightSecondary))
        Spacer(Modifier.height(2.dp))
        Text(puzzle.prompt, style = ios(28, FontWeight.Bold, IOS.LightLabel, tracking = -0.4f))
        Text(
            if (wrong) "틀렸어요. 새 문제입니다." else " ",
            style = ios(14, color = IOS.LightRed),
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val i = r * 3 + c
                        CaptchaCell(puzzle.tiles[i], i in picked, Modifier.weight(1f)) {
                            touch()
                            wrong = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            picked = if (i in picked) picked - i else picked + i
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    picked.isEmpty() -> IOS.LightKey
                    pressed -> IOS.LightOrange.copy(alpha = 0.7f)
                    else -> IOS.LightOrange
                },
            )
            .clickable(source, indication = null, enabled = picked.isNotEmpty()) {
                touch()
                if (picked == puzzle.answer) {
                    solved++
                    if (solved >= total) {
                        onSolved()
                        return@clickable
                    }
                    wrong = false
                } else {
                    wrong = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { shake.shake() }
                }
                puzzle = Captcha.generate()
                picked = emptySet()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "확인",
            style = ios(18, FontWeight.SemiBold, if (picked.isEmpty()) IOS.LightSecondary else Color.White),
        )
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun CaptchaCell(tile: CaptchaTile, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = Color(tile.color.argb)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(IOS.LightBg)
            .then(
                if (selected) Modifier.border(3.dp, IOS.LightOrange, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(if (selected) 26.dp else 20.dp)) {
            when (tile.shape) {
                CaptchaShape.CIRCLE -> drawCircle(color)
                CaptchaShape.SQUARE -> drawRoundRect(
                    color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                )
                CaptchaShape.TRIANGLE -> drawPath(
                    Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height * 0.92f)
                        lineTo(0f, size.height * 0.92f)
                        close()
                    },
                    color,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(IOS.LightOrange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
