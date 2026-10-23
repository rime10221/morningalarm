package com.rime.morningalarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rime.morningalarm.alarm.AlarmService
import com.rime.morningalarm.alarm.AlarmState
import com.rime.morningalarm.alarm.RingMode
import com.rime.morningalarm.alarm.RingSession
import com.rime.morningalarm.data.AlarmStore
import com.rime.morningalarm.mission.MathMission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private enum class Stage { ALERT, MISSION, DONE }

private const val MISSION_IDLE_MS = 30_000L

@Composable
fun RingRoot(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val session by AlarmState.session.collectAsState()
    var stage by remember { mutableStateOf(Stage.ALERT) }

    // 기상 확인에 실패해 다시 울리기 시작하면 미션부터 다시
    LaunchedEffect(session?.startedAt) {
        if (session?.mode == RingMode.RINGING && stage != Stage.DONE) stage = Stage.ALERT
    }
    LaunchedEffect(stage) { AlarmState.inMission.value = stage == Stage.MISSION }

    val s = session
    when {
        stage == Stage.DONE -> DoneScreen(onFinish)
        s == null -> LaunchedEffect(Unit) { onFinish() }
        s.mode == RingMode.CHECK -> CheckScreen(s) {
            AlarmService.send(ctx, AlarmService.ACTION_CHECK_OK)
            onFinish()
        }
        else -> AnimatedContent(
            stage,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "stage",
        ) { st ->
            if (st == Stage.MISSION) {
                MissionScreen(
                    onSolved = {
                        AlarmService.send(ctx, AlarmService.ACTION_DISMISS)
                        stage = Stage.DONE
                    },
                    onIdle = { stage = Stage.ALERT },
                )
            } else {
                AlertScreen(s) { stage = Stage.MISSION }
            }
        }
    }
}

@Composable
private fun rememberNow(periodMs: Long = 1000): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

// ------------------------------------------------------------------ alert

@Composable
private fun AlertScreen(session: RingSession, onStart: () -> Unit) {
    val flash = AlarmStore.settings.collectAsState().value.screenFlash
    var bright by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        while (true) {
            bright = !bright
            delay(500) // 1Hz 점멸
        }
    }
    val bg = if (bright) Color.White else Color.Black
    val fg = if (bright) Color.Black else Color.White
    val now = rememberNow()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val elapsed = ((now - session.startedAt) / 1000).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(cal.time),
            style = ios(20, FontWeight.SemiBold, fg.copy(alpha = 0.8f)),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ampm(h), style = ios(28, FontWeight.Light, fg), modifier = Modifier.padding(bottom = 22.dp))
            Spacer(Modifier.width(6.dp))
            Text(clock(h, m), style = ios(104, FontWeight.Thin, fg, tracking = -3f))
        }
        Text(session.label, style = ios(22, FontWeight.SemiBold, fg))
        Spacer(Modifier.height(10.dp))
        Text(
            "울린 지 ${elapsed / 60}분 ${elapsed % 60}초",
            style = ios(15, color = fg.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.weight(1f))
        if (session.snoozeLeft > 0) {
            SnoozeButton(fg, session.snoozeLeft, AlarmStore.settings.collectAsState().value.snoozeMinutes)
            Spacer(Modifier.height(28.dp))
        }
        SlideToStart("밀어서 미션 시작", fg, bg, onStart)
        Spacer(Modifier.height(12.dp))
        Text(
            "끄는 방법은 미션뿐입니다",
            style = ios(13, color = fg.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.height(40.dp))
    }
}

/** iOS 잠금화면 알람의 "다시 알림" 버튼. */
@Composable
private fun SnoozeButton(fg: Color, left: Int, minutes: Int) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(fg.copy(alpha = if (pressed) 0.28f else 0.18f))
            .clickable(source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                AlarmService.send(ctx, AlarmService.ACTION_SNOOZE)
            }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("다시 알림", style = ios(22, FontWeight.SemiBold, IOS.Orange))
        Spacer(Modifier.height(2.dp))
        Text("${minutes}분 후 · ${left}회 남음", style = ios(14, color = fg.copy(alpha = 0.6f)))
    }
}

