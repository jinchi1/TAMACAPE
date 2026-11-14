package ai.tamacape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal class GarageCar(context: Context, private val closed: Boolean, toggle: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val door = Path()

    init {
        contentDescription = if (closed) "닫힌 가상 차량 도어. 눌러서 열기" else "열린 가상 차량 도어. 눌러서 닫기"
        isFocusable = true
        setOnClickListener { toggle() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = minOf(width / 300f, height / 140f)
        canvas.translate((width - 300f * scale) / 2, (height - 140f * scale) / 2)
        canvas.scale(scale, scale)

        paint.color = Color.rgb(57, 114, 143)
        canvas.drawRoundRect(103f, 12f, 197f, 128f, 26f, 26f, paint)
        paint.color = Color.rgb(182, 220, 234)
        canvas.drawRoundRect(114f, 24f, 186f, 53f, 9f, 9f, paint)
        canvas.drawRoundRect(114f, 94f, 186f, 114f, 9f, 9f, paint)
        paint.color = Color.rgb(39, 70, 98)
        canvas.drawRoundRect(121f, 59f, 179f, 88f, 5f, 5f, paint)
        door.reset()
        door.moveTo(196f, 52f)
        door.lineTo(if (closed) 198f else 239f, if (closed) 54f else 68f)
        door.lineTo(if (closed) 198f else 233f, if (closed) 88f else 96f)
        door.lineTo(196f, 88f)
        door.close()
        paint.color = if (closed) Color.rgb(105, 220, 169) else Color.rgb(251, 182, 92)
        canvas.drawPath(door, paint)
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.rgb(84, 71, 143)
            canvas.drawRoundRect(5f, 5f, 295f, 135f, 14f, 14f, paint)
            paint.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }
}
