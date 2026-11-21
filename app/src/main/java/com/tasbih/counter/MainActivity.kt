package com.tasbih.counter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private companion object {
        const val KEY_DHIKR = "selected_dhikr"
    }

    private enum class Dhikr(val words: String) {
        GLORY("سبحان الله"),
        THANKS("الحمد لله"),
        GREATNESS("الله أكبر"),
        ONENESS("لا إله إلا الله"),
        FORGIVENESS("استغفر الله")
    }

    private lateinit var canvasView: TasbihCanvasView
    private lateinit var prefs: SharedPreferences
    private lateinit var counter: TasbihCounter
    private lateinit var cycleLabel: TextView
    private lateinit var soundButton: ImageButton
    private lateinit var dhikrText: TextView
    private lateinit var dhikrScroll: ScrollView
    private lateinit var dhikrStrip: HorizontalScrollView
    private lateinit var dhikrButtons: Map<Dhikr, Button>
    private lateinit var dhikrNumber: TextView
    private lateinit var greeting: TextView
    private var tapPoint: PointF? = null
    private var selectedDhikr: Dhikr? = null
    private var clickTone: ToneGenerator? = null
    private var soundEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private val dailyReminder = Runnable { showDailyReminder() }
    private val lockedCounterIntent by lazy { Intent(this, LockedCounterService::class.java) }
    private val stopCounterIfUnlocked = Runnable {
        if (!isScreenLocked()) stopService(lockedCounterIntent)
    }
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var singlePointerSwipe = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        counter = TasbihCounter(this)
        prefs = counter.prefs
        soundEnabled = prefs.getBoolean("click_sound_enabled", true)
        selectedDhikr = Dhikr.values().firstOrNull { it.name == prefs.getString(KEY_DHIKR, null) }

        val rootLayout = FrameLayout(this)

        canvasView = TasbihCanvasView(this).apply {
            enableCounting(this)
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
            background = actionBackground(selected = false)
            setOnClickListener {
                counter.reset()
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
            setColorFilter(0xFF9CAFC8.toInt())
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
        dhikrNumber = statusChip("", 0xFFB6C7DF.toInt())
        val statusRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(cycleLabel)
            addView(dhikrNumber)
        }
        val choices = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        dhikrButtons = Dhikr.values().associateWith { dhikr ->
            actionButton(dhikr.words).apply {
                setSingleLine(true)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener {
                    selectDhikr(dhikr.takeUnless { it == selectedDhikr })
                }
                choices.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                    marginEnd = dp(6)
                })
            }
        }
        dhikrStrip = HorizontalScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            contentDescription = "التسبيح"
            isHorizontalScrollBarEnabled = false
            addView(choices, FrameLayout.LayoutParams(-2, -2))
        }
        greeting = arabicText("", 17f, 0xFFDCE3F4.toInt()).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        updateGreeting()
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), 0)
            addView(toolbar, LinearLayout.LayoutParams(-1, dp(48)))
            addView(greeting, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(dhikrStrip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
            })
        }
        rootLayout.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        dhikrText = arabicText("", 19f, 0xFFD6E4EB.toInt()).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            enableCounting(this)
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
        dhikrNumber.visibility = if (dhikr == null) View.GONE else View.VISIBLE
        dhikrNumber.text = dhikr?.let {
            "الذكر ${"١٢٣٤٥"[it.ordinal]} من ٥"
        }.orEmpty()
        dhikrButtons.forEach { (choice, button) ->
            button.isSelected = choice == dhikr
            button.background = actionBackground(button.isSelected)
        }
        dhikr?.let(dhikrButtons::get)?.let { button ->
            button.post { button.requestRectangleOnScreen(Rect(0, 0, button.width, button.height), !animate) }
        }
    }

    private fun selectDhikr(dhikr: Dhikr?) {
        selectedDhikr = dhikr
        prefs.edit().putString(KEY_DHIKR, dhikr?.name).apply()
        updateDhikrDisplay(animate = true)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                val stripBounds = Rect()
                dhikrStrip.getGlobalVisibleRect(stripBounds)
                singlePointerSwipe = !stripBounds.contains(event.rawX.toInt(), event.rawY.toInt())
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> singlePointerSwipe = false
            MotionEvent.ACTION_UP -> {
                val dx = event.x - swipeStartX
                val dy = event.y - swipeStartY
                if (singlePointerSwipe && abs(dx) >= dp(60) && abs(dx) > abs(dy) * 1.5f) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    val items = Dhikr.values()
                    val next = if (dx < 0) (selectedDhikr?.ordinal ?: -1) + 1
                        else (selectedDhikr?.ordinal ?: items.size) - 1
                    items.getOrNull(next)?.let { selectDhikr(it) }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun updateGreeting() {
        val name = prefs.getString("user_name", "").orEmpty().trim()
        greeting.text = if (name.isEmpty()) "أهلًا وسهلًا بك" else "أهلًا بك، $name"
    }

    private fun showDailyReminder() {
        val today = Calendar.getInstance()
        val day = String.format(Locale.ROOT, "%04d-%03d", today.get(Calendar.YEAR), today.get(Calendar.DAY_OF_YEAR))
        if (prefs.getString("reminder_day", null) != day) {
            val reminders = listOf(
                "كن من الذاكرين الله كثيرًا، واجعل للذكر نصيبًا من يومك.",
                "جدّد صلتك بالله اليوم بذكرٍ يسير تداوم عليه.",
                "مهما انشغلت اليوم، خصّص لحظة تذكر فيها الله."
            )
            val message = reminders[today.get(Calendar.DAY_OF_YEAR) % reminders.size]
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            prefs.edit().putString("reminder_day", day).apply()
        }
        val tomorrow = (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        handler.removeCallbacks(dailyReminder)
        handler.postDelayed(dailyReminder, (tomorrow.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1L))
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = borderedSurface(0xFF111827.toInt(), 0xFF34445B.toInt(), 20)
            isFocusableInTouchMode = true
        }
        val dialog = createPanelDialog(panel)
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(arabicText("إعدادات المسبحة", 21f, 0xFFC2CDE8.toInt()).apply {
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(actionButton("×").apply {
                contentDescription = "إغلاق الإعدادات"
                textSize = 23f
                minWidth = dp(48)
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        panel.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(this).apply { addView(cards) }, LinearLayout.LayoutParams(-1, -2).apply {
            weight = 1f
        })

        val profile = settingsCard(cards, "اسمك")
        profile.addView(EditText(this).apply {
            hint = "أدخل اسمك"
            textSize = 16f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(40))
            setTextColor(0xFFE2E9F7.toInt())
            setHintTextColor(0xFFA1ADC2.toInt())
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = borderedSurface(0xFF131D30.toInt(), 0xFF414F68.toInt(), 10)
            setText(prefs.getString("user_name", ""))
            doAfterTextChanged {
                prefs.edit().putString("user_name", it.toString().trim()).apply()
                updateGreeting()
            }
        }, LinearLayout.LayoutParams(-1, -2))

        val cycleCard = settingsCard(cards, "عدد التسبيحات في الدورة")
        val cycleOptions = LinearLayout(this)
        val cycleButtons = listOf(33, 100).associateWith { size ->
            actionButton(if (size == 33) "٣٣" else "١٠٠", size == counter.cycleSize).also {
                cycleOptions.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                })
            }
        }
        cycleButtons.forEach { (size, button) ->
            button.setOnClickListener {
                counter.cycleSize = size
                cycleButtons.forEach { (value, option) ->
                    option.isSelected = value == size
                    option.background = actionBackground(option.isSelected)
                }
                refreshCount()
            }
        }
        cycleCard.addView(cycleOptions, LinearLayout.LayoutParams(-1, -2))

        settingsCard(cards, "طريقة التسبيح").addView(arabicText(
            "انقر للعد. اسحب لليسار للذكر التالي أو لليمين للذكر السابق. يمكنك العد بأزرار السماعة عند إطفاء الشاشة من داخل المسبحة.",
            15f, 0xFFB8C4D8.toInt()
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) })

        val developer = settingsCard(cards, "المطور")
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        developer.addView(arabicText("رقم الإصدار $versionName", 14f, 0xFFB8C4D8.toInt()))
        developer.addView(actionButton("زيارة موقع المطور").apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sameerlab.com")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "تعذّر فتح موقع المطور", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        panel.addView(actionButton("تم", selected = true).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        dialog.show()
    }

    private fun settingsCard(parent: LinearLayout, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = borderedSurface(0xFF1A2435.toInt(), 0xFF2E3A51.toInt(), 14)
        addView(arabicText(title, 16f, 0xFFCBD2EF.toInt()).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun borderedSurface(fill: Int, border: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(1), border)
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
        ColorStateList.valueOf(0x269EAED6),
        borderedSurface(
            if (selected) 0xFF364566.toInt() else 0xFF1C263D.toInt(),
            if (selected) 0xFF7687AD.toInt() else 0xFF394362.toInt(),
            12
        ),
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
        counter.increment()
        refreshCount()
    }

    private fun enableCounting(view: View) {
        view.isSoundEffectsEnabled = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> tapPoint = null
                MotionEvent.ACTION_UP -> {
                    val origin = IntArray(2)
                    canvasView.getLocationOnScreen(origin)
                    tapPoint = PointF(event.rawX - origin[0], event.rawY - origin[1])
                }
            }
            false
        }
        view.setOnClickListener {
            incrementCount()
            val point = tapPoint
            tapPoint = null
            if (point != null) canvasView.playTapRipple(point.x, point.y)
            if (soundEnabled) clickTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
        }
    }

    private fun refreshCount() {
        canvasView.cycleSize = counter.cycleSize
        canvasView.count = counter.count
        cycleLabel.text = "الدورات: ${counter.count / counter.cycleSize} · ${counter.count % counter.cycleSize}/${counter.cycleSize}"
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(stopCounterIfUnlocked)
        LockedCounterService.activityVisible = true
        ContextCompat.startForegroundService(this, lockedCounterIntent)
        clickTone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()
        refreshCount()
        showDailyReminder()
    }

    override fun onPause() {
        LockedCounterService.activityVisible = false
        handler.postDelayed(stopCounterIfUnlocked, 250L)
        handler.removeCallbacks(dailyReminder)
        clickTone?.release()
        clickTone = null
        super.onPause()
    }

    override fun onUserLeaveHint() {
        stopService(lockedCounterIntent)
        super.onUserLeaveHint()
    }
}
