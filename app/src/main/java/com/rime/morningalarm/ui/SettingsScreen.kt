package com.rime.morningalarm.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rime.morningalarm.alarm.AlarmScheduler
import com.rime.morningalarm.data.AlarmStore
import com.rime.morningalarm.data.WakeSettings

@Composable
fun SettingsScreen(perms: PermStatus) {
    val ctx = LocalContext.current
    val s by AlarmStore.settings.collectAsStateWithLifecycle()
    fun update(f: (WakeSettings) -> WakeSettings) = AlarmStore.updateSettings(f)
    fun open(intent: Intent) = try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(ctx, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) open(Perms.notificationSettings(ctx))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(44.dp))
        Text(
            "설정",
            style = ios(34, FontWeight.Bold, tracking = -0.4f),
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )

        // ---------------------------------------------------------- 깨우기
        GroupedSection(
            header = "깨우기",
            footer = "이름을 입력하면 알람이 울린 지 20초 후부터 낯선 목소리로 이름을 부릅니다. " +
                "자기 이름은 잠든 뇌도 골라 듣습니다.",
        ) {
            IOSRow("내 이름") {
                BasicTextField(
                    value = s.name,
                    onValueChange = { v -> update { it.copy(name = v.take(10)) } },
                    singleLine = true,
                    textStyle = ios(17, color = IOS.Secondary).copy(textAlign = TextAlign.End),
                    cursorBrush = SolidColor(IOS.Orange),
                    modifier = Modifier.width(160.dp),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterEnd) {
                            if (s.name.isEmpty()) Text("예: 민수", style = ios(17, color = IOS.Tertiary))
                            inner()
                        }
                    },
                )
            }
            IOSDivider()
            IOSRow("음성으로 이름 부르기") { IOSSwitch(s.voice, { v -> update { it.copy(voice = v) } }) }
            IOSDivider()
            IOSRow("진동") { IOSSwitch(s.vibrate, { v -> update { it.copy(vibrate = v) } }) }
            IOSDivider()
            IOSRow("화면 점멸") { IOSSwitch(s.screenFlash, { v -> update { it.copy(screenFlash = v) } }) }
            IOSDivider()
            IOSRow("플래시 라이트 점멸") { IOSSwitch(s.torchStrobe, { v -> update { it.copy(torchStrobe = v) } }) }
        }

        // ---------------------------------------------------------- 미션
        GroupedSection(
            header = "끄기 미션",
            footer = "30초 동안 입력이 없으면 처음부터 다시 풀어야 합니다.",
        ) {
            IOSRow("문제 수") {
                Text("${s.missionCount}개", style = ios(17, color = IOS.Secondary))
                Spacer(Modifier.width(12.dp))
                Stepper(s.missionCount, 1..10) { v -> update { it.copy(missionCount = v) } }
            }
            IOSDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("난이도", style = ios(17))
                Spacer(Modifier.height(10.dp))
                SegmentedControl(listOf("쉬움", "보통", "어려움"), s.difficulty, { v -> update { it.copy(difficulty = v) } })
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf("예) 37 + 25", "예) 34 × 7 + 52", "예) 27 × 18 + 41")[s.difficulty.coerceIn(0, 2)],
                    style = ios(13, color = IOS.Secondary),
                )
            }
        }

        // ---------------------------------------------------------- 기상 확인
        GroupedSection(
            header = "기상 확인",
            footer = "알람을 끈 뒤 다시 잠드는 걸 막습니다. 정해진 시간 후 확인 화면이 뜨고, " +
                "60초 안에 누르지 않으면 최고 강도로 다시 울립니다.",
        ) {
            IOSRow("다시 잠들었는지 확인") { IOSSwitch(s.wakeCheck, { v -> update { it.copy(wakeCheck = v) } }) }
            if (s.wakeCheck) {
                IOSDivider()
                IOSRow("확인 시점") {
                    Text("${s.wakeCheckMinutes}분 후", style = ios(17, color = IOS.Secondary))
                    Spacer(Modifier.width(12.dp))
                    Stepper(s.wakeCheckMinutes, 1..15) { v -> update { it.copy(wakeCheckMinutes = v) } }
                }
            }
        }

        // ---------------------------------------------------------- 권한
        GroupedSection(
            header = "권한",
            footer = "삼성 기기는 '설정 › 배터리 › 백그라운드 사용 제한'에서 이 앱이 '절전 예외 앱'에 있는지도 확인하세요.",
        ) {
            PermRow("알림", perms.notifications) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(Perms.notificationSettings(ctx))
            }
            IOSDivider()
            PermRow("잠금화면 위 전체 화면 알림", perms.fullScreen) { open(Perms.fullScreenSettings(ctx)) }
            IOSDivider()
            PermRow("정확한 알람", perms.exactAlarm) { open(Perms.exactAlarmSettings(ctx)) }
            IOSDivider()
            PermRow("다른 앱 위에 표시", perms.overlay, optionalNote = "탈출 방지") { open(Perms.overlaySettings(ctx)) }
            IOSDivider()
            PermRow("배터리 최적화 제외", perms.battery, optionalNote = "권장") { open(Perms.batterySettings(ctx)) }
        }

        // ---------------------------------------------------------- 테스트
        GroupedSection(footer = "화면을 끄고 기다려 보세요. 실제 알람과 똑같이 울리고, 미션을 풀어야 꺼집니다.") {
            IOSRow("10초 후 테스트 알람", titleColor = IOS.Orange, onClick = {
                AlarmScheduler.scheduleTest(ctx, 10)
                Toast.makeText(ctx, "10초 후 울립니다", Toast.LENGTH_SHORT).show()
            })
        }

        // ---------------------------------------------------------- 과학
        GroupedSection(header = "왜 깰 수밖에 없나요?") {
            Science.forEachIndexed { i, item ->
                if (i > 0) IOSDivider()
                ScienceRow(item)
            }
        }
        SectionFooter("고주파 '삐삐' 소리 대신, 수면 연구에서 사람을 가장 잘 깨운 신호들을 조합해 매번 실시간으로 합성합니다.")
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermRow(title: String, granted: Boolean, optionalNote: String? = null, onClick: () -> Unit) {
    IOSRow(title, onClick = if (granted) null else onClick) {
        if (granted) {
            Icon(Icons.Filled.CheckCircle, "허용됨", tint = IOS.Green, modifier = Modifier.size(22.dp))
        } else {
            ValueChevron(if (optionalNote != null) "허용 ($optionalNote)" else "허용 필요", IOS.Orange)
        }
    }
}

