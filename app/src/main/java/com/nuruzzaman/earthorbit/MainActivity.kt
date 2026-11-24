package com.nuruzzaman.earthorbit

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** Shows the four shapes and lets you add each one to the home screen. */
class MainActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val names = mapOf(
        Shape.ORBIT to ("Orbit" to "Square. The whole orbit with a month ring."),
        Shape.GLOBE to ("Globe" to "Round. A close-up of Earth with real day and night."),
        Shape.WIDE to ("Wide" to "Orbit on the left, date and numbers on the right."),
        Shape.STRIP to ("Strip" to "One row with a small Earth, the date and the distance.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B1020.toInt()
        window.navigationBarColor = 0xFF0B1020.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0B1020.toInt())
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "Earth Orbit"
            textSize = 28f
            setTextColor(0xFFE9EEFB.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Pick a shape and tap Add to home screen. You can also long-press an empty spot on your home screen, open Widgets and look for Earth Orbit. Each widget can be Solid, Glass or Transparent."
            textSize = 14f
            setTextColor(0xFF8693B3.toInt())
            setPadding(0, dp(8), 0, dp(20))
        })

        val previews = HashMap<Shape, ImageView>()
        Shape.values().forEach { shape ->
            val (title, sub) = names.getValue(shape)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    setColor(0xFF121A30.toInt())
                    cornerRadius = dp(20).toFloat()
                }
            }
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            previews[shape] = img
            val side = if (shape.squareBitmap) dp(200) else resources.displayMetrics.widthPixels - dp(40) - dp(32)
            val h = if (shape.squareBitmap) side else (side * shape.defaultDp.second / shape.defaultDp.first)
            card.addView(img, LinearLayout.LayoutParams(side, h).apply { gravity = Gravity.CENTER_HORIZONTAL })
            card.addView(TextView(this).apply {
                text = title
                textSize = 18f
                setTextColor(0xFFE9EEFB.toInt())
                setPadding(0, dp(12), 0, 0)
            })
            card.addView(TextView(this).apply {
                text = sub
                textSize = 13f
                setTextColor(0xFF8693B3.toInt())
                setPadding(0, dp(2), 0, dp(8))
            })
            card.addView(Button(this).apply {
                text = "Add to home screen"
                setOnClickListener { pin(shape) }
            })
            root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            })
        }

        thread {
            val now = System.currentTimeMillis()
            Shape.values().forEach { shape ->
                val side = if (shape.squareBitmap) dp(200) else resources.displayMetrics.widthPixels - dp(40) - dp(32)
                val h = if (shape.squareBitmap) side else (side * shape.defaultDp.second / shape.defaultDp.first)
                val bmp = EarthRenderer.render(applicationContext, shape, Look.SOLID, true, side, h, now)
                runOnUiThread { previews.getValue(shape).setImageBitmap(bmp) }
            }
        }
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