/** iOS "밀어서 잠금 해제" 슬라이더. */
@Composable
private fun SlideToStart(text: String, fg: Color, bg: Color, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val x = remember { Animatable(0f) }
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sweep by shimmer.animateFloat(
        -0.3f, 1.3f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    BoxWithConstraints(
        Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.14f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val knob = 64.dp
        val maxPx = with(density) { (maxWidth - knob - 8.dp).toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        Text(
            text,
            style = ios(19, FontWeight.Normal).copy(
                brush = Brush.linearGradient(
                    0f to fg.copy(alpha = 0.35f),
                    0.45f to fg.copy(alpha = 0.35f),
                    0.5f to fg,
                    0.55f to fg.copy(alpha = 0.35f),
                    1f to fg.copy(alpha = 0.35f),
                    start = Offset(widthPx * (sweep - 0.5f), 0f),
                    end = Offset(widthPx * (sweep + 0.5f), 0f),
                ),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
        )
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(knob)
                .clip(CircleShape)
                .background(fg)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        scope.launch { x.snapTo((x.value + d).coerceIn(0f, maxPx)) }
                    },
                    onDragStopped = {
                        if (x.value > maxPx * 0.85f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            x.animateTo(maxPx)
                            onDone()
                        } else {
                            x.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = bg, modifier = Modifier.size(30.dp))
        }
    }
}

// ------------------------------------------------------------------ mission

@Composable
private fun MissionScreen(onSolved: () -> Unit, onIdle: () -> Unit) {
    val settings = remember { AlarmStore.settings.value }
    val total = settings.missionCount.coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathMission.generate(settings.difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shake = remember { Animatable(0f) }

    // 30초 동안 손을 놓으면 = 다시 잠들고 있다 → 처음부터
    LaunchedEffect(lastInput) {
        delay(MISSION_IDLE_MS)
        onIdle()
    }
    val now = rememberNow(100)
    val idleLeft = (1f - (now - lastInput).toFloat() / MISSION_IDLE_MS).coerceIn(0f, 1f)

    fun press(key: String) {
        lastInput = System.currentTimeMillis()
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
                    problem = MathMission.generate(settings.difficulty)
                    input = ""
                } else {
                    wrong = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        for (dx in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(dx, tween(45))
                    }
                    input = ""
                    problem = MathMission.generate(settings.difficulty)
                }
            }
            else -> if (input.length < 6) input += key
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IOS.LightBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 남은 시간 바
        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(IOS.LightKey),
        ) {
            val barColor by animateColorAsState(if (idleLeft < 0.3f) IOS.LightRed else IOS.LightOrange, label = "bar")
            Box(
                Modifier
                    .fillMaxWidth(idleLeft)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("문제 ${solved + 1} / $total", style = ios(17, FontWeight.SemiBold, IOS.LightSecondary))
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
                if (input.isEmpty()) "?" else input,
                style = ios(
                    52, FontWeight.Bold,
                    when {
                        wrong -> IOS.LightRed
                        input.isEmpty() -> IOS.LightKeyPressed
                        else -> IOS.LightLabel
                    },
                ),
            )
            Text(
                if (wrong) "틀렸어요. 새 문제입니다." else " ",
                style = ios(14, color = IOS.LightRed),
            )
        }
        Spacer(Modifier.weight(1f))

        // 키패드
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

// ------------------------------------------------------------------ done

@Composable
private fun DoneScreen(onFinish: () -> Unit) {
    val settings = remember { AlarmStore.settings.value }
    LaunchedEffect(Unit) {
        delay(2800)
        onFinish()
    }
    val cal = Calendar.getInstance()
    Column(
        Modifier.fillMaxSize().background(IOS.LightBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(IOS.LightOrange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("좋은 아침이에요", style = ios(32, FontWeight.Bold, IOS.LightLabel, tracking = -0.4f))
        Spacer(Modifier.height(6.dp))
        Text(
            "${ampm(cal.get(Calendar.HOUR_OF_DAY))} ${clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}",
            style = ios(20, color = IOS.LightSecondary),
        )
        if (settings.wakeCheck) {
            Spacer(Modifier.height(28.dp))
            Text(
                "${settings.wakeCheckMinutes}분 후에 깨어 있는지\n한 번 더 확인할게요",
                style = ios(16, color = IOS.LightSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ wake check

@Composable
private fun CheckScreen(session: RingSession, onAwake: () -> Unit) {
    val now = rememberNow(100)
    val total = AlarmService.CHECK_WINDOW_MS.toFloat()
    val left = ((session.checkDeadline - now) / total).coerceIn(0f, 1f)
    val secs = ((session.checkDeadline - now + 999) / 1000).coerceAtLeast(0)
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(IOS.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("깨어 있나요?", style = ios(34, FontWeight.Bold, tracking = -0.4f))
        Spacer(Modifier.height(8.dp))
        Text(
            "시간 안에 누르지 않으면\n최고 강도로 다시 울립니다",
            style = ios(16, color = IOS.Secondary),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                drawArc(
                    IOS.Elevated, 0f, 360f, false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                drawArc(
                    if (left < 0.3f) IOS.Red else IOS.Orange, -90f, 360f * left, false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text("$secs", style = ios(76, FontWeight.Thin, tracking = -2f))
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (pressed) IOS.Orange.copy(alpha = 0.7f) else IOS.Orange)
                .clickable(press, indication = null, onClick = onAwake),
            contentAlignment = Alignment.Center,
        ) {
            Text("깼어요", style = ios(19, FontWeight.SemiBold, Color.Black))
        }
        Spacer(Modifier.height(32.dp))
    }
}