private data class ScienceItem(val title: String, val body: String, val source: String)

private val Science = listOf(
    ScienceItem(
        "520Hz 사각파 · T3 리듬",
        "일반 알람의 높은 '삐삐'(약 3kHz) 소리보다 낮은 520Hz 사각파가 깊이 잠든 사람을 훨씬 낮은 음량에서도 깨웠습니다. " +
            "이 결과로 미국 화재경보 기준(NFPA 72)은 침실용 저주파 경보음을 도입했습니다. 0.5초 켜짐·꺼짐 3회 후 쉬는 T3 리듬을 사용합니다.",
        "Bruck et al., J Sleep Res 2009 · Bruck & Thomas, Ear Hear 2009",
    ),
    ScienceItem(
        "거칠기 (Roughness)",
        "비명 소리의 핵심은 음높이가 아니라 30–150Hz로 빠르게 떨리는 '거칠기'입니다. 이 대역은 편도체를 자극하고 " +
            "뇌를 강제로 동기화시켜 불쾌감과 회피 반응을 일으킵니다. 20초가 지나면 소리에 거칠기가 더해집니다.",
        "Arnal et al., Curr Biol 2015 · Nat Commun 2019",
    ),
    ScienceItem(
        "매일 달라지는 소리",
        "같은 자극이 반복되면 뇌는 그것을 '무시해도 되는 것'으로 학습합니다(습관화). 10년 쓴 알람이 안 들리는 이유입니다. " +
            "이 앱은 녹음된 음원이 없고, 매일 음높이·리듬·질감을 바꿔 실시간 합성합니다. 45초 이후에는 예측할 수 없는 패턴과 침묵 뒤의 갑작스러운 소리가 이어집니다.",
        "Rankin et al., Neurobiol Learn Mem 2009",
    ),
    ScienceItem(
        "이름 부르기 · 낯선 목소리",
        "잠든 상태에서도 뇌는 다른 이름보다 자기 이름에 더 크게 반응하고, 익숙한 목소리보다 낯선 목소리에 더 강하게 반응합니다.",
        "Perrin et al., Clin Neurophysiol 1999 · Blume et al., NeuroImage 2018",
    ),
    ScienceItem(
        "빛과 진동",
        "밝은 빛은 멜라토닌을 억제하고 각성도를 높여 기상 직후의 멍함(수면 관성)을 줄입니다. 화면 밝기를 최대로 올리고 1초 주기로 점멸합니다(광과민성 발작 위험 대역인 3Hz 미만). " +
            "진동 같은 촉각 자극도 청각만큼 효과적인 각성 신호입니다.",
        "Cajochen, Sleep Med Rev 2007 · Hilditch et al., J Sleep Res 2022",
    ),
    ScienceItem(
        "암산 미션 · 기상 확인",
        "수면 관성은 깬 뒤 15–30분 지속되며 이때 다시 잠들기 쉽습니다. 암산은 전전두엽을 강제로 가동시키고, " +
            "몇 분 뒤 기상 확인은 '끄고 다시 자기'를 막습니다.",
        "Tassi & Muzet, Sleep Med Rev 2000",
    ),
    ScienceItem(
        "트레이드오프",
        "멜로디 알람이 기상 후 멍함을 줄인다는 연구도 있습니다. 이 앱은 '확실히 깨우기'를 우선하고, 대신 빛과 인지 과제로 멍함을 상쇄합니다.",
        "McFarlane et al., PLOS ONE 2020",
    ),
)

@Composable
private fun ScienceRow(item: ScienceItem) {
    var open by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Column(Modifier.fillMaxWidth().iosClickable { open = !open }) {
        IOSRow(item.title) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = IOS.Tertiary,
                modifier = Modifier.size(22.dp).rotate(angle),
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Text(item.body, style = ios(15, color = IOS.Label.copy(alpha = 0.85f)).copy(lineHeight = 22.sp))
                Spacer(Modifier.height(8.dp))
                Text(item.source, style = ios(12, color = IOS.Secondary))
            }
        }
    }
}

