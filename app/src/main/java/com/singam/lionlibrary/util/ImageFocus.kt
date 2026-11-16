package com.singam.lionlibrary.util

import kotlin.math.abs

/**
 * Estimates where the subject sits horizontally in a small ARGB image, as a fraction of its width.
 * Detail (luma edges) and colour saturation per column stand in for "interesting"; the weighted
 * centre of that energy, pulled slightly toward the middle, is the answer. Flat or empty images
 * return 0.5. The result stays inside [MIN_FOCUS]..[MAX_FOCUS] so a crop never pins to an edge.
 */
object ImageFocus {
    const val MIN_FOCUS = .2f
    const val MAX_FOCUS = .8f

    /**
     * Share of the estimate kept; the rest is the centre, so weak evidence moves the crop little.
     */
    private const val CONFIDENCE = .75f

    fun horizontalFocus(pixels: IntArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3 || pixels.size < width * height) return .5f
        val energy = DoubleArray(width)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val luma = luma(pixels[i])
                val edge = abs(luma - luma(pixels[i + 1])) + abs(luma - luma(pixels[i + width]))
                energy[x] += edge + saturation(pixels[i]) * .5
            }
        }
        val total = energy.sum()
        if (total <= 1e-6) return .5f
        // Small smoothing so a single sharp column cannot dominate.
        var weighted = 0.0
        for (x in 0 until width) {
            val smooth =
                (energy[x] * 2 +
                    energy.getOrElse(x - 1) { 0.0 } +
                    energy.getOrElse(x + 1) { 0.0 }) / 4
            weighted += smooth * (x + .5)
        }
        val centroid = (weighted / total / width).toFloat()
        val pulled = .5f + (centroid - .5f) * CONFIDENCE
        return pulled.coerceIn(MIN_FOCUS, MAX_FOCUS)
    }

    private fun luma(argb: Int): Double {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }

    private fun saturation(argb: Int): Double {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0.0 else (max - min).toDouble() / max
    }
}
