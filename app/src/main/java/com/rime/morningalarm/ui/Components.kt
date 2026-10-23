package com.rime.morningalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 리플 없이 iOS 처럼 눌림 시 살짝 어두워지는 클릭. */
@Composable
fun Modifier.iosClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .background(if (pressed && enabled) Color.White.copy(alpha = 0.08f) else Color.Transparent)
        .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun IOSSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) IOS.Green else IOS.Fill, tween(220), label = "track")
    val x by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "thumb",
    )
    Box(
        modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = ios(13, color = IOS.Secondary),
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 7.dp),
    )
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text,
        style = ios(13, color = IOS.Secondary).copy(lineHeight = 18.sp),
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
    )
}

/** iOS 의 inset grouped 리스트 섹션. */
@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    background: Color = IOS.Grouped,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (header != null) SectionHeader(header) else Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background),
            content = content,
        )
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
fun IOSDivider(inset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(IOS.Separator),
    )
}

@Composable
fun IOSRow(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = IOS.Label,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.iosClickable(onClick = onClick) else Modifier)
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ios(17, color = titleColor), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun RowScope.ValueChevron(value: String, valueColor: Color = IOS.Secondary) {
    Text(value, style = ios(17, color = valueColor))
    Spacer(Modifier.width(4.dp))
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = IOS.Tertiary,
        modifier = Modifier.size(22.dp),
    )
}

/** iOS UISegmentedControl. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(IOS.Elevated)
            .padding(2.dp),
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(segW)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .background(Color(0xFF636366), RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = ios(13, if (i == selected) FontWeight.SemiBold else FontWeight.Normal),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** iOS UIStepper (− | +). */
@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .size(94.dp, 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(IOS.Elevated),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf("−", value > range.first) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(value - 1)
        }
        Box(Modifier.width(0.5.dp).height(18.dp).background(IOS.Tertiary))
        StepperHalf("+", value < range.last) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(value + 1)
        }
    }
}

@Composable
private fun RowScope.StepperHalf(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .iosClickable(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = ios(22, FontWeight.Light, if (enabled) IOS.Label else IOS.Tertiary))
    }
}

/** 요일 선택 동그라미 (일~토). */
@Composable
fun DayPicker(days: Int, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val names = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        names.forEachIndexed { i, n ->
            val on = (days shr i) and 1 == 1
            val bg by animateColorAsState(if (on) IOS.Orange else IOS.Fill, tween(150), label = "day")
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(days xor (1 shl i))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    n,
                    style = ios(15, FontWeight.SemiBold, if (on) Color.Black else IOS.Label),
                )
            }
        }
    }
}
