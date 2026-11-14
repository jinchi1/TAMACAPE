package ai.tamacape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/** Decorative feedback for accessible dashboard controls; never drives a real car. */
internal class FogRoadScene(context: Context, private val slowed: Boolean, private val spaced: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = "안개 도로. " + (if (slowed) "감속 완료. " else "감속 필요. ") +
            (if (spaced) "차간거리 확보" else "앞차와 가까움")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF475774.toInt())
        canvas.save()
        val scale = minOf(width / 480f, height / 180f)
        canvas.translate((width - 480f * scale) / 2f, (height - 180f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.shader = LinearGradient(0f, 0f, 0f, 180f,
            intArrayOf(0xFF475774.toInt(), 0xFFB2C5CF.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 480f, 180f, paint)
        paint.shader = null
        val road = Path().apply {
            moveTo(204f, 40f); lineTo(276f, 40f)
            lineTo(440f, 180f); lineTo(40f, 180f); close()
        }
        paint.color = 0xFF3F4C65.toInt()
        canvas.drawPath(road, paint)
        paint.color = 0xFFD4E1E5.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(206f, 44f, 65f, 180f, paint)
        canvas.drawLine(274f, 44f, 415f, 180f, paint)
        for (y in 60..168 step 24) {
            paint.strokeWidth = (y - 30) / 28f
            canvas.drawLine(240f, y.toFloat(), 240f, y + 11f, paint)
        }
        val carY = if (spaced) 73f else 118f
        val carWidth = if (spaced) 47f else 86f
        paint.color = 0xFF253348.toInt()
        canvas.drawOval(240f - carWidth / 2 - 4, carY + 18, 240f + carWidth / 2 + 4, carY + 28, paint)
        paint.color = 0xFF91DED1.toInt()
        canvas.drawRoundRect(240f - carWidth / 2, carY - 14, 240f + carWidth / 2, carY + 22, 9f, 9f, paint)
        paint.color = 0xFF334D68.toInt()
        canvas.drawRoundRect(240f - carWidth / 3, carY - 9, 240f + carWidth / 3, carY + 3, 4f, 4f, paint)
        paint.color = 0xFFFFB1A4.toInt()
        canvas.drawCircle(240f - carWidth / 3, carY + 13, 4f, paint)
        canvas.drawCircle(240f + carWidth / 3, carY + 13, 4f, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, 100f,
            intArrayOf(0xDDC9DBE0.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 480f, 100f, paint)
        paint.shader = null
        paint.color = 0xE6222D47.toInt()
        canvas.drawRoundRect(12f, 132f, 180f, 168f, 12f, 12f, paint)
        paint.color = if (slowed) 0xFFADFFE4.toInt() else 0xFFFFDF91.toInt()
        paint.textSize = 16f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(if (slowed) "속도 ↓  감속 완료" else "속도를 낮춰 주세요", 22f, 155f, paint)
        canvas.restore()
    }
}
