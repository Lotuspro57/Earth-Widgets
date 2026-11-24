package com.nuruzzaman.earthorbit

import android.app.Activity
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.min

/** Opens when a widget is added or reconfigured. Compact: pick a look, a date, and whether it animates. */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var shape: Shape
    private var look = Look.SOLID
    private var showInfo = true
    private var fixedMs = 0L // 0 = live
    private var animate = false

    private lateinit var preview: ImageView
    private lateinit var lookChips: List<TextView>
    private lateinit var todayChip: TextView
    private lateinit var pickChip: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var renderToken = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val mgr = AppWidgetManager.getInstance(this)
        shape = Shape.fromClassName(mgr.getAppWidgetInfo(widgetId)?.provider?.className)
        val saved = WidgetPrefs.get(this, widgetId)
        look = saved.look
        showInfo = saved.showInfo
        fixedMs = saved.fixedMs
        animate = saved.animate

        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "Widget settings"
            textSize = 18f
            setTextColor(Ui.INK)
        })

        // A wallpaper-like backdrop so Glass and Transparent can be judged.
        val backdrop = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF9DB6C9.toInt(), 0xFF5F7E99.toInt(), 0xFF2F4A63.toInt())
            ).apply { cornerRadius = dp(14).toFloat() }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val (pw, ph) = previewBox()
        backdrop.addView(preview, FrameLayout.LayoutParams(pw, ph, Gravity.CENTER_HORIZONTAL))
        root.addView(backdrop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        // Look
        root.addView(Ui.label(this, "LOOK"))
        lookChips = Look.values().map { l ->
            Ui.chip(this, l.label, l == look) {
                look = l
                refreshChips()
                redraw()
            }
        }
        root.addView(Ui.row(this, *lookChips.toTypedArray()))

        // Date
        root.addView(Ui.label(this, "DATE"))
        todayChip = Ui.chip(this, "Today (live)", fixedMs == 0L) {
            fixedMs = 0L
            refreshChips()
            redraw()
        }
        pickChip = Ui.chip(this, "Pick a date", fixedMs != 0L) { pickDate() }
        root.addView(Ui.row(this, todayChip, pickChip))

        // Switches
        root.addView(Switch(this).apply {
            text = "Show date and details"
            textSize = 14f
            setTextColor(Ui.INK)
            isChecked = showInfo
            setPadding(0, dp(10), 0, dp(4))
            setOnCheckedChangeListener { _, on -> showInfo = on; redraw() }
        })
        root.addView(Switch(this).apply {
            text = "Time-lapse animation"
            textSize = 14f
            setTextColor(Ui.INK)
            isChecked = animate
            setPadding(0, dp(4), 0, dp(2))
            setOnCheckedChangeListener { _, on -> animate = on }
        })
        val hint = (if (shape.loopsOverDay) {
            "Loops through one day: Earth spins and the time moves on."
        } else {
            "Loops through one year: Earth circles the Sun."
        }) + " The preview shows one frame. Very small widgets hide the text."
        root.addView(TextView(this).apply {
            text = hint
            textSize = 12f
            setTextColor(Ui.MUTED)
            setPadding(0, 0, 0, dp(10))
        })

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { save(mgr) }
        })

        refreshChips()
        redraw()
    }

    private fun previewBox(): Pair<Int, Int> {
        val maxW = resources.displayMetrics.widthPixels - dp(32) - dp(20)
        val (wd, hd) = shape.defaultDp
        return if (shape.squareBitmap) {
            val s = min(maxW, dp(200))
            s to s
        } else {
            val w = min(maxW, dp(320))
            w to (w * hd / wd)
        }
    }

    private fun refreshChips() {
        Look.values().forEachIndexed { i, l -> Ui.style(lookChips[i], l == look) }
        Ui.style(todayChip, fixedMs == 0L)
        Ui.style(pickChip, fixedMs != 0L)
        pickChip.text = if (fixedMs != 0L) {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(fixedMs))
        } else {
            "Pick a date"
        }
    }

    private fun pickDate() {
        val cal = GregorianCalendar().apply { timeInMillis = if (fixedMs != 0L) fixedMs else System.currentTimeMillis() }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = GregorianCalendar()
                c.set(y, m, d, 12, 0, 0)
                fixedMs = c.timeInMillis
                refreshChips()
                redraw()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun redraw() {
        val token = ++renderToken
        val (w, h) = previewBox()
        val chosenLook = look
        val info = showInfo
        val at = if (fixedMs != 0L) fixedMs else System.currentTimeMillis()
        thread {
            val bmp = EarthRenderer.render(applicationContext, shape, chosenLook, info, w, h, at, RenderOptions(minDp = 150f))
            ui.post { if (token == renderToken && !isFinishing) preview.setImageBitmap(bmp) }
        }
    }

    private fun save(mgr: AppWidgetManager) {
        WidgetPrefs.save(this, widgetId, WidgetSettings(look, showInfo, fixedMs, animate))
        val id = widgetId
        val app = applicationContext
        thread {
            WidgetUpdater.update(app, mgr, id, shape)
            ui.post {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        }
    }
}
