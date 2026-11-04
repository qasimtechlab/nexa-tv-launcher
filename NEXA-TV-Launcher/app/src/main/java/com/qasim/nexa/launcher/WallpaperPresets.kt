package com.qasim.nexa.launcher

import android.graphics.drawable.GradientDrawable

internal data class WallpaperPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val colors: IntArray
)

internal object WallpaperPresets {
    val all = listOf(
        WallpaperPreset("aurora", "Aurora", "Cool blue and violet", intArrayOf(0xFF071B37.toInt(), 0xFF234E78.toInt(), 0xFF684A91.toInt())),
        WallpaperPreset("ocean", "Deep Ocean", "Midnight blue depths", intArrayOf(0xFF041C2C.toInt(), 0xFF07516A.toInt(), 0xFF159A9A.toInt())),
        WallpaperPreset("nebula", "Nebula", "Cosmic purple glow", intArrayOf(0xFF160E32.toInt(), 0xFF44246F.toInt(), 0xFFB04F80.toInt())),
        WallpaperPreset("sunset", "Sunset", "Warm amber horizon", intArrayOf(0xFF311A35.toInt(), 0xFF9C3E55.toInt(), 0xFFE69A52.toInt())),
        WallpaperPreset("emerald", "Emerald", "Deep forest green", intArrayOf(0xFF061F25.toInt(), 0xFF0D5A50.toInt(), 0xFF70A77C.toInt())),
        WallpaperPreset("graphite", "Graphite", "Minimal silver and charcoal", intArrayOf(0xFF101722.toInt(), 0xFF344153.toInt(), 0xFF718096.toInt()))
    )

    fun find(id: String?): WallpaperPreset = all.firstOrNull { it.id == id } ?: all.first()

    fun drawable(id: String?): GradientDrawable {
        val colors = find(id).colors
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }
}
