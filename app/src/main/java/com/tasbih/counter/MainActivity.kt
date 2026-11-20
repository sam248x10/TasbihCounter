package com.tasbih.counter

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var canvasView: TasbihCanvasView
    private lateinit var prefs: SharedPreferences

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(VolumeCounterService.PREFS_NAME, Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val serviceIntent = Intent(this, VolumeCounterService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val rootLayout = FrameLayout(this).apply {
            setPadding(0, 0, 0, 0)
        }

        canvasView = TasbihCanvasView(this).apply {
            count = prefs.getInt(VolumeCounterService.KEY_COUNT, 0)
            setOnClickListener {
                val next = prefs.getInt(VolumeCounterService.KEY_COUNT, 0) + 1
                prefs.edit().putInt(VolumeCounterService.KEY_COUNT, next).apply()
                count = next
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
            text = "طھطµظپظٹط± ط§ظ„ط¹ط¯ط§ط¯"
            setTextColor(Color.WHITE)
            textSize = 18f
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#8B5CF6"),
                    Color.parseColor("#3B82F6"),
                    Color.parseColor("#06B6D4")
                )
            )
            setOnClickListener {
                prefs.edit().putInt(VolumeCounterService.KEY_COUNT, 0).apply()
                canvasView.count = 0
            }
        }
        val topParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            150,
            Gravity.TOP
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        rootLayout.addView(resetBtn, topParams)

        setContentView(rootLayout)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val next = prefs.getInt(VolumeCounterService.KEY_COUNT, 0) + 1
            prefs.edit().putInt(VolumeCounterService.KEY_COUNT, next).apply()
            canvasView.count = next
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshCount() {
        canvasView.count = prefs.getInt(VolumeCounterService.KEY_COUNT, 0)
    }

    override fun onResume() {
        super.onResume()
        refreshCount()
        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            IntentFilter(VolumeCounterService.ACTION_COUNT_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        unregisterReceiver(updateReceiver)
        super.onPause()
    }
}
