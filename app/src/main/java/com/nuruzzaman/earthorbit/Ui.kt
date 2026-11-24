package com.nuruzzaman.earthorbit

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for the compact, code-built screens. */
object Ui {
    const val BG = 0xFF0B1020.toInt()
    const val CARD = 0xFF121A30.toInt()
    const val INK = 0xFFE9EEFB.toInt()
    const val MUTED = 0xFF8693B3.toInt()
    const val ACCENT = 0xFF5AAAFF.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun roundedBg(ctx: Context, fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(ctx, 1), stroke)
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    /** A small tappable pill. Call [setSelected] to show it as chosen. */
    fun chip(ctx: Context, text: String, selected: Boolean = false, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7))
            isClickable = true
            setOnClickListener { onClick() }
            style(this, selected)
        }

    fun style(chip: TextView, selected: Boolean) {
        val ctx = chip.context
        chip.setTextColor(INK)
        chip.background = if (selected) roundedBg(ctx, 0xFF1E3A6B.toInt(), ACCENT, 16)
        else roundedBg(ctx, CARD, 0xFF2A3658.toInt(), 16)
    }

    /** A horizontal row whose chips share the width equally. */
    fun row(ctx: Context, vararg chips: TextView): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            chips.forEach { c ->
                addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(ctx, 2), dp(ctx, 3), dp(ctx, 2), dp(ctx, 3))
                })
            }
        }

    fun label(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(ctx, 2), dp(ctx, 10), 0, dp(ctx, 2))
        }
}
