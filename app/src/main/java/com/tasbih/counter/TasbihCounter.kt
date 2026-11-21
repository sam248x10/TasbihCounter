package com.tasbih.counter

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

internal class TasbihCounter(private val context: Context) {
    val prefs = context.getSharedPreferences("TasbihPrefs", Context.MODE_PRIVATE)

    val count get() = prefs.getInt("count", 0)

    var cycleSize: Int
        get() = if (prefs.getInt("cycle_size", 33) == 100) 100 else 33
        set(value) { prefs.edit().putInt("cycle_size", value).apply() }

    fun reset() {
        prefs.edit().putInt("count", 0).apply()
    }

    // Activity key events and media session callbacks both run on the main thread.
    fun increment() {
        val next = count + 1
        prefs.edit().putInt("count", next).apply()
        if (next % cycleSize == 0) vibrate()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 90, 65, 140)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attributes)
        } else {
            vibrator.vibrate(pattern, -1, attributes)
        }
    }
}
