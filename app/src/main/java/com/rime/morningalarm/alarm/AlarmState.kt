package com.rime.morningalarm.alarm

import kotlinx.coroutines.flow.MutableStateFlow

enum class RingMode {
    /** 알람이 울리는 중. 미션을 풀어야 꺼진다. */
    RINGING,

    /** 알람을 끈 뒤 다시 잠들지 않았는지 확인하는 중. */
    CHECK,
}

data class RingSession(
    val mode: RingMode,
    val label: String,
    val startedAt: Long = System.currentTimeMillis(),
    val checkDeadline: Long = 0L,
    /** 남은 다시 울림 횟수. 0 이면 버튼 숨김. */
    val snoozeLeft: Int = 0,
    /** 지금까지 다시 울림을 누른 횟수. */
    val snoozeUsed: Int = 0,
)

/** 서비스와 알람 화면이 공유하는 상태 (같은 프로세스). */
object AlarmState {
    val session = MutableStateFlow<RingSession?>(null)

    /** 알람 화면이 현재 사용자에게 보이고 있는지. 서비스가 화면 이탈을 감지하는 데 사용. */
    @Volatile var ringVisible = false

    /** 미션(문제 풀이) 화면에 있는지. 이 동안은 소리를 약간 줄이고 음성은 멈춘다. */
    val inMission = MutableStateFlow(false)
}
