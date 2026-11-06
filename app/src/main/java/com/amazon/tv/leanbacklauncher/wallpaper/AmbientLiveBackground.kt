package com.amazon.tv.leanbacklauncher.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight animated, tvOS-inspired ambient backdrop rendered in the launcher itself.
 * This avoids relying on device-specific wallpaper preview activities.
 */
class AmbientLiveBackground @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startedAt = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("launcher_live_background", false)) return
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f
        canvas.drawColor(0xFF071326.toInt())

        drawGlow(canvas, w * (.28f + .12f * sin(t * .17f)), h * (.32f + .10f * cos(t * .13f)), w * .72f, 0xFF1D6AB1.toInt())
        drawGlow(canvas, w * (.72f + .10f * cos(t * .11f)), h * (.62f + .08f * sin(t * .16f)), w * .68f, 0xFF563E9B.toInt())
        drawGlow(canvas, w * (.48f + .06f * sin(t * .09f)), h * (.84f + .04f * cos(t * .12f)), w * .54f, 0xFF167C88.toInt())
        postInvalidateDelayed(50)
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(x, y, radius, intArrayOf(color, color and 0x00FFFFFF, 0x00000000), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }
}
