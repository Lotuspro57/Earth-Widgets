package com.nuruzzaman.earthorbit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** The live, animated orbit shown in the app. Time can follow the clock, be paused on a date, or run fast. */
class EarthView(context: Context) : View(context) {

    /** The moment being shown. */
    var simMs: Long = System.currentTimeMillis()

    /** Follow the real clock. */
    var live = true

    /** Run time forward at [msPerSecond] per real second. */
    var playing = false
    var msPerSecond = 86_400_000L

    /** Called about once a second (and on every date change) with the moment shown. */
    var onTime: ((Long) -> Unit)? = null

    private var lastUptime = SystemClock.uptimeMillis()
    private var spin = 0.0
    private var lastSecond = -1L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height)
        if (side <= 0) return

        val up = SystemClock.uptimeMillis()
        val dt = min(up - lastUptime, 100L) / 1000.0
        lastUptime = up

        if (live) {
            simMs = System.currentTimeMillis()
        } else if (playing) {
            simMs += (dt * msPerSecond).toLong()
            spin += dt * 0.35
        }

        val rs = min(side, 480)
        val density = resources.displayMetrics.density
        val bmp = EarthRenderer.render(
            context, Shape.ORBIT, Look.SOLID, true, rs, rs, simMs,
            RenderOptions(spin = if (playing) spin else null, minDp = rs / density)
        )
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        dst.set(left, top, left + side, top + side)
        canvas.drawBitmap(bmp, null, dst, paint)
        bmp.recycle()

        val sec = simMs / 1000
        if (sec != lastSecond) {
            lastSecond = sec
            val t = simMs
            post { onTime?.invoke(t) }
        }

        if (playing && !live) postInvalidateOnAnimation()
        else if (live) postInvalidateDelayed(1000)
    }
}
