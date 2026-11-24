package com.nuruzzaman.earthorbit

import android.app.Activity
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.min

/** A live, animated Earth with date and time controls, and a compact list for adding widgets. */
class MainActivity : Activity() {

    private lateinit var earth: EarthView
    private lateinit var stamp: TextView
    private lateinit var modeText: TextView
    private lateinit var playChip: TextView
    private lateinit var speedChips: List<TextView>
    private val stampFormat = SimpleDateFormat("EEE, d MMM yyyy  HH:mm:ss", Locale.getDefault())
    private var speedIndex = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    private val speeds = listOf(
        "1 day/s" to 86_400_000L,
        "1 week/s" to 604_800_000L,
        "1 month/s" to 2_629_800_000L
    )

    private val shapeInfo = listOf(
        Triple(Shape.ORBIT, "Orbit", "Whole orbit, 2×2"),
        Triple(Shape.GLOBE, "Globe", "Round Earth, 1×1"),
        Triple(Shape.WIDE, "Wide", "Orbit and date, 3×1"),
        Triple(Shape.STRIP, "Strip", "Earth and date, 2×1")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(root)
        })

        // Header: name, then the timestamp
        root.addView(TextView(this).apply {
            text = "Earth Orbit"
            textSize = 20f
            setTextColor(Ui.INK)
        })
        stamp = TextView(this).apply {
            textSize = 15f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Ui.INK)
            setPadding(0, dp(2), 0, 0)
        }
        root.addView(stamp)
        modeText = TextView(this).apply {
            textSize = 12f
            setTextColor(Ui.ACCENT)
        }
        root.addView(modeText)

        // The live Earth
        val side = min(resources.displayMetrics.widthPixels - dp(32), dp(380))
        earth = EarthView(this)
        root.addView(earth, LinearLayout.LayoutParams(side, side).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
        })
        earth.onTime = { ms ->
            stamp.text = stampFormat.format(Date(ms))
            modeText.text = when {
                earth.live -> "LIVE"
                earth.playing -> "TIME-LAPSE  ·  ${speeds[speedIndex].first}"
                else -> "PAUSED ON THIS DATE"
            }
        }

        // Row 1: pick a date, back to now, play
        playChip = Ui.chip(this, "▶ Play") { togglePlay() }
        root.addView(Ui.row(
            this,
            Ui.chip(this, "Pick date") { pickDate() },
            Ui.chip(this, "Now") { goLive() },
            playChip
        ))

        // Row 2: step by year, month, day
        fun step(field: Int, amount: Int): () -> Unit = {
            val cal = GregorianCalendar().apply { timeInMillis = earth.simMs }
            cal.add(field, amount)
            setTime(cal.timeInMillis)
        }
        root.addView(Ui.row(
            this,
            Ui.chip(this, "−1 yr", onClick = step(Calendar.YEAR, -1)),
            Ui.chip(this, "−1 mo", onClick = step(Calendar.MONTH, -1)),
            Ui.chip(this, "−1 day", onClick = step(Calendar.DAY_OF_MONTH, -1)),
            Ui.chip(this, "+1 day", onClick = step(Calendar.DAY_OF_MONTH, 1)),
            Ui.chip(this, "+1 mo", onClick = step(Calendar.MONTH, 1)),
            Ui.chip(this, "+1 yr", onClick = step(Calendar.YEAR, 1))
        ))

        // Row 3: time-lapse speed
        speedChips = speeds.mapIndexed { i, (label, _) ->
            Ui.chip(this, label, i == speedIndex) { setSpeed(i) }
        }
        root.addView(Ui.row(this, *speedChips.toTypedArray()))
        earth.msPerSecond = speeds[speedIndex].second

        // Widgets
        root.addView(Ui.label(this, "ADD A WIDGET TO YOUR HOME SCREEN").apply { setPadding(dp(2), dp(18), 0, dp(4)) })
        val thumbs = HashMap<Shape, ImageView>()
        shapeInfo.forEach { (shape, title, sub) ->
            root.addView(widgetRow(shape, title, sub, thumbs))
        }
        thread {
            val now = System.currentTimeMillis()
            shapeInfo.forEach { (shape, _, _) ->
                val (w, h) = thumbSize(shape)
                val bmp = EarthRenderer.render(
                    applicationContext, shape, Look.SOLID, !shape.squareBitmap, w, h, now,
                    RenderOptions(minDp = 150f)
                )
                runOnUiThread { thumbs.getValue(shape).setImageBitmap(bmp) }
            }
        }

        earth.invalidate()
    }

    private fun thumbSize(shape: Shape): Pair<Int, Int> = when (shape) {
        Shape.ORBIT, Shape.GLOBE -> dp(64) to dp(64)
        Shape.WIDE -> dp(120) to dp(38)
        Shape.STRIP -> dp(96) to dp(45)
    }

    private fun widgetRow(shape: Shape, title: String, sub: String, thumbs: MutableMap<Shape, ImageView>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = Ui.roundedBg(this@MainActivity, Ui.CARD, Ui.CARD, 14)
        }
        val thumb = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        thumbs[shape] = thumb
        row.addView(thumb, LinearLayout.LayoutParams(dp(120), dp(64)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(Ui.INK) })
        texts.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(Ui.MUTED) })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
        row.addView(Ui.chip(this, "Add") { pin(shape) })
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        }
        return row
    }

    // ------------------------------------------------------------ time controls

    private fun setTime(ms: Long) {
        earth.live = false
        earth.playing = false
        earth.simMs = ms
        playChip.text = "▶ Play"
        earth.invalidate()
    }

    private fun goLive() {
        earth.live = true
        earth.playing = false
        playChip.text = "▶ Play"
        earth.invalidate()
    }

    private fun togglePlay() {
        if (earth.live) earth.live = false
        earth.playing = !earth.playing
        playChip.text = if (earth.playing) "⏸ Pause" else "▶ Play"
        earth.invalidate()
    }

    private fun setSpeed(i: Int) {
        speedIndex = i
        earth.msPerSecond = speeds[i].second
        speedChips.forEachIndexed { k, c -> Ui.style(c, k == i) }
        earth.invalidate()
    }

    private fun pickDate() {
        val cal = GregorianCalendar().apply { timeInMillis = earth.simMs }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = GregorianCalendar().apply { timeInMillis = earth.simMs }
                c.set(y, m, d)
                setTime(c.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pin(shape: Shape) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, shape.providerClass), null, null)
        } else {
            Toast.makeText(
                this,
                "Your launcher cannot add widgets from here. Long-press the home screen, open Widgets and pick Earth Orbit.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
