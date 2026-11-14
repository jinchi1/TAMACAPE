package ai.tamacape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

internal val MobiGrowth.avatarArt: Int get() = when (this) {
    MobiGrowth.SPROUT -> R.drawable.mobi_sprite
    MobiGrowth.EXPLORER, MobiGrowth.SAFETY_EXPLORER -> R.drawable.mobi_explorer_sprite
    MobiGrowth.MASTER -> R.drawable.mobi_master_sprite
}

// The character art and the accessory are rendered independently so that equipment
// can be changed immediately without needing a separate sprite for every combination.
internal class MobiAccessory(context: Context, private val item: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Color.rgb(247, 188, 59)
    private val teal = Color.rgb(63, 147, 137)
    private val brown = Color.rgb(94, 57, 44)
    private val navy = Color.rgb(53, 57, 94)
    private val cream = Color.rgb(255, 231, 171)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 100f, height / 100f)
        fun oval(l: Float, t: Float, r: Float, b: Float, color: Int) {
            paint.color = color
            canvas.drawOval(l, t, r, b, paint)
        }
        fun bar(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
            paint.color = color
            canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
        }
        fun lens(x: Float, color: Int) {
            oval(x, 42f, x + 22, 58f, brown)
            oval(x + 2, 44f, x + 20, 56f, color)
            oval(x + 5, 45f, x + 10, 48f, Color.WHITE)
        }
        fun star(x: Float, y: Float, radius: Float, color: Int) {
            val path = Path()
            repeat(10) { index ->
                val angle = Math.PI * index / 5 - Math.PI / 2
                val distance = if (index % 2 == 0) radius else radius * 0.45f
                val px = x + (kotlin.math.cos(angle) * distance).toFloat()
                val py = y + (kotlin.math.sin(angle) * distance).toFloat()
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            paint.color = color
            canvas.drawPath(path, paint)
        }
        when (item) {
            "안전모", "캡모자", "탐험모자" -> {
                val color = when (item) {
                    "안전모" -> gold
                    "캡모자" -> teal
                    else -> brown
                }
                canvas.save()
                canvas.clipRect(0f, 0f, 100f, 34f)
                oval(23f, 18f, 78f, 51f, color)
                canvas.restore()
                bar(19f, 31f, if (item == "캡모자") 90f else 83f, 38f, 4f, color)
                star(51f, 27f, 6f, cream)
            }
            "고글" -> {
                bar(18f, 47f, 82f, 51f, 2f, brown)
                lens(24f, teal)
                lens(54f, teal)
            }
            "별 선글라스" -> {
                bar(18f, 47f, 82f, 51f, 2f, navy)
                for (x in listOf(36f, 65f)) {
                    star(x, 49f, 14f, gold)
                    star(x, 49f, 10f, navy)
                }
            }
            "헤드셋" -> {
                oval(9f, 42f, 22f, 64f, navy)
                oval(78f, 42f, 91f, 64f, navy)
                bar(62f, 60f, 87f, 64f, 2f, navy)
            }
            "스카프", "별 망토" -> {
                bar(26f, 63f, 74f, 69f, 4f, if (item == "스카프") teal else gold)
                bar(62f, 66f, if (item == "스카프") 71f else 88f, 84f, 4f, gold)
                star(64f, 68f, 6f, cream)
            }
            "탐험 가방" -> {
                bar(68f, 62f, 89f, 86f, 6f, brown)
                bar(71f, 66f, 87f, 75f, 4f, gold)
                bar(76f, 72f, 82f, 79f, 2f, cream)
            }
            "안전벨트 배지" -> {
                oval(56f, 66f, 72f, 82f, navy)
                star(64f, 74f, 7f, gold)
            }
        }
        canvas.restore()
    }
}

internal class RoomFurniture(context: Context, private val item: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 100f, height / 100f)
        fun block(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
            paint.color = color
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
        val mint = Color.rgb(132, 205, 177)
        val purple = Color.rgb(172, 157, 224)
        val gold = Color.rgb(255, 211, 122)
        when (item) {
            "별빛 조명" -> {
                block(45f, 40f, 55f, 87f, 5f, purple)
                block(28f, 77f, 72f, 91f, 7f, purple)
                block(21f, 16f, 79f, 53f, 17f, gold)
            }
            "민트 러그" -> {
                block(2f, 58f, 98f, 94f, 18f, mint)
                block(15f, 67f, 85f, 85f, 9f, Color.rgb(198, 241, 224))
            }
            "탐험 책장" -> {
                block(8f, 13f, 92f, 92f, 9f, Color.rgb(180, 128, 87))
                block(16f, 23f, 84f, 54f, 4f, Color.rgb(255, 232, 203))
                block(16f, 62f, 84f, 84f, 4f, Color.rgb(255, 232, 203))
                block(23f, 27f, 34f, 54f, 3f, purple)
                block(36f, 31f, 48f, 54f, 3f, mint)
                block(53f, 25f, 67f, 54f, 3f, gold)
            }
            "구름 쿠션" -> {
                block(8f, 51f, 91f, 87f, 17f, purple)
                block(25f, 34f, 61f, 75f, 18f, purple)
                block(51f, 42f, 80f, 77f, 14f, purple)
            }
        }
        canvas.restore()
    }
}
