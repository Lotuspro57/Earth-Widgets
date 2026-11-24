package com.nuruzzaman.earthorbit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Equirectangular Earth maps made by tools/make_textures.py (1024 x 512, row 0 = 90 N, column 0 = 180 W). */
private class Textures(val color: IntArray, val aux: IntArray, val w: Int, val h: Int)

/** A camera looking at Earth. Each vector is given in the ecliptic frame (x, y in the orbit plane, z = north). */
private class Basis(val r: DoubleArray, val u: DoubleArray, val c: DoubleArray)

object EarthRenderer {
    private const val TAU = 2 * PI
    private val SIN_E = OrbitMath.SIN_E
    private val COS_E = OrbitMath.COS_E

    private val POLAR = Basis(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))

    @Volatile
    private var tex: Textures? = null

    private fun textures(ctx: Context): Textures {
        tex?.let { return it }
        synchronized(this) {
            tex?.let { return it }
            fun read(name: String): Triple<IntArray, Int, Int> {
                val bmp = ctx.applicationContext.assets.open(name).use { BitmapFactory.decodeStream(it) }
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val w = bmp.width
                val h = bmp.height
                bmp.recycle()
                return Triple(px, w, h)
            }
            val c = read("earth_color.png")
            val a = read("earth_aux.png")
            return Textures(c.first, a.first, c.second, c.third).also { tex = it }
        }
    }

    // ---------------------------------------------------------------- small helpers

    private fun sstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun sunVec(L: Double) = doubleArrayOf(-cos(L), -sin(L), 0.0)

    /** Where the Sun is in the sky of Earth's surface: how far Earth has turned, from the UTC clock. */
    private fun spinAngle(L: Double, nowMs: Long): Double {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val utcHours = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0 + cal.get(Calendar.SECOND) / 3600.0
        val s = sunVec(L)
        return atan2(s[1] * COS_E, s[0]) - Math.toRadians((12 - utcHours) * 15)
    }

    /** Camera for the Globe and Strip shapes: 35 degrees off the Sun direction and 20 degrees above the orbit plane. */
    private fun globeView(L: Double): Basis {
        val alpha = Math.toRadians(35.0)
        val beta = Math.toRadians(20.0)
        val s = sunVec(L)
        val perp = doubleArrayOf(-s[1], s[0], 0.0)
        val c = DoubleArray(3) { k ->
            cos(beta) * (cos(alpha) * s[k] + sin(alpha) * perp[k]) + (if (k == 2) sin(beta) else 0.0)
        }
        val cn = sqrt(dot(c, c))
        for (k in 0..2) c[k] /= cn
        val z = doubleArrayOf(0.0, 0.0, 1.0)
        val zc = dot(z, c)
        val u = DoubleArray(3) { z[it] - zc * c[it] }
        val un = sqrt(dot(u, u))
        for (k in 0..2) u[k] /= un
        val r = doubleArrayOf(u[1] * c[2] - u[2] * c[1], u[2] * c[0] - u[0] * c[2], u[0] * c[1] - u[1] * c[0])
        return Basis(r, u, c)
    }

    // ---------------------------------------------------------------- Earth sprite

    private fun channel(p00: Int, p10: Int, p01: Int, p11: Int, shift: Int, w00: Double, w10: Double, w01: Double, w11: Double): Double =
        ((p00 shr shift) and 255) * w00 + ((p10 shr shift) and 255) * w10 +
            ((p01 shr shift) and 255) * w01 + ((p11 shr shift) and 255) * w11

    /** Shades one Earth: terrain, clouds, night lights, sun glint, twilight and atmosphere. */
    private fun earthSprite(ctx: Context, radius: Float, view: Basis, sun: DoubleArray, theta: Double): Bitmap {
        val t = textures(ctx)
        val d0 = ceil(radius * 2.4).toInt()
        val size = if (d0 % 2 == 1) d0 + 1 else d0
        val out = IntArray(size * size)
        val sx = dot(sun, view.r)
        val sy = dot(sun, view.u)
        val sz = dot(sun, view.c)
        val hx = sx
        val hy = sy
        val hz = sz + 1.0
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        val tw = t.w
        val th = t.h
        val center = size / 2.0

        for (j in 0 until size) {
            val y = -((j + 0.5 - center) / radius)
            for (i in 0 until size) {
                val x = (i + 0.5 - center) / radius
                val rr = hypot(x, y)
                if (rr > 1.2) continue

                if (rr > 1.0) {
                    val k = (rr - 1.0) / 0.2
                    val dd = (x / rr) * sx + (y / rr) * sy
                    val lf = sstep(-0.4, 0.65, dd)
                    val a = (1 - k).pow(2.4) * 0.75 * (0.12 + 0.88 * lf)
                    out[j * size + i] = Color.argb((a * 255).toInt().coerceIn(0, 255), 110, 170, 255)
                    continue
                }

                val z = sqrt(max(0.0, 1 - rr * rr))
                val n0 = x * view.r[0] + y * view.u[0] + z * view.c[0]
                val n1 = x * view.r[1] + y * view.u[1] + z * view.c[1]
                val n2 = x * view.r[2] + y * view.u[2] + z * view.c[2]
                val lat = asin((n1 * SIN_E + n2 * COS_E).coerceIn(-1.0, 1.0))
                val phi = atan2(n1 * COS_E - n2 * SIN_E, n0)

                var u = (phi - theta + PI) / TAU
                u -= floor(u)
                val fx = u * tw - 0.5
                val x0f = floor(fx)
                val wx = fx - x0f
                val x0 = ((x0f.toInt() % tw) + tw) % tw
                val x1 = (x0 + 1) % tw
                val fy = ((PI / 2 - lat) / PI * th - 0.5).coerceIn(0.0, th - 1.001)
                val y0 = fy.toInt()
                val wy = fy - y0
                val y1 = y0 + 1
                val w00 = (1 - wx) * (1 - wy)
                val w10 = wx * (1 - wy)
                val w01 = (1 - wx) * wy
                val w11 = wx * wy

                val c00 = t.color[y0 * tw + x0]
                val c10 = t.color[y0 * tw + x1]
                val c01 = t.color[y1 * tw + x0]
                val c11 = t.color[y1 * tw + x1]
                var r = channel(c00, c10, c01, c11, 16, w00, w10, w01, w11)
                var g = channel(c00, c10, c01, c11, 8, w00, w10, w01, w11)
                var b = channel(c00, c10, c01, c11, 0, w00, w10, w01, w11)

                val a00 = t.aux[y0 * tw + x0]
                val a10 = t.aux[y0 * tw + x1]
                val a01 = t.aux[y1 * tw + x0]
                val a11 = t.aux[y1 * tw + x1]
                val cloud = channel(a00, a10, a01, a11, 16, w00, w10, w01, w11) / 255.0 * 0.92
                val lights = channel(a00, a10, a01, a11, 8, w00, w10, w01, w11) / 255.0
                val land = channel(a00, a10, a01, a11, 0, w00, w10, w01, w11)

                r = r * (1 - cloud) + 245 * cloud
                g = g * (1 - cloud) + 248 * cloud
                b = b * (1 - cloud) + 252 * cloud

                val d = x * sx + y * sy + z * sz
                val shade = 0.03 + 1.05 * max(d, 0.0).pow(0.8)
                r *= shade; g *= shade; b *= shade

                val night = 1 - sstep(-0.05, 0.2, d)
                r += 4 * night; g += 8 * night; b += 18 * night

                val twilight = sstep(-0.12, 0.0, d) * (1 - sstep(0.0, 0.16, d)) * 0.07
                r += 255 * twilight; g += 110 * twilight; b += 40 * twilight

                if (lights > 0) {
                    var nf = ((d - 0.04) / -0.18).coerceIn(0.0, 1.0)
                    nf = nf * nf * (3 - 2 * nf) * (1 - cloud * 0.75)
                    val k = lights * nf * 0.95
                    r += 255 * k; g += 196 * k; b += 110 * k
                }

                if (d > 0 && land < 128) {
                    val nh = (x * hx + y * hy + z * hz) / hl
                    if (nh > 0) {
                        val sp = nh.coerceAtMost(1.0).pow(90) * 0.85 * (1 - cloud)
                        r += 255 * sp; g += 250 * sp; b += 240 * sp
                    }
                }

                val rim = (1 - z).pow(3)
                val am = rim * (0.12 + 0.88 * sstep(-0.25, 0.5, d)) * 0.85
                r = r * (1 - am * 0.5) + 110 * am
                g = g * (1 - am * 0.5) + 170 * am
                b = b * (1 - am * 0.5) + 255 * am

                out[j * size + i] = Color.argb(255, r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, size, 0, 0, size, size)
        return bmp
    }

    // ---------------------------------------------------------------- drawing pieces

    private val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun drawEarth(canvas: Canvas, ctx: Context, cx: Float, cy: Float, radius: Float, view: Basis, L: Double, nowMs: Long) {
        val sprite = earthSprite(ctx, radius, view, sunVec(L), spinAngle(L, nowMs))
        canvas.drawBitmap(sprite, cx - sprite.width / 2f, cy - sprite.height / 2f, filter)
        sprite.recycle()
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, sunR: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, sunR * 7,
                intArrayOf(0x8CFFBE5A.toInt(), 0x8CFFBE5A.toInt(), 0x38FF9632, 0x0FFF6E1E, 0x00FF6414),
                floatArrayOf(0f, 0.114f, 0.25f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, sunR * 7, glow)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - sunR * 0.2f, cy - sunR * 0.2f, sunR * 1.25f,
                intArrayOf(0xFFFFF3C4.toInt(), 0xFFFFC04A.toInt(), 0xFFFF8F2A.toInt()),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, sunR, body)
    }

    private fun textPaint(size: Float, bold: Boolean, color: Int, shadow: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        if (shadow) setShadowLayer(size * 0.14f, 0f, size * 0.06f, 0xB4000000.toInt())
    }

    private fun fit(p: Paint, text: String, maxWidth: Float) {
        while (p.measureText(text) > maxWidth && p.textSize > 6f) p.textSize *= 0.94f
    }

    /** The orbit ring, month marks, trailing arc, Sun and a polar-view Earth. */
    private fun drawOrbitScene(
        canvas: Canvas, ctx: Context, cx: Float, cy: Float, size: Float, st: OrbitMath.State,
        nowMs: Long, look: Look, monthLabels: Boolean
    ) {
        val orbitR = size * 0.335f
        val earthR = size * 0.098f
        val sunR = size * 0.058f
        val transparent = look == Look.TRANSPARENT
        val stroke = max(1.2f, size / 260f)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = if (transparent) 0x99FFFFFF.toInt() else 0x5A8CAAE6
            if (transparent) setShadowLayer(stroke * 2, 0f, stroke * 0.5f, 0x80000000.toInt())
        }
        val ring = Path()
        var a = 0
        while (a <= 360) {
            val l = Math.toRadians(a.toDouble())
            val rr = OrbitMath.radiusAt(l)
            val px = (cx + orbitR * rr * cos(l)).toFloat()
            val py = (cy - orbitR * rr * sin(l)).toFloat()
            if (a == 0) ring.moveTo(px, py) else ring.lineTo(px, py)
            a += 3
        }
        ring.close()
        canvas.drawPath(ring, ringPaint)

        if (monthLabels) {
            val names = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
            val year = cal.get(Calendar.YEAR)
            val curMonth = cal.get(Calendar.MONTH)
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
            val label = textPaint(size * 0.034f, false, 0xFF8693B3.toInt(), transparent).apply {
                textAlign = Paint.Align.CENTER
                if (transparent) color = 0xFFFFFFFF.toInt()
            }
            for (m in 0 until 12) {
                cal.clear()
                cal.set(year, m, 1, 0, 0, 0)
                val s = OrbitMath.state(cal.timeInMillis)
                val cl = cos(s.L).toFloat()
                val sl = sin(s.L).toFloat()
                val rt = orbitR * s.r.toFloat()
                tick.color = if (m == curMonth) 0xFFE9EEFB.toInt() else if (transparent) 0xB3FFFFFF.toInt() else 0x668CAAE6
                canvas.drawLine(cx + (rt - size * 0.01f) * cl, cy - (rt - size * 0.01f) * sl, cx + (rt + size * 0.01f) * cl, cy - (rt + size * 0.01f) * sl, tick)
                label.color = if (m == curMonth) 0xFFFFFFFF.toInt() else if (transparent) 0xCCFFFFFF.toInt() else 0xFF8693B3.toInt()
                val lx = cx + (rt + size * 0.052f) * cl
                val ly = cy - (rt + size * 0.052f) * sl + label.textSize * 0.35f
                canvas.drawText(names[m], lx, ly, label)
            }
        }

        // trailing arc behind Earth
        val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = max(2f, size / 170f)
        }
        for (k in 0 until 40) {
            val a0 = st.L - Math.toRadians(k * 1.6)
            val a1 = st.L - Math.toRadians((k + 1) * 1.6)
            val r0 = OrbitMath.radiusAt(a0)
            val r1 = OrbitMath.radiusAt(a1)
            trail.color = Color.argb(((1 - k / 40f) * 0.6f * 255).toInt(), 130, 190, 255)
            canvas.drawLine(
                (cx + orbitR * r0 * cos(a0)).toFloat(), (cy - orbitR * r0 * sin(a0)).toFloat(),
                (cx + orbitR * r1 * cos(a1)).toFloat(), (cy - orbitR * r1 * sin(a1)).toFloat(), trail
            )
        }

        drawSun(canvas, cx, cy, sunR)

        val ex = (cx + orbitR * st.r * cos(st.L)).toFloat()
        val ey = (cy - orbitR * st.r * sin(st.L)).toFloat()
        drawEarth(canvas, ctx, ex, ey, earthR, POLAR, st.L, nowMs)
    }

    // ---------------------------------------------------------------- background and mask

    private fun shapePath(w: Float, h: Float, radius: Float, circle: Boolean, inverse: Boolean): Path {
        val p = Path()
        if (circle) {
            p.addCircle(w / 2f, h / 2f, min(w, h) / 2f, Path.Direction.CW)
        } else {
            p.addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW)
        }
        if (inverse) p.fillType = Path.FillType.INVERSE_WINDING
        return p
    }

    private fun drawStars(canvas: Canvas, w: Int, h: Int, density: Int, maxAlpha: Int, seed: Long) {
        val rnd = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = (w.toLong() * h / density).toInt()
        val unit = min(w, h) / 400f
        for (n in 0 until count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val mag = rnd.nextFloat().pow(3)
            p.color = Color.argb(((0.25f + mag * 0.75f) * maxAlpha).toInt(), 235, 240, 255)
            canvas.drawCircle(x, y, max(0.5f, (0.4f + mag * 1.1f) * unit * 1.4f), p)
        }
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, look: Look, seed: Long) {
        when (look) {
            Look.SOLID -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        w / 2f, h / 2f, max(w, h) * 0.75f,
                        intArrayOf(0xFF0C162E.toInt(), 0xFF010207.toInt()), null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                drawStars(canvas, w, h, 4200, 255, seed)
            }
            Look.GLASS -> {
                canvas.drawColor(0x5C0D1630)
                drawStars(canvas, w, h, 9000, 150, seed)
            }
            Look.TRANSPARENT -> Unit
        }
    }

    // ---------------------------------------------------------------- public entry

    fun render(ctx: Context, shape: Shape, look: Look, showInfo: Boolean, wPx: Int, hPx: Int, nowMs: Long): Bitmap {
        val side = min(wPx, hPx)
        val w = if (shape.squareBitmap) side else wPx
        val h = if (shape.squareBitmap) side else hPx
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val st = OrbitMath.state(nowMs)
        val transparent = look == Look.TRANSPARENT
        val ink = 0xFFE9EEFB.toInt()
        val muted = if (transparent) 0xFFFFFFFF.toInt() else 0xFFB4C0DC.toInt()

        val fw = w.toFloat()
        val fh = h.toFloat()
        val circle = shape == Shape.GLOBE
        val corner = when (shape) {
            Shape.ORBIT -> side * 0.2f
            Shape.GLOBE -> side / 2f
            Shape.WIDE -> fh * 0.2f
            Shape.STRIP -> fh / 2f
        }

        drawBackground(canvas, w, h, look, shape.ordinal * 101L + 7)

        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(nowMs))
        val dist = String.format(Locale.US, "%.1fM km", st.distanceMkm)
        val seasonName = arrayOf("Spring", "Summer", "Autumn", "Winter")[st.season]
        val dayOfYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_YEAR)

        when (shape) {
            Shape.ORBIT -> {
                drawOrbitScene(canvas, ctx, fw / 2, fh / 2, fw, st, nowMs, look, monthLabels = true)
                if (showInfo) {
                    val p1 = textPaint(fw * 0.062f, true, ink, transparent)
                    canvas.drawText(date, fw * 0.07f, fw * 0.06f + p1.textSize * 0.8f, p1)
                    val p2 = textPaint(fw * 0.044f, false, muted, transparent)
                    canvas.drawText("$dist from Sun", fw * 0.07f, fw * 0.95f, p2)
                }
            }

            Shape.GLOBE -> {
                val view = globeView(st.L)
                val earthR = if (showInfo) fw * 0.31f else fw * 0.40f
                val cy = if (showInfo) fh * 0.44f else fh * 0.5f
                // faint warm glow on the side the Sun is on
                val sun = sunVec(st.L)
                var gx = dot(sun, view.r)
                var gy = dot(sun, view.u)
                val gn = hypot(gx, gy).takeIf { it > 1e-6 } ?: 1.0
                gx /= gn; gy /= gn
                val glowCx = (fw / 2 + gx * earthR * 1.9).toFloat()
                val glowCy = (cy - gy * earthR * 1.9).toFloat()
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        glowCx, glowCy, earthR * 2.2f,
                        intArrayOf(0x5AFFB45A, 0x00FF6414), null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(glowCx, glowCy, earthR * 2.2f, glow)
                drawEarth(canvas, ctx, fw / 2, cy, earthR, view, st.L, nowMs)
                if (showInfo) {
                    val p1 = textPaint(fw * 0.075f, true, ink, transparent).apply { textAlign = Paint.Align.CENTER }
                    canvas.drawText(date, fw / 2, fh * 0.855f, p1)
                    val p2 = textPaint(fw * 0.052f, false, muted, transparent).apply { textAlign = Paint.Align.CENTER }
                    canvas.drawText(dist, fw / 2, fh * 0.94f, p2)
                }
            }

            Shape.WIDE -> {
                if (showInfo) {
                    drawOrbitScene(canvas, ctx, fh / 2, fh / 2, fh, st, nowMs, look, monthLabels = false)
                    val x0 = fh * 1.02f
                    val avail = fw - x0 - fh * 0.06f
                    val lines = listOf(
                        "$seasonName · day $dayOfYear" to 0.47f,
                        "$dist from Sun" to 0.65f,
                        String.format(Locale.US, "%.1f km/s", st.speedKms) to 0.82f
                    )
                    val p1 = textPaint(fh * 0.17f, true, ink, transparent)
                    fit(p1, date, avail)
                    canvas.drawText(date, x0, fh * 0.30f, p1)
                    for ((text, base) in lines) {
                        val p = textPaint(fh * 0.095f, false, muted, transparent)
                        fit(p, text, avail)
                        canvas.drawText(text, x0, fh * base, p)
                    }
                } else {
                    drawOrbitScene(canvas, ctx, fw / 2, fh / 2, min(fw, fh), st, nowMs, look, monthLabels = false)
                }
            }

            Shape.STRIP -> {
                val view = globeView(st.L)
                if (showInfo) {
                    drawEarth(canvas, ctx, fh * 0.55f, fh / 2, fh * 0.40f, view, st.L, nowMs)
                    val x0 = fh * 1.12f
                    val avail = fw - x0 - fh * 0.3f
                    val p1 = textPaint(fh * 0.30f, true, ink, transparent)
                    fit(p1, date, avail)
                    canvas.drawText(date, x0, fh * 0.50f, p1)
                    val line = "$dist · $seasonName"
                    val p2 = textPaint(fh * 0.19f, false, muted, transparent)
                    fit(p2, line, avail)
                    canvas.drawText(line, x0, fh * 0.80f, p2)
                } else {
                    drawEarth(canvas, ctx, fw / 2, fh / 2, fh * 0.42f, view, st.L, nowMs)
                }
            }
        }

        // round the corners (or cut the circle) with soft edges
        if (look != Look.TRANSPARENT) {
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawPath(shapePath(fw, fh, corner, circle, inverse = true), cut)
            if (look == Look.GLASS) {
                val sw = max(1.5f, min(fw, fh) / 150f)
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = sw
                    color = 0x55FFFFFF
                }
                val inset = sw / 2f
                if (circle) {
                    canvas.drawCircle(fw / 2, fh / 2, min(fw, fh) / 2f - inset, edge)
                } else {
                    canvas.drawRoundRect(RectF(inset, inset, fw - inset, fh - inset), corner, corner, edge)
                }
            }
        }
        return bmp
    }
}
