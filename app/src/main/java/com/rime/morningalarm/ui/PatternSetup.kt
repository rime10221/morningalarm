package com.rime.morningalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** 끄기용 패턴 등록: 두 번 그려서 일치하면 저장. */
@Composable
fun PatternSetupDialog(onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var first by remember { mutableStateOf<List<Int>?>(null) }
        var message by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        var attempt by remember { mutableIntStateOf(0) }

        // 오류 표시 후 선 지우기
        LaunchedEffect(error, attempt) {
            if (error) {
                delay(700)
                error = false
                attempt++
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(IOS.Grouped)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
                Text(
                    "취소",
                    style = ios(17, color = IOS.Orange),
                    modifier = Modifier.align(Alignment.CenterStart).iosClickable(onClick = onDismiss).padding(6.dp),
                )
                Text("끄기 패턴", style = ios(17, FontWeight.SemiBold), modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(40.dp))
            Text(
                if (first == null) "새 패턴을 그리세요" else "확인을 위해 한 번 더 그리세요",
                style = ios(22, FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message.ifEmpty { "점 4개 이상 연결" },
                style = ios(15, color = if (error) IOS.Red else IOS.Secondary),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            key(attempt) {
                PatternLock(
                    colors = PatternColors(IOS.Tertiary, IOS.Orange, IOS.Red),
                    error = error,
                    onStart = { message = "" },
                    onComplete = { drawn ->
                        val f = first
                        when {
                            drawn.size < 4 -> {
                                message = "점을 4개 이상 연결하세요"
                                error = true
                            }
                            f == null -> {
                                first = drawn
                                attempt++
                            }
                            f == drawn -> onSave(drawn)
                            else -> {
                                message = "패턴이 일치하지 않아요. 처음부터 다시 그리세요"
                                first = null
                                error = true
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 44.dp),
                )
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}
