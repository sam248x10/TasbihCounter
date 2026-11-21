package com.tasbih.counter

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat

internal fun Context.isScreenOff() = getSystemService(PowerManager::class.java)?.isInteractive == false

internal fun Context.isScreenLocked() =
    isScreenOff() || getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

class LockedCounterService : Service() {
    private lateinit var session: MediaSession
    private lateinit var counter: TasbihCounter
    private var silentTrack: AudioTrack? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateSession()
            if (!activityVisible &&
                (intent.action == Intent.ACTION_USER_PRESENT ||
                    (intent.action == Intent.ACTION_SCREEN_ON && !isScreenLocked()))
            ) {
                stopSelf()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        counter = TasbihCounter(this)
        startForeground(1, notification())
        val mainHandler = Handler(Looper.getMainLooper())
        session = MediaSession(this, "TasbihLockedCounter").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val key = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false
                    if (!isScreenOff() || !isHeadsetButton(key.keyCode)) return false
                    if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) counter.increment()
                    return true
                }
            }, mainHandler)
            setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    if (direction == 1 || direction == -1) {
                        mainHandler.post {
                            if (session.isActive && isScreenOff()) counter.increment()
                        }
                    }
                }
            })
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateSession()
        return START_NOT_STICKY
    }

    private fun updateSession() {
        val screenOff = isScreenOff()
        if (!screenOff) {
            session.isActive = false
            releaseSilentTrack()
        }
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE)
            .setState(if (screenOff) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (screenOff) 1f else 0f)
            .build())
        if (screenOff) {
            session.isActive = true
            if (silentTrack == null) startSilentTrack()
        }
    }

    // Local playback activity helps Android route hardware buttons to this session while asleep.
    private fun startSilentTrack() {
        val sampleRate = 8000
        val channel = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channel, encoding)
        if (bufferSize <= 0) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                    .setChannelMask(channel).setEncoding(encoding).build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            silentTrack = track
            val silence = ByteArray(bufferSize)
            if (track.write(silence, 0, bufferSize) == bufferSize &&
                track.setLoopPoints(0, bufferSize / 2, -1) == AudioTrack.SUCCESS
            ) {
                track.play()
            } else {
                releaseSilentTrack()
            }
        } catch (_: IllegalArgumentException) {
            releaseSilentTrack()
        } catch (_: IllegalStateException) {
            releaseSilentTrack()
        } catch (_: UnsupportedOperationException) {
            releaseSilentTrack()
        }
    }

    private fun releaseSilentTrack() {
        silentTrack?.release()
        silentTrack = null
    }

    private fun isHeadsetButton(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

    @Suppress("DEPRECATION")
    private fun notification(): Notification {
        val channelId = "locked_counter"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "التسبيح عند إطفاء الشاشة", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_IMMUTABLE)
        return builder.setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("المسبحة")
            .setContentText("العد بأزرار السماعة أثناء إطفاء الشاشة")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        session.isActive = false
        session.release()
        releaseSilentTrack()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        internal var activityVisible = false
    }
}
