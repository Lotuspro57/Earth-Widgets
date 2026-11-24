package com.nuruzzaman.earthorbit

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.concurrent.thread

/** Opens when a widget is added or reconfigured: pick Solid, Glass or Transparent and see it live. */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var shape: Shape
    private var look = Look.SOLID
    private var showInfo = true
    private lateinit var preview: ImageView
    private val ui = Handler(Looper.getMainLooper())
    private var renderToken = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        look = WidgetPrefs.look(this, widgetId)
        showInfo = WidgetPrefs.showInfo(this, widgetId)

        window.statusBarColor = 0xFF0B1020.toInt()
        window.navigationBarColor = 0xFF0B1020.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0B1020.toInt())
            addView(root)
        }
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Choose a look"
            textSize = 22f
            setTextColor(0xFFE9EEFB.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Transparent shows your wallpaper through the widget."
            textSize = 14f
            setTextColor(0xFF8693B3.toInt())
            setPadding(0, dp(4), 0, dp(16))
        })

        // A wallpaper-like backdrop so Glass and Transparent can be judged.
        val backdrop = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF9DB6C9.toInt(), 0xFF5F7E99.toInt(), 0xFF2F4A63.toInt())
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val (pw, ph) = previewBox()
        backdrop.addView(preview, FrameLayout.LayoutParams(pw, ph, Gravity.CENTER_HORIZONTAL))
        root.addView(backdrop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        Look.values().forEach { l ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = l.label
                textSize = 16f
                setTextColor(0xFFE9EEFB.toInt())
                isChecked = l == look
                tag = l
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            look = g.findViewById<RadioButton>(checkedId).tag as Look
            redraw()
        }
        root.addView(group)

        root.addView(Switch(this).apply {
            text = "Show date and details"
            textSize = 16f
            setTextColor(0xFFE9EEFB.toInt())
            isChecked = showInfo
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, on -> showInfo = on; redraw() }
        })

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { save(mgr) }
        })

        redraw()
    }

    /** Preview size in pixels, matching the shape's proportions. */
    private fun previewBox(): Pair<Int, Int> {
        val maxW = resources.displayMetrics.widthPixels - dp(40) - dp(32)
        val (wd, hd) = shape.defaultDp
        val ratio = if (shape.squareBitmap) 1f else wd.toFloat() / hd
        val w = if (shape.squareBitmap) minOf(maxW, dp(280)) else maxW
        return w to (w / ratio).toInt()
    }

    private fun redraw() {
        val token = ++renderToken
        val (w, h) = previewBox()
        thread {
            val bmp: Bitmap = EarthRenderer.render(applicationContext, shape, look, showInfo, w, h, System.currentTimeMillis())
            ui.post { if (token == renderToken && !isFinishing) preview.setImageBitmap(bmp) }
        }
    }

    private fun save(mgr: AppWidgetManager) {
        WidgetPrefs.save(this, widgetId, look, showInfo)
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
