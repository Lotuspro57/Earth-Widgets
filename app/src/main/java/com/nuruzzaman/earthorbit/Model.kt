package com.nuruzzaman.earthorbit

import android.content.Context

/** The four widget shapes. defaultDp is the size used until the home screen reports the real one. */
enum class Shape(val providerClass: Class<*>, val defaultDp: Pair<Int, Int>, val squareBitmap: Boolean) {
    ORBIT(OrbitWidget::class.java, 150 to 150, true),
    GLOBE(GlobeWidget::class.java, 70 to 70, true),
    WIDE(WideWidget::class.java, 220 to 70, false),
    STRIP(StripWidget::class.java, 150 to 70, false);

    /** True when the time-lapse loops over one day (Earth spins) instead of one year (Earth circles the Sun). */
    val loopsOverDay: Boolean get() = this == GLOBE || this == STRIP

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

/**
 * Settings of one widget.
 * fixedMs = 0 means live (today and now); any other value is the chosen date.
 * animate turns on the looping time-lapse.
 */
class WidgetSettings(
    val look: Look,
    val showInfo: Boolean,
    val fixedMs: Long,
    val animate: Boolean
) {
    val live: Boolean get() = fixedMs == 0L
}

/** Per-widget settings, saved by appWidgetId so each widget on the screen can look different. */
object WidgetPrefs {
    private const val FILE = "earth_widgets"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(ctx: Context, id: Int): WidgetSettings {
        val p = sp(ctx)
        val look = Look.values().getOrNull(p.getInt("look_$id", Look.SOLID.ordinal)) ?: Look.SOLID
        return WidgetSettings(
            look = look,
            showInfo = p.getBoolean("info_$id", true),
            fixedMs = p.getLong("fixed_$id", 0L),
            animate = p.getBoolean("anim_$id", false)
        )
    }

    fun save(ctx: Context, id: Int, s: WidgetSettings) {
        sp(ctx).edit()
            .putInt("look_$id", s.look.ordinal)
            .putBoolean("info_$id", s.showInfo)
            .putLong("fixed_$id", s.fixedMs)
            .putBoolean("anim_$id", s.animate)
            .apply()
    }

    fun remove(ctx: Context, id: Int) {
        sp(ctx).edit().remove("look_$id").remove("info_$id").remove("fixed_$id").remove("anim_$id").apply()
    }
}
