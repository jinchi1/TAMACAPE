package ai.tamacape

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.*

/** Updates only player widgets, preserving album scroll and focus. */
internal class SongPlayerView(context: Context, private val songs: List<SavedSong>, private val large: Boolean,
    private val allowed: () -> Boolean) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val portrait = ImageView(context)
    private val title = text(23f)
    private val state = text(16f)
    private val clock = text(17f)
    private val lyric = text(23f)
    private val seek = SeekBar(context)
    private val play = Button(context)
    private val repeat = Button(context)
    private var dragging = false
    private var selected: SavedSong? = null
    private fun text(size: Float) = TextView(context).apply {
        textSize = size * if (large) 1.2f else 1f
        setTextColor(Color.WHITE)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun command(action: String) {
        if (!allowed()) return
        context.startService(Intent(context, SongPlayback::class.java).setAction(action)
            .putExtra("id", (selected ?: songs.firstOrNull())?.id))
    }
    init {
        orientation = VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF514979.toInt(), 0xFF252E49.toInt())).apply { cornerRadius = dp(24).toFloat() }
        title.setTypeface(null, Typeface.BOLD)
        lyric.setTextColor(0xFFB8F3DD.toInt())
        lyric.minHeight = dp(100)
        val header = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        portrait.scaleType = ImageView.ScaleType.FIT_CENTER
        header.addView(portrait, LayoutParams(dp(144), dp(160)))
        val headings = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), 0, 0, 0) }
        headings.addView(title); headings.addView(state)
        header.addView(headings, LayoutParams(0, -2, 1f))
        header.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF494263.toInt(), 0xFF303B55.toInt())).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFF85779F.toInt())
            }
        header.setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(header)
        lyric.gravity = android.view.Gravity.CENTER
        lyric.setPadding(dp(16), dp(18), dp(16), dp(18))
        lyric.background = GradientDrawable().apply {
            setColor(0xFF24283F.toInt()); cornerRadius = dp(18).toFloat()
        }
        addView(lyric, LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })
        seek.progressTintList = android.content.res.ColorStateList.valueOf(0xFFB4E6D5.toInt())
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFFDE98.toInt())
        seek.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF65607F.toInt())
        addView(seek); addView(clock)
        val controls = LinearLayout(context)
        fun control(button: Button, label: String, action: () -> Unit) {
            button.text = label
            GameUi.style(button, button === play, large)
            button.minHeight = dp(56)
            button.setOnClickListener { action() }
            controls.addView(button, LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(8), dp(4), dp(8)) })
        }
        control(Button(context), "◀ 이전") { command("previous") }
        control(play, "▶ 재생") { command(if (SongPlayback.playing) "pause" else "play") }
        control(Button(context), "다음 · ▶") { command("next") }
        addView(controls)
        GameUi.style(repeat, large = large)
        repeat.setOnClickListener { command("repeat") }
        addView(repeat)
        seek.contentDescription = "동요 재생 위치"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) clock.text = "${songTime(value.toLong())} / ${songTime(selected?.duration ?: 0)}"
            }
            override fun onStopTrackingTouch(bar: SeekBar) {
                dragging = false
                if (allowed()) context.startService(Intent(context, SongPlayback::class.java)
                    .setAction("seek").putExtra("position", bar.progress.toLong()))
            }
        })
        refresh()
    }
    private fun refresh() {
        selected = songs.firstOrNull { it.id == SongPlayback.currentId }
        val song = selected
        val friend = companions.firstOrNull { it.id == (song ?: songs.firstOrNull())?.companion } ?: companions.first()
        portrait.setImageResource(friend.art)
        portrait.contentDescription = friend.name
        val position = SongPlayback.position().coerceIn(0, song?.duration ?: 0)
        title.text = song?.title ?: "듣고 싶은 동요를 골라 주세요"
        state.text = if (song == null) "재생 대기" else if (SongPlayback.playing) "♫ 재생 중" else "Ⅱ 일시정지"
        play.text = if (SongPlayback.playing) "Ⅱ 일시정지" else "▶ 재생"
        repeat.text = if (SongPlayback.repeat) "↻ 한 곡 반복 · 켜짐" else "↻ 한 곡 반복 · 꺼짐"
        seek.isEnabled = song != null && allowed()
        if (!dragging) {
            seek.max = (song?.duration ?: 1).toInt()
            seek.progress = position.toInt()
            clock.text = "${songTime(position)} / ${songTime(song?.duration ?: 0)}"
        }
        lyric.text = when {
            song == null -> "캐릭터와 함께 안전 약속을 노래해요"
            song.file.extension == "wav" -> "♪ 시연 연주곡 · 보컬 없음"
            else -> {
                val lines = song.lyrics.lines().filter(String::isNotBlank)
                val index = estimatedLyricLine(song.lyrics, position, song.duration)
                "가사 따라보기 · 예상 타이밍\n" + (lines.getOrNull(index) ?: "♪") +
                    (lines.getOrNull(index + 1)?.let { "\n$it" } ?: "")
            }
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            refresh()
            postDelayed(this, 250)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
}
