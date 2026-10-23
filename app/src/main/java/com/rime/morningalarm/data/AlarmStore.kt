package com.rime.morningalarm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 알람/설정 저장소. 재부팅 직후(잠금 해제 전)에도 알람을 복구할 수 있도록
 * Device Protected Storage 에 저장한다.
 */
object AlarmStore {
    private lateinit var prefs: SharedPreferences

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _settings = MutableStateFlow(WakeSettings())
    val settings: StateFlow<WakeSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val storage = context.applicationContext.createDeviceProtectedStorageContext()
        prefs = storage.getSharedPreferences("morning_store", Context.MODE_PRIVATE)

        val arr = JSONArray(prefs.getString("alarms", "[]"))
        _alarms.value = (0 until arr.length())
            .map { Alarm.fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
        _settings.value = prefs.getString("settings", null)
            ?.let { WakeSettings.fromJson(JSONObject(it)) } ?: WakeSettings()
    }

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun newId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) {
        saveAlarms(_alarms.value.filterNot { it.id == alarm.id } + alarm)
    }

    @Synchronized
    fun delete(id: Int) = saveAlarms(_alarms.value.filterNot { it.id == id })

    @Synchronized
    fun updateSettings(transform: (WakeSettings) -> WakeSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit().putString("settings", s.toJson().toString()).apply()
    }

    /**
     * 알람이 울리기 전 원래 알람 볼륨. 프로세스가 죽어도 복원할 수 있게 디스크에 저장한다.
     * -1 이면 저장된 값 없음.
     */
    var savedVolume: Int
        get() = prefs.getInt("saved_volume", -1)
        set(v) {
            prefs.edit().putInt("saved_volume", v).commit()
        }

    private fun saveAlarms(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }))
        _alarms.value = sorted
        val arr = JSONArray().apply { sorted.forEach { put(it.toJson()) } }
        prefs.edit().putString("alarms", arr.toString()).apply()
    }
}
