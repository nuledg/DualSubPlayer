package com.dualsub.player.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.roundToInt

/** Volume (system music stream) and brightness (this window only), both as 0..100. */
class DeviceControls(private val activity: Activity) {

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumePercent(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max
    }

    fun setVolumePercent(percent: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = (percent / 100f * max).roundToInt().coerceIn(0, max)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        } catch (_: SecurityException) {
            // Some devices block volume changes in special modes. We ignore it.
        }
    }

    fun brightnessPercent(): Float {
        val current = activity.window.attributes.screenBrightness
        val value = if (current >= 0f) current else systemBrightness()
        return value * 100f
    }

    fun setBrightnessPercent(percent: Float) {
        val params = activity.window.attributes
        // 0.01 instead of 0 so the screen never turns fully black and unusable.
        params.screenBrightness = (percent / 100f).coerceIn(0.01f, 1f)
        activity.window.attributes = params
    }

    /** Gives brightness control back to the system. Called when leaving the player. */
    fun resetBrightness() {
        val params = activity.window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = params
    }

    private fun systemBrightness(): Float = try {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Settings.SettingNotFoundException) {
        0.5f
    }
}
