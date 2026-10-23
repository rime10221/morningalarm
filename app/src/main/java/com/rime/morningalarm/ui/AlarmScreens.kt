package com.rime.morningalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rime.morningalarm.alarm.AlarmScheduler
import com.rime.morningalarm.data.Alarm
import com.rime.morningalarm.data.AlarmStore
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

// ------------------------------------------------------------------ list

@Composable
fun AlarmListScreen(
    perms: PermStatus,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onFixPermissions: () -> Unit,
) {
    val ctx = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    val next = alarms.filter { it.enabled }.minOfOrNull { AlarmScheduler.nextTrigger(it, now) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).iosClickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, "알람 추가", tint = IOS.Orange, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                "알람",
                style = ios(34, FontWeight.Bold, tracking = -0.4f),
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
            Text(
                if (next != null) "다음 알람까지 ${untilText(next, now)}" else "켜진 알람 없음",
                style = ios(15, color = IOS.Secondary),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        }

        if (!perms.criticalOk) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IOS.Grouped)
                        .iosClickable(onClick = onFixPermissions)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = IOS.Orange, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("알람이 울리지 않을 수 있어요", style = ios(16, FontWeight.SemiBold))
                        Text("필수 권한을 허용해 주세요", style = ios(14, color = IOS.Secondary))
                    }
                    Row { ValueChevron("") }
                }
            }
        }

        item { IOSDivider() }

        if (alarms.isEmpty()) {
            item {
                Text(
                    "알람 없음",
                    style = ios(20, color = IOS.Secondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 140.dp),
                )
            }
        }

        items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm,
                onClick = { onEdit(alarm) },
                onToggle = { on ->
                    val updated = alarm.copy(enabled = on)
                    AlarmStore.upsert(updated)
                    AlarmScheduler.schedule(ctx, updated)
                },
            )
            IOSDivider()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val color = if (alarm.enabled) IOS.Label else IOS.Secondary
    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(ampm(alarm.hour), style = ios(24, FontWeight.Light, color), modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.width(4.dp))
                Text(clock(alarm.hour, alarm.minute), style = ios(58, FontWeight.Light, color, tracking = -1f))
            }
            Text(
                listOf(alarm.label.ifBlank { "알람" }, repeatText(alarm.days)).joinToString(", "),
                style = ios(15, color = color),
            )
        }
        IOSSwitch(alarm.enabled, onToggle)
    }
}

fun ampm(hour: Int) = if (hour < 12) "오전" else "오후"
fun clock(hour: Int, minute: Int) = "${if (hour % 12 == 0) 12 else hour % 12}:${"%02d".format(minute)}"

fun repeatText(days: Int): String = when (days) {
    0 -> "한 번"
    Alarm.EVERYDAY -> "매일"
    Alarm.WEEKDAYS -> "주중"
    Alarm.WEEKEND -> "주말"
    else -> {
        // 월요일부터 표시
        val names = listOf("일", "월", "화", "수", "목", "금", "토")
        (1..7).map { it % 7 }.filter { (days shr it) and 1 == 1 }.joinToString(" ") { names[it] }
    }
}

// ------------------------------------------------------------------ editor

@Composable
fun AlarmEditor(
    initial: Alarm,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
) {
    var pm by remember { mutableIntStateOf(if (initial.hour >= 12) 1 else 0) }
    var hour12 by remember { mutableIntStateOf(initial.hour % 12) } // 0 = 12시
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableIntStateOf(initial.days) }
    var label by remember { mutableStateOf(initial.label) }
    val settings by AlarmStore.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(IOS.Grouped)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 상단 바
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            Text(
                "취소",
                style = ios(17, color = IOS.Orange),
                modifier = Modifier.align(Alignment.CenterStart).iosClickable(onClick = onCancel).padding(6.dp),
            )
            Text(
                if (isNew) "알람 추가" else "알람 편집",
                style = ios(17, FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                "저장",
                style = ios(17, FontWeight.SemiBold, IOS.Orange),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .iosClickable {
                        val h24 = hour12 + if (pm == 1) 12 else 0
                        onSave(initial.copy(hour = h24, minute = minute, days = days, label = label.trim(), enabled = true))
                    }
                    .padding(6.dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 휠 피커
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IOS.Elevated),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelPicker(listOf("오전", "오후"), pm, { pm = it }, width = 80.dp)
                    WheelPicker(
                        (1..12).map { it.toString() },
                        if (hour12 == 0) 11 else hour12 - 1,
                        { hour12 = (it + 1) % 12 },
                        width = 70.dp, cyclic = true, alignment = TextAlign.End,
                    )
                    Text(":", style = ios(23), modifier = Modifier.padding(horizontal = 6.dp))
                    WheelPicker(
                        (0..59).map { "%02d".format(it) },
                        minute,
                        { minute = it },
                        width = 70.dp, cyclic = true, alignment = TextAlign.Start,
                    )
                }
            }

            GroupedSection(header = "반복", background = IOS.Elevated) {
                DayPicker(days) { days = it }
                IOSDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickChip("주중", days == Alarm.WEEKDAYS) { days = Alarm.WEEKDAYS }
                    QuickChip("주말", days == Alarm.WEEKEND) { days = Alarm.WEEKEND }
                    QuickChip("매일", days == Alarm.EVERYDAY) { days = Alarm.EVERYDAY }
                    QuickChip("한 번", days == 0) { days = 0 }
                }
            }

            GroupedSection(background = IOS.Elevated) {
                IOSRow("레이블") {
                    BasicTextField(
                        value = label,
                        onValueChange = { label = it.take(30) },
                        singleLine = true,
                        textStyle = ios(17, color = IOS.Secondary).copy(textAlign = TextAlign.End),
                        cursorBrush = SolidColor(IOS.Orange),
                        modifier = Modifier.width(200.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (label.isEmpty()) Text("알람", style = ios(17, color = IOS.Tertiary))
                                inner()
                            }
                        },
                    )
                }
            }

            val diff = listOf("쉬움", "보통", "어려움")[settings.difficulty.coerceIn(0, 2)]
            SectionFooter(
                "끄려면 암산 문제 ${settings.missionCount}개($diff)를 풀어야 합니다." +
                    if (settings.wakeCheck) " 끈 뒤 ${settings.wakeCheckMinutes}분 후 다시 깨어 있는지 확인합니다." else "",
            )

            if (!isNew) {
                GroupedSection(background = IOS.Elevated) {
                    IOSRow("알람 삭제", titleColor = IOS.Red, onClick = onDelete)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = ios(14, FontWeight.SemiBold, if (selected) IOS.Orange else IOS.Secondary),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) IOS.Orange.copy(alpha = 0.18f) else IOS.Fill.copy(alpha = 0.5f))
            .iosClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
