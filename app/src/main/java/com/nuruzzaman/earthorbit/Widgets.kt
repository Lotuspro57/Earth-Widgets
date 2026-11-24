package com.nuruzzaman.earthorbit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Draws a widget's picture(s) and sends them to the home screen. */
object WidgetUpdater {
    const val ACTION_REFRESH = "com.nuruzzaman.earthorbit.REFRESH"

    /** Pictures in one time-lapse loop. */
    private const val FRAMES = 12
    private const val HOUR_MS = 3_600_000L
    private const val MONTH_MS = 2_629_800_000L

    /** Largest side of a still picture, and pixel budgets, to stay within the home screen's memory limit for widget images. */
    private const val MAX_SIDE_PX = 800f
    private const val STILL_PIXELS = 640f * 640f
    private const val FRAME_PIXELS = 120_000f

    private val FRAME_IDS = intArrayOf(
        R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5,
        R.id.f6, R.id.f7, R.id.f8, R.id.f9, R.id.f10, R.id.f11
    )

    private class Size(val w: Int, val h: Int, val wDp: Float, val hDp: Float) {
        val minDp: Float get() = min(wDp, hDp)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, shape: Shape) {
        val s = WidgetPrefs.get(ctx, id)
        val now = System.currentTimeMillis()
        val base = if (s.live) now else s.fixedMs
        val size = size(ctx, mgr, id, shape, s.animate)
        val density = ctx.resources.displayMetrics.density

        val rv: RemoteViews
        if (s.animate) {
            // A loop of 12 pictures that the home screen flips through by itself.
            rv = RemoteViews(ctx.packageName, R.layout.widget_flip)
            for (i in 0 until FRAMES) {
                val t = if (shape.loopsOverDay) base + i * 2 * HOUR_MS else base + i * MONTH_MS
                val opt = RenderOptions(
                    drawTime = shape.loopsOverDay,
                    spin = if (shape.loopsOverDay) null else i * 1.1,
                    minDp = size.minDp
                )
                val bmp = EarthRenderer.render(ctx, shape, s.look, s.showInfo, size.w, size.h, t, opt)
                rv.setImageViewBitmap(FRAME_IDS[i], bmp)
            }
        } else {
            val info = EarthRenderer.infoShown(shape, s.showInfo, size.wDp, size.hDp, size.minDp)
            val showClock = s.live && info
            val reserve = if (showClock && !shape.squareBitmap) 46f * density else 0f
            rv = RemoteViews(ctx.packageName, if (shape == Shape.GLOBE) R.layout.widget_bottom else R.layout.widget_corner)
            val bmp = EarthRenderer.render(
                ctx, shape, s.look, s.showInfo, size.w, size.h, base,
                RenderOptions(minDp = size.minDp, clockReservePx = reserve)
            )
            rv.setImageViewBitmap(R.id.image, bmp)
            rv.setViewVisibility(R.id.clock, if (showClock) View.VISIBLE else View.GONE)
            val st = OrbitMath.state(base)
            rv.setContentDescription(
                R.id.image,
                String.format("Earth is %.1f million kilometres from the Sun. Tap to refresh.", st.distanceMkm)
            )
        }

        val tap = Intent(ctx, shape.providerClass).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(ctx, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.root, pi)
        mgr.updateAppWidget(id, rv)
    }

    private fun size(ctx: Context, mgr: AppWidgetManager, id: Int, shape: Shape, frames: Boolean): Size {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        var wDp = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)).toFloat()
        var hDp = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)).toFloat()
        if (wDp <= 0f) wDp = shape.defaultDp.first.toFloat()
        if (hDp <= 0f) hDp = shape.defaultDp.second.toFloat()
        if (shape.squareBitmap) {
            val sq = min(wDp, hDp)
            wDp = sq; hDp = sq
        }
        val density = ctx.resources.displayMetrics.density
        val w = wDp * density
        val h = hDp * density
        val budget = if (frames) FRAME_PIXELS else STILL_PIXELS
        val scale = minOf(1f, MAX_SIDE_PX / max(w, h), sqrt(budget / (w * h)))
        return Size(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), wDp, hDp)
    }
}

abstract class BaseWidgetProvider(private val shape: Shape) : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(context, mgr, ids)

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) =
        refresh(context, mgr, intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val mgr = AppWidgetManager.getInstance(context)
                refresh(context, mgr, mgr.getAppWidgetIds(ComponentName(context, shape.providerClass)))
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun refresh(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                ids.forEach { WidgetUpdater.update(app, mgr, it, shape) }
            } finally {
                pending?.finish()
            }
        }
    }
}

class OrbitWidget : BaseWidgetProvider(Shape.ORBIT)
class GlobeWidget : BaseWidgetProvider(Shape.GLOBE)
class WideWidget : BaseWidgetProvider(Shape.WIDE)
class StripWidget : BaseWidgetProvider(Shape.STRIP)
