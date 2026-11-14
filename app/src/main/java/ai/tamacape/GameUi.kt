package ai.tamacape

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button

/** Shared controls for game screens and the music player. */
internal object GameUi {
    fun control(context: Context, enabled: Boolean, focused: Boolean, primary: Boolean = false, radius: Int = 18): RippleDrawable {
        fun dp(n: Int) = (n * context.resources.displayMetrics.density + .5f).toInt()
        val colors = when {
            !enabled -> intArrayOf(0xFF34354F.toInt(), 0xFF292B43.toInt())
            primary -> intArrayOf(0xFF9B87F5.toInt(), 0xFF6751B8.toInt())
            else -> intArrayOf(0xFF575675.toInt(), 0xFF373951.toInt())
        }
        val shape = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(if (focused) 3 else 1), if (focused) 0xFFFFDE98.toInt() else 0xFF8981AA.toInt())
        }
        return RippleDrawable(ColorStateList.valueOf(0x448FBDFF), shape, null)
    }
    fun style(button: Button, primary: Boolean = false, large: Boolean = false) {
        val density = button.resources.displayMetrics.density
        button.apply {
            isAllCaps = false
            textSize = if (large) 20f else 17f
            minHeight = (58 * density).toInt()
            minimumWidth = 0
            setPadding((12*density).toInt(), (10*density).toInt(), (12*density).toInt(), (10*density).toInt())
            setTextColor(0xFFF5F0FF.toInt())
            setTypeface(null, Typeface.BOLD)
            backgroundTintList = null
            stateListAnimator = null
            background = control(context, isEnabled, false, primary)
            setOnFocusChangeListener { _, focused -> background = control(context, isEnabled, focused, primary) }
        }
    }
}
