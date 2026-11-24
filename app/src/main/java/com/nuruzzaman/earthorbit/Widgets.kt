package com.nuruzzaman.earthorbit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/** Draws a widget's picture and sends it to the home screen. */
object WidgetUpdater {
    const val ACTION_REFRESH = "com.nuruzzaman.earthorbit.REFRESH"

    /** Largest picture handed to the home screen, to stay within its memory limit for widget images. */
    private const val MAX_SIDE_PX = 800
    private const val MAX_PIXELS = 640 * 640

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, shape: Shape) {
        val look = WidgetPrefs.look(ctx, id)
        val info = WidgetPrefs.showInfo(ctx, id)
        val (w, h) = sizePx(ctx, mgr, id, shape)
        val bmp = EarthRenderer.render(ctx, shape, look, info, w, h, System.currentTimeMillis())

        val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
        rv.setImageViewBitmap(R.id.image, bmp)
        val st = OrbitMath.state(System.currentTimeMillis())
        rv.setContentDescription(
            R.id.image,
            String.format("Earth is %.1f million kilometres from the Sun. Tap to refresh.", st.distanceMkm)
        )
        val tap = Intent(ctx, shape.providerClass).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(ctx, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.root, pi)
        mgr.updateAppWidget(id, rv)
    }

    private fun sizePx(ctx: Context, mgr: AppWidgetManager, id: Int, shape: Shape): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        var wDp = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        var hDp = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (wDp <= 0) wDp = shape.defaultDp.first
        if (hDp <= 0) hDp = shape.defaultDp.second
        val density = ctx.resources.displayMetrics.density
        var w = wDp * density
        var h = hDp * density
        if (shape.squareBitmap) {
            val s = min(w, h)
            w = s; h = s
        }
        val scale = minOf(1f, MAX_SIDE_PX / max(w, h), sqrtScale(w * h))
        return max(1, (w * scale).toInt()) to max(1, (h * scale).toInt())
    }

    private fun sqrtScale(pixels: Float): Float =
        if (pixels <= MAX_PIXELS) 1f else kotlin.math.sqrt(MAX_PIXELS / pixels)
}

abstract class BaseWidgetProvider(private val shape: Shape) : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(context, mgr, ids)

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle?) =
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
