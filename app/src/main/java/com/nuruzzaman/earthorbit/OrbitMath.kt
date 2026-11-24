package com.nuruzzaman.earthorbit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth's position on its real, slightly oval orbit for any moment in time. */
object OrbitMath {
    const val ECC = 0.01671
    val PERI = Math.toRadians(102.9373)
    val TILT = Math.toRadians(23.4393)
    val SIN_E = sin(TILT)
    val COS_E = cos(TILT)
    const val AU_MKM = 149.598
    private const val TAU = 2 * PI

    /** L = Earth's heliocentric longitude (radians), r = distance from the Sun in AU. */
    class State(val L: Double, val r: Double) {
        val distanceMkm: Double get() = r * AU_MKM
        val speedKms: Double get() = 29.78 * sqrt(2.0 / r - 1.0)

        /** 0 spring, 1 summer, 2 autumn, 3 winter in the northern hemisphere. */
        val season: Int
            get() {
                val sunLon = (L + PI) % TAU
                return (sunLon / (PI / 2)).toInt() % 4
            }
    }

    fun state(epochMs: Long): State {
        val d = epochMs / 86_400_000.0 + 2440587.5 - 2451545.0
        var m = Math.toRadians((357.5291 + 0.98560028 * d) % 360.0)
        if (m < 0) m += TAU
        val nu = m + 2 * ECC * sin(m) + 1.25 * ECC * ECC * sin(2 * m)
        var l = (nu + PERI) % TAU
        if (l < 0) l += TAU
        val r = (1 - ECC * ECC) / (1 + ECC * cos(nu))
        return State(l, r)
    }

    /** Orbit radius (AU) at heliocentric longitude [l]. */
    fun radiusAt(l: Double): Double = (1 - ECC * ECC) / (1 + ECC * cos(l - PERI))
}
