package com.rime.morningalarm.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** 카메라 플래시를 켜고 끈다. setTorchMode 는 CAMERA 권한이 필요 없다. */
class Torch(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = try {
        cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }

    private var on = false

    fun toggle() = set(!on)

    fun set(value: Boolean) {
        val id = cameraId ?: return
        try {
            cm.setTorchMode(id, value)
            on = value
        } catch (_: Exception) {
        }
    }
}
