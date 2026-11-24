package com.nuruzzaman.earthorbit

import android.content.Context

/** The four widget shapes. */
enum class Shape(val providerClass: Class<*>, val defaultDp: Pair<Int, Int>, val squareBitmap: Boolean) {
    ORBIT(OrbitWidget::class.java, 220 to 220, true),
    GLOBE(GlobeWidget::class.java, 150 to 150, true),
    WIDE(WideWidget::class.java, 300 to 130, false),
    STRIP(StripWidget::class.java, 300 to 60, false);

    companion object {
        fun fromClassName(name: String?): Shape =
            values().firstOrNull { it.providerClass.name == name } ?: ORBIT
    }
}

/** How the widget background looks. */
enum class Look(val label: String) {
    SOLID("Solid"),
    GLASS("Glass"),
    TRANSPARENT("Transparent")
}

/** Per-widget settings, saved by appWidgetId so each widget on the screen can look different. */
object WidgetPrefs {
    private const val FILE = "earth_widgets"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun look(ctx: Context, id: Int): Look =
        Look.values().getOrNull(sp(ctx).getInt("look_$id", Look.SOLID.ordinal)) ?: Look.SOLID

    fun showInfo(ctx: Context, id: Int): Boolean = sp(ctx).getBoolean("info_$id", true)

    fun save(ctx: Context, id: Int, look: Look, showInfo: Boolean) {
        sp(ctx).edit().putInt("look_$id", look.ordinal).putBoolean("info_$id", showInfo).apply()
    }

    fun remove(ctx: Context, id: Int) {
        sp(ctx).edit().remove("look_$id").remove("info_$id").apply()
    }
}
