package com.rime.morningalarm.data

import org.json.JSONObject

/**
 * @param days 요일 비트마스크. bit0=일, bit1=월 … bit6=토. 0이면 1회성 알람.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
) {
    fun isOn(day: Int) = (days shr day) and 1 == 1

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute)
        .put("days", days).put("label", label).put("enabled", enabled)

    companion object {
        const val WEEKDAYS = 0b0111110
        const val WEEKEND = 0b1000001
        const val EVERYDAY = 0b1111111

        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.optInt("days", 0),
            label = o.optString("label", ""),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

data class WakeSettings(
    /** 음성으로 불러줄 이름 (자기 이름은 수면 중에도 뇌가 선택적으로 반응한다) */
    val name: String = "",
    val voice: Boolean = true,
    val vibrate: Boolean = true,
    val screenFlash: Boolean = true,
    val torchStrobe: Boolean = false,
    val missionCount: Int = 3,
    /** 0 쉬움, 1 보통, 2 어려움 */
    val difficulty: Int = 1,
    val wakeCheck: Boolean = true,
    val wakeCheckMinutes: Int = 3,
    val snooze: Boolean = true,
    val snoozeMinutes: Int = 5,
    val snoozeMax: Int = 3,
    /** 끄기 미션: 0 암산, 1 패턴, 2 캡챠 */
    val missionType: Int = 0,
    /** 등록한 패턴 (3x3 점 인덱스 0..8 을 쉼표로). 빈 문자열이면 미등록. */
    val pattern: String = "",
    /** 미션 중 소리: 0 그대로, 1 작게, 2 끄기 */
    val missionSound: Int = 1,
) {
    val patternDots: List<Int> get() = pattern.split(',').mapNotNull { it.toIntOrNull() }

    /** 패턴 미션인데 패턴이 없으면 암산으로 대체. */
    val effectiveMission: Int get() = if (missionType == 1 && patternDots.size < 4) 0 else missionType

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("voice", voice).put("vibrate", vibrate)
        .put("screenFlash", screenFlash).put("torchStrobe", torchStrobe)
        .put("missionCount", missionCount).put("difficulty", difficulty)
        .put("wakeCheck", wakeCheck).put("wakeCheckMinutes", wakeCheckMinutes)
        .put("snooze", snooze).put("snoozeMinutes", snoozeMinutes).put("snoozeMax", snoozeMax)
        .put("missionType", missionType).put("pattern", pattern).put("missionSound", missionSound)

    companion object {
        fun fromJson(o: JSONObject) = WakeSettings(
            name = o.optString("name", ""),
            voice = o.optBoolean("voice", true),
            vibrate = o.optBoolean("vibrate", true),
            screenFlash = o.optBoolean("screenFlash", true),
            torchStrobe = o.optBoolean("torchStrobe", false),
            missionCount = o.optInt("missionCount", 3),
            difficulty = o.optInt("difficulty", 1),
            wakeCheck = o.optBoolean("wakeCheck", true),
            wakeCheckMinutes = o.optInt("wakeCheckMinutes", 3),
            snooze = o.optBoolean("snooze", true),
            snoozeMinutes = o.optInt("snoozeMinutes", 5),
            snoozeMax = o.optInt("snoozeMax", 3),
            missionType = o.optInt("missionType", 0),
            pattern = o.optString("pattern", ""),
            missionSound = o.optInt("missionSound", 1),
        )
    }
}
