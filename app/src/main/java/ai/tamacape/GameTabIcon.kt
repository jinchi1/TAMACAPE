package ai.tamacape

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

internal enum class GameIcon { HOME, MAP, GIFT, BOOK, SETTINGS, BELT, DOOR, WARNING }

internal class GameTabIcon(private val icon: GameIcon, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg points: Float) {
            val path = Path()
            path.moveTo(points[0], points[1])
            for (index in 2 until points.size step 2) path.lineTo(points[index], points[index + 1])
            canvas.drawPath(path, paint)
        }
        when (icon) {
            GameIcon.HOME -> {
                line(2f, 11f, 12f, 3f, 22f, 11f)
                line(5f, 10f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 10f)
            }
            GameIcon.MAP -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                line(16f, 7f, 14f, 14f, 8f, 17f, 10f, 10f, 16f, 7f)
            }
            GameIcon.GIFT -> {
                canvas.drawRect(4f, 10f, 20f, 21f, paint)
                canvas.drawRect(3f, 6f, 21f, 10f, paint)
                line(12f, 6f, 12f, 21f)
                canvas.drawOval(6f, 2f, 12f, 6f, paint)
                canvas.drawOval(12f, 2f, 18f, 6f, paint)
            }
            GameIcon.BOOK -> {
                line(12f, 6f, 7f, 4f, 3f, 4f, 3f, 19f, 7f, 19f, 12f, 21f, 17f, 19f, 21f, 19f, 21f, 4f, 17f, 4f, 12f, 6f, 12f, 21f)
            }
            GameIcon.BELT -> {
                line(5f, 3f, 18f, 16f)
                line(3f, 6f, 16f, 19f)
                canvas.drawRoundRect(14f, 14f, 21f, 22f, 2f, 2f, paint)
            }
            GameIcon.DOOR -> {
                line(4f, 20f, 4f, 9f, 8f, 4f, 17f, 4f, 21f, 10f, 21f, 20f, 4f, 20f)
                line(4f, 11f, 21f, 11f)
                line(15f, 15f, 18f, 15f)
            }
            GameIcon.WARNING -> {
                line(12f, 3f, 22f, 20f, 2f, 20f, 12f, 3f)
                line(12f, 9f, 12f, 13f)
                canvas.drawPoint(12f, 16f, paint)
            }
            GameIcon.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 7f, paint)
                canvas.drawCircle(12f, 12f, 2.5f, paint)
                for (index in 0 until 8) {
                    canvas.save()
                    canvas.rotate(45f * index, 12f, 12f)
                    canvas.drawLine(12f, 2f, 12f, 5f, paint)
                    canvas.restore()
                }
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated by Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
