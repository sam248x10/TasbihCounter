package com.tasbih.counter

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var canvasView: TasbihCanvasView
    private lateinit var prefs: SharedPreferences
    private lateinit var cycleLabel: TextView
    private lateinit var soundButton: ImageButton
    private var clickTone: ToneGenerator? = null
    private var soundEnabled = true

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(VolumeCounterService.PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean("click_sound_enabled", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val serviceIntent = Intent(this, VolumeCounterService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val rootLayout = FrameLayout(this)

        canvasView = TasbihCanvasView(this).apply {
            isSoundEffectsEnabled = false
            setOnClickListener {
                incrementCount()
                if (soundEnabled) clickTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
            }
        }
        rootLayout.addView(
            canvasView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val resetBtn = Button(this).apply {
            text = "↻  تصفير العداد"
            setTextColor(Color.WHITE)
            textSize = 18f
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#126C72"),
                    Color.parseColor("#1F409F"),
                    Color.parseColor("#6022A9")
                )
            )
            setOnClickListener {
                prefs.edit().putInt(VolumeCounterService.KEY_COUNT, 0).apply()
                refreshCount()
            }
        }

        val infoButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            setColorFilter(0xFFD7B8FF.toInt())
            setBackgroundColor(0xFF11182B.toInt())
            contentDescription = "معلومات المسبحة"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("طريقة الاستخدام")
                    .setMessage("المس الشاشة أو اضغط زر رفع الصوت أو خفضه لزيادة العداد. كل ٣٣ تسبيحة تكمل دورة واحدة. يمكنك كتم صوت النقرة من زر الصوت.")
                    .setPositiveButton("تم", null)
                    .show()
            }
        }
        soundButton = ImageButton(this).apply {
            setColorFilter(0xFF85E1D5.toInt())
            setBackgroundColor(0xFF11182B.toInt())
            setOnClickListener {
                soundEnabled = !soundEnabled
                prefs.edit().putBoolean("click_sound_enabled", soundEnabled).apply()
                updateSoundButton()
            }
        }
        updateSoundButton()

        val toolbar = LinearLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            addView(infoButton, LinearLayout.LayoutParams(dp(48), -1))
            addView(soundButton, LinearLayout.LayoutParams(dp(48), -1))
            addView(resetBtn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        cycleLabel = statusChip("", 0xFFD9C8FF.toInt())
        val statusRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(statusChip("● جاهز للتسبيح", 0xFF85E1D5.toInt()))
            addView(cycleLabel)
            addView(statusChip("أزرار الصوت مفعّلة", 0xFFAFD8FF.toInt()))
        }
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))
            addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
        rootLayout.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val hint = TextView(this).apply {
            text = "انقر في أي مكان على الشاشة لزيادة العداد"
            textSize = 13f
            setTextColor(0xFFA2ADC2.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(18))
        }
        rootLayout.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        refreshCount()
        setContentView(rootLayout)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun statusChip(label: String, color: Int) = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(color)
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0x8011182B.toInt())
            setStroke(dp(1), color and 0x44FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
    }

    private fun updateSoundButton() {
        soundButton.setImageResource(
            if (soundEnabled) android.R.drawable.ic_lock_silent_mode_off
            else android.R.drawable.ic_lock_silent_mode
        )
        soundButton.contentDescription = if (soundEnabled) "كتم صوت النقر" else "تشغيل صوت النقر"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            incrementCount()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun incrementCount() {
        val next = prefs.getInt(VolumeCounterService.KEY_COUNT, 0) + 1
        prefs.edit().putInt(VolumeCounterService.KEY_COUNT, next).apply()
        refreshCount()
    }

    private fun refreshCount() {
        val count = prefs.getInt(VolumeCounterService.KEY_COUNT, 0)
        canvasView.count = count
        cycleLabel.text = "الدورة: ${count / TasbihCanvasView.BEADS_PER_CYCLE}"
    }

    override fun onResume() {
        super.onResume()
        clickTone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()
        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            IntentFilter(VolumeCounterService.ACTION_COUNT_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshCount()
    }

    override fun onPause() {
        unregisterReceiver(updateReceiver)
        clickTone?.release()
        clickTone = null
        super.onPause()
    }
}
