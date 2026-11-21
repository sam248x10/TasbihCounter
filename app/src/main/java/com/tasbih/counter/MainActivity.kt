package com.tasbih.counter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME = "TasbihPrefs"
        const val KEY_COUNT = "count"
        const val KEY_DHIKR = "selected_dhikr"
    }

    private enum class Dhikr(val words: String) {
        PRAISE("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"),
        PROTECTION("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ"),
        FORGIVENESS("أَسْتَغْفِرُ اللَّهَ"),
        PEACE("اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ"),
        GLORY("سُبْحَانَ اللَّهِ"),
        THANKS("الْحَمْدُ لِلَّهِ"),
        GREATNESS("اللَّهُ أَكْبَرُ"),
        ONENESS("لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ")
    }

    private lateinit var canvasView: TasbihCanvasView
    private lateinit var prefs: SharedPreferences
    private lateinit var cycleLabel: TextView
    private lateinit var soundButton: ImageButton
    private lateinit var dhikrText: TextView
    private lateinit var dhikrScroll: ScrollView
    private var selectedDhikr: Dhikr? = null
    private var clickTone: ToneGenerator? = null
    private var soundEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean("click_sound_enabled", true)
        selectedDhikr = Dhikr.values().firstOrNull { it.name == prefs.getString(KEY_DHIKR, null) }

        val rootLayout = FrameLayout(this)

        canvasView = TasbihCanvasView(this).apply {
            isSoundEffectsEnabled = false
            setOnClickListener { countTap() }
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
                prefs.edit().putInt(KEY_COUNT, 0).apply()
                refreshCount()
            }
        }

        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(0xFFD7B8FF.toInt())
            setBackgroundColor(0xFF11182B.toInt())
            contentDescription = "الإعدادات"
            setOnClickListener { showSettings() }
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
            addView(settingsButton, LinearLayout.LayoutParams(dp(48), -1))
            addView(soundButton, LinearLayout.LayoutParams(dp(48), -1))
            addView(resetBtn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        cycleLabel = statusChip("", 0xFFD9C8FF.toInt())
        val statusRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(actionButton("اختر الأذكار", selected = true).apply {
                setOnClickListener { showDhikrPicker() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            addView(cycleLabel)
        }
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))
            addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
        rootLayout.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        dhikrText = arabicText("", 19f, 0xFFD6E4EB.toInt()).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isSoundEffectsEnabled = false
            setOnClickListener { countTap() }
        }
        dhikrScroll = ScrollView(this).apply {
            visibility = View.INVISIBLE
            isFillViewport = true
            addView(dhikrText, FrameLayout.LayoutParams(-1, -2))
        }
        rootLayout.addView(dhikrScroll, FrameLayout.LayoutParams(-1, 0).apply {
            setMargins(dp(18), 0, dp(18), 0)
        })
        rootLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val params = dhikrScroll.layoutParams as FrameLayout.LayoutParams
            val top = topPanel.bottom + dp(6)
            val availableHeight = (canvasView.lowerRingTop - dp(8) - top).coerceAtLeast(0)
            if (params.topMargin != top || params.height != availableHeight) {
                params.topMargin = top
                params.height = availableHeight
                dhikrScroll.layoutParams = params
            }
        }

        val hint = TextView(this).apply {
            text = "انقر في أي مكان على الشاشة لزيادة العداد"
            textSize = 13f
            setTextColor(0xFFA2ADC2.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(18))
        }
        rootLayout.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        updateDhikrDisplay(animate = false)
        refreshCount()
        setContentView(rootLayout)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun updateDhikrDisplay(animate: Boolean) {
        val dhikr = selectedDhikr
        val wasVisible = dhikrScroll.visibility == View.VISIBLE
        if (!animate || wasVisible != (dhikr != null)) {
            canvasView.positionBelowDhikr(dhikr != null, animate)
            dhikrScroll.animate().cancel()
            dhikrScroll.visibility = if (dhikr == null) View.INVISIBLE else View.VISIBLE
            dhikrScroll.alpha = 1f
            if (animate && dhikr != null) {
                dhikrScroll.alpha = 0f
                dhikrScroll.animate().alpha(1f).setStartDelay(TasbihCanvasView.MOVE_DURATION)
                    .setDuration(160L).start()
            }
        }
        dhikrText.text = dhikr?.words.orEmpty()
        dhikrScroll.scrollTo(0, 0)
    }

    private fun selectDhikr(dhikr: Dhikr?) {
        selectedDhikr = dhikr
        prefs.edit().putString(KEY_DHIKR, dhikr?.name).apply()
        updateDhikrDisplay(animate = true)
    }

    private fun showSettings() {
        val panel = dialogPanel("الإعدادات")
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        panel.addView(arabicText("رقم الإصدار $versionName", 16f, 0xFFD6E4EB.toInt()))
        panel.addView(actionButton("زيارة موقع المطور").apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sameerlab.com")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "تعذّر فتح موقع المطور", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
            bottomMargin = dp(16)
        })
        panel.addView(arabicText(
            "المس الشاشة أو اضغط زر رفع الصوت أو خفضه لزيادة العداد. كل ٣٣ تسبيحة تكمل دورة واحدة.",
            16f,
            0xFFD6E4EB.toInt()
        ))
        panel.addView(arabicText(
            "يمكنك كتم صوت النقرة من زر الصوت. عند مغادرة المسبحة يعود الزران للتحكم بالصوت.",
            15f,
            0xFFA2ADC2.toInt()
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val dialog = createPanelDialog(panel)
        panel.addView(actionButton("تم", selected = true).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        dialog.show()
    }

    private fun showDhikrPicker() {
        val panel = dialogPanel("اختر الأذكار")
        val dialog = createPanelDialog(panel)
        val categories = listOf(
            "أذكار الصباح والمساء" to listOf(Dhikr.PRAISE, Dhikr.PROTECTION),
            "أذكار بعد الصلاة" to listOf(
                Dhikr.FORGIVENESS, Dhikr.PEACE, Dhikr.GLORY, Dhikr.THANKS, Dhikr.GREATNESS, Dhikr.ONENESS
            )
        )
        val tabs = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        panel.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        panel.addView(scroll, LinearLayout.LayoutParams(
            -1, minOf(dp(260), resources.displayMetrics.heightPixels / 3)
        ).apply { topMargin = dp(8) })

        val tabButtons = categories.map { (label, _) ->
            actionButton(label).also {
                tabs.addView(it, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                })
            }
        }
        fun showCategory(index: Int) {
            tabButtons.forEachIndexed { i, tab ->
                tab.isSelected = i == index
                tab.background = actionBackground(tab.isSelected)
            }
            rows.removeAllViews()
            categories[index].second.forEach { dhikr ->
                rows.addView(actionButton(dhikr.words, dhikr == selectedDhikr).apply {
                    textSize = 17f
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        selectDhikr(dhikr)
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            scroll.scrollTo(0, 0)
        }
        tabButtons.forEachIndexed { index, tab -> tab.setOnClickListener { showCategory(index) } }
        showCategory(categories.indexOfFirst { (_, entries) ->
            entries.any { it == selectedDhikr }
        }.coerceAtLeast(0))

        val actions = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        if (selectedDhikr != null) {
            actions.addView(actionButton("إلغاء اختيار الذكر").apply {
                setOnClickListener {
                    selectDhikr(null)
                    dialog.dismiss()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        actions.addView(actionButton("إغلاق").apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        dialog.show()
    }

    private fun dialogPanel(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF242548.toInt(), 0xFF11182B.toInt())
        ).apply { cornerRadius = dp(20).toFloat() }
        addView(arabicText(title, 21f, 0xFFB7F1E8.toInt()), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })
    }

    private fun createPanelDialog(panel: LinearLayout) =
        AlertDialog.Builder(this).setView(panel).create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

    private fun arabicText(label: String, size: Float, color: Int) = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(color)
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.START
    }

    private fun actionButton(label: String, selected: Boolean = false) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        isSelected = selected
        minHeight = dp(48)
        setPadding(dp(9), dp(6), dp(9), dp(6))
        setTextColor(0xFFE6F5F5.toInt())
        background = actionBackground(selected)
    }

    private fun actionBackground(selected: Boolean) = RippleDrawable(
        ColorStateList.valueOf(0x3385E1D5),
        GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (selected) 0xFF215457.toInt() else 0xFF1C263D.toInt())
            setStroke(dp(1), if (selected) 0xFF85E1D5.toInt() else 0xFF394362.toInt())
        },
        null
    )

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
        val next = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, next).apply()
        refreshCount()
    }

    private fun countTap() {
        incrementCount()
        canvasView.playTapRipple()
        if (soundEnabled) clickTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
    }

    private fun refreshCount() {
        val count = prefs.getInt(KEY_COUNT, 0)
        canvasView.count = count
        cycleLabel.text = "الدورة: ${count / TasbihCanvasView.BEADS_PER_CYCLE}"
    }

    override fun onResume() {
        super.onResume()
        clickTone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()
        refreshCount()
    }

    override fun onPause() {
        clickTone?.release()
        clickTone = null
        super.onPause()
    }
}
