package com.rime.morningalarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rime.morningalarm.R

/** iOS 시스템 색상 (다크 모드 기준) + 미션 화면용 라이트 색상. */
object IOS {
    val Bg = Color(0xFF000000)
    val Grouped = Color(0xFF1C1C1E)
    val Elevated = Color(0xFF2C2C2E)
    val Fill = Color(0xFF39393D)
    val Separator = Color(0xFF38383A)
    val Label = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF8E8E93)
    val Tertiary = Color(0xFF5A5A5E)
    val Orange = Color(0xFFFF9F0A)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)

    val LightBg = Color(0xFFF2F2F7)
    val LightCard = Color(0xFFFFFFFF)
    val LightKey = Color(0xFFE5E5EA)
    val LightKeyPressed = Color(0xFFC7C7CC)
    val LightLabel = Color(0xFF000000)
    val LightSecondary = Color(0xFF6C6C70)
    val LightOrange = Color(0xFFFF9500)
    val LightRed = Color(0xFFFF3B30)
}

/** Pretendard: Apple SF Pro 의 비율을 한글에 맞춰 설계한 오픈소스 서체. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

fun ios(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = IOS.Label, tracking: Float = 0f) =
    TextStyle(
        fontFamily = Pretendard,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        letterSpacing = tracking.sp,
    )

@Composable
fun MorningTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = Pretendard)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IOS.Orange,
            background = IOS.Bg,
            surface = IOS.Grouped,
            onSurface = IOS.Label,
            onBackground = IOS.Label,
        ),
        typography = Typography(
            bodyLarge = base, bodyMedium = base, bodySmall = base,
            titleLarge = base, titleMedium = base, titleSmall = base,
            labelLarge = base, labelMedium = base, labelSmall = base,
        ),
        content = content,
    )
}
