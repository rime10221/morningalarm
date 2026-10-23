package com.rime.morningalarm.ui

import android.Manifest
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rime.morningalarm.R
import com.rime.morningalarm.alarm.AlarmScheduler
import com.rime.morningalarm.data.Alarm
import com.rime.morningalarm.data.AlarmStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        AlarmScheduler.rescheduleAll(this)
        setContent { MorningTheme { MainScreen() } }
    }
}

/** onResume 마다 증가하는 값. 설정 화면에서 돌아왔을 때 권한 상태를 다시 읽는 데 사용. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
private fun MainScreen() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorAlarm by remember { mutableStateOf<Alarm?>(null) }
    var editorIsNew by remember { mutableStateOf(true) }

    val resume = rememberResumeTick()
    val perms = remember(resume) { Perms.status(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Perms.status(ctx).notifications) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openEditor(alarm: Alarm?) {
        editorIsNew = alarm == null
        editorAlarm = alarm ?: Alarm(id = AlarmStore.newId(), hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        editorOpen = true
    }

    BackHandler(editorOpen) { editorOpen = false }

    Box(Modifier.fillMaxSize().background(IOS.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> AlarmListScreen(
                        perms = perms,
                        onAdd = { openEditor(null) },
                        onEdit = { openEditor(it) },
                        onFixPermissions = { tab = 1 },
                    )
                    else -> SettingsScreen(perms = perms)
                }
            }
            TabBar(tab) { tab = it }
        }

        // 아래에서 올라오는 시트
        AnimatedVisibility(editorOpen, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            editorOpen,
            enter = slideInVertically(tween(380)) { it },
            exit = slideOutVertically(tween(300)) { it },
        ) {
            editorAlarm?.let { alarm ->
                AlarmEditor(
                    initial = alarm,
                    isNew = editorIsNew,
                    onCancel = { editorOpen = false },
                    onSave = { saved ->
                        AlarmStore.upsert(saved)
                        AlarmScheduler.schedule(ctx, saved)
                        editorOpen = false
                        Toast.makeText(ctx, untilText(AlarmScheduler.nextTrigger(saved)) + " 후에 울립니다", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        AlarmScheduler.cancel(ctx, alarm.id)
                        AlarmStore.delete(alarm.id)
                        editorOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF121212))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(IOS.Separator))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TabItem("알람", selected == 0, Modifier.weight(1f), { onSelect(0) }) { tint ->
                Icon(painterResource(R.drawable.ic_alarm), null, tint = tint, modifier = Modifier.size(26.dp))
            }
            TabItem("설정", selected == 1, Modifier.weight(1f), { onSelect(1) }) { tint ->
                Icon(Icons.Filled.Settings, null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) IOS.Orange else IOS.Secondary
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = ios(10, FontWeight.SemiBold, tint))
    }
}

fun untilText(at: Long, now: Long = System.currentTimeMillis()): String {
    val mins = ((at - now + 59_999) / 60_000).coerceAtLeast(0)
    val d = mins / (60 * 24)
    val h = (mins / 60) % 24
    val m = mins % 60
    return buildString {
        if (d > 0) append("${d}일 ")
        if (h > 0) append("${h}시간 ")
        if (m > 0 || (d == 0L && h == 0L)) append("${m}분")
    }.trim()
}
