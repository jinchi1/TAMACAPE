package ai.tamacape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

internal class AlleyScene(context: Context, private val select: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> true
        MotionEvent.ACTION_UP -> {
            if (event.x >= 0 && event.x < width && event.y >= 0 && event.y < height) {
                performClick()
                select((event.x * 3 / width).toInt())
            }
            true
        }
        else -> true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 300f, height / 120f)
        fun rect(x: Float, y: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
            paint.color = color
            canvas.drawRoundRect(x, y, r, b, radius, radius, paint)
        }
        fun circle(x: Float, y: Float, radius: Float, color: Int) {
            paint.color = color
            canvas.drawCircle(x, y, radius, paint)
        }
        canvas.drawColor(Color.rgb(205, 224, 249))
        rect(0f, 50f, 300f, 120f, Color.rgb(227, 216, 201))
        rect(0f, 90f, 300f, 120f, Color.rgb(100, 107, 135))
        for (x in 10..290 step 28) {
            rect(x.toFloat(), 106f, x + 14f, 108f, Color.rgb(238, 223, 162))
        }

        // Parked van, open plaza, and the blind corner of a brick wall.
        rect(9f, 49f, 90f, 86f, Color.rgb(57, 132, 143), 7f)
        rect(28f, 40f, 83f, 71f, Color.rgb(76, 152, 161), 9f)
        rect(33f, 45f, 55f, 59f, Color.rgb(177, 218, 238), 3f)
        rect(60f, 45f, 76f, 59f, Color.rgb(177, 218, 238), 3f)
        circle(28f, 85f, 8f, Color.rgb(58, 55, 73))
        circle(72f, 85f, 8f, Color.rgb(58, 55, 73))

        rect(109f, 55f, 189f, 87f, Color.rgb(241, 230, 192), 4f)
        rect(145f, 60f, 155f, 82f, Color.rgb(176, 193, 189), 2f)
        rect(131f, 67f, 169f, 78f, Color.rgb(122, 173, 191), 5f)

        rect(216f, 38f, 296f, 88f, Color.rgb(175, 110, 121), 4f)
        rect(216f, 38f, 234f, 88f, Color.rgb(141, 79, 97), 3f)
        for (y in 48..80 step 10) {
            rect(235f, y.toFloat(), 296f, y + 1f, Color.rgb(212, 149, 150))
        }
        canvas.restore()
    }
}
