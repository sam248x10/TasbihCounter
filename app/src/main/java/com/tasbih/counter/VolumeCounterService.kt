package com.tasbih.counter

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat

class VolumeCounterService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var silentTrack: AudioTrack? = null

    companion object {
        const val ACTION_COUNT_UPDATED = "com.tasbih.counter.UPDATED"
        const val PREFS_NAME = "TasbihPrefs"
        const val KEY_COUNT = "count"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        startSilentAudioKeepAlive()
        setupMediaSession()
    }

    private fun startSilentAudioKeepAlive() {
        val sampleRate = 8000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        silentTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val silence = ByteArray(bufferSize)
        silentTrack?.write(silence, 0, silence.size)
        silentTrack?.setLoopPoints(0, silence.size / 2, -1)
        silentTrack?.play()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "TasbihVolumeSession").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build()
            )

            val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    if (direction == 1 || direction == -1) {
                        incrementCounter()
                    }
                }
            }
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }
    }

    private fun incrementCounter() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextCount = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, nextCount).apply()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(35)
        }

        sendBroadcast(Intent(ACTION_COUNT_UPDATED).setPackage(packageName))
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "tasbih_channel")
            .setContentTitle("ط§ظ„ظ…ط³ط¨ط­ط© ظ†ط´ط·ط© ظپظٹ ط§ظ„ط®ظ„ظپظٹط©")
            .setContentText("ط§ط¶ط؛ط· ط±ظپط¹ ط£ظˆ ط®ظپط¶ ط§ظ„طµظˆطھ ظ„ظ„ط¹ط¯ظ‘ ط­طھظ‰ ظˆط§ظ„ط´ط§ط´ط© ظ…ط·ظپظٹط©")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tasbih_channel", "ط®ط¯ظ…ط© ط¹ط¯ظ‘ط§ط¯ ط§ظ„طµظˆطھ", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        silentTrack?.stop()
        silentTrack?.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
