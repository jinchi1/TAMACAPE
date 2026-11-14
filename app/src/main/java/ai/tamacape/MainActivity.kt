package ai.tamacape

import android.app.Activity
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.app.AlertDialog
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : Activity() {
    private lateinit var store: AdventureStore
    private lateinit var settingsStore: GameSettingsStore
    private var settings = GameSettings()
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var car: Car? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var restrictionState = ParkingAccess.UNKNOWN
    private var openDialog: AlertDialog? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var snapshotJob: Job? = null
    private var snapshot: VehicleSnapshot? = null
    private var readingSnapshot = false
    private val worker = Executors.newSingleThreadExecutor()
    private var save = AdventureSave()
    private var loading = true
    private var saving = false
    private var storageError = false
    private lateinit var root: LinearLayout
    private var scroll: ScrollView? = null
    private var page = "홈"
    private var previewItem: String? = null
    private var turnPuzzle: SignalPractice? = null
    private var unopenedRewardStage: Int? = null
    private var idle: ObjectAnimator? = null
    private var gameAudio: ToneGenerator? = null
    private var active = false
    private var stageIndex = 0
    private var feedback = ""
    private val familyVoice by lazy { FamilyVoice(this) }
    private var quizTts: android.speech.tts.TextToSpeech? = null
    private var quizTtsReady: Boolean? = null
    private var spokenQuestion: Pair<Int, Int>? = null
    private var speechId: String? = null
    private var voiceStatus = ""
    private var voiceText = ""
    private var voiceListening = false
    private val familyAi = FamilyAiClient()
    private var aiJob: Job? = null
    private var aiGeneration = 0
    private var aiEndpoint = FAMILY_AI_ENDPOINT
    private var aiAccess = ""
    private val songAlbum by lazy { SongAlbum(this) }
    private var songPreview: Pair<String, String>? = null
    private var songAge = "4-6"
    private var songStyle = "bright"
    private var latestDiscussion: Pair<Int, Int>? = null
    private var aiKind = "coach"
    private var aiStage = 0
    private var aiPuzzle = 0
    private var aiDraft = ""
    private var aiOutput = ""
    private var aiMessage = ""
    private var aiBusy = false
    private var aiVoicePending = ""
    private val cream = Color.rgb(17, 20, 44)
    private val ink = Color.rgb(245, 242, 255)
    private val mint = Color.rgb(118, 94, 225)
    private val lavender = Color.rgb(40, 43, 79)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AdventureStore(this)
        settingsStore = GameSettingsStore(this)
        page = savedInstanceState?.getString("page") ?: "홈"
        if (page == "AI 친구") {
            aiKind = savedInstanceState?.getString("aiKind")?.takeIf { it in listOf("coach", "song") } ?: "coach"
            aiStage = (savedInstanceState?.getInt("aiStage") ?: 0).coerceIn(stages.indices)
            aiPuzzle = (savedInstanceState?.getInt("aiPuzzle") ?: 0).coerceIn(stages[aiStage].puzzles.indices)
        }
        if (page == "퍼즐" && savedInstanceState != null) {
            val step = SignalStep.entries.find { it.name == savedInstanceState.getString("turnStep") }
            val signal = TurnSignal.entries.find { it.name == savedInstanceState.getString("turnSignal") }
            if (step != null && signal != null && step != SignalStep.COMPLETE) {
                turnPuzzle = SignalPractice(step, signal)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = android.window.OnBackInvokedCallback { handleSystemBack() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        }
        loadSave()
        connectRestrictions()
    }

    @Deprecated("Legacy Android back dispatch")
    override fun onBackPressed() { handleSystemBack() }

    private fun handleSystemBack() {
        if (openDialog?.isShowing == true) { openDialog?.dismiss(); return }
        if (saving) return
        if (loading || !restrictionState.allowsGame || page == "홈") {
            finish()
            return
        }
        navigate(when (page) {
            "AI 친구" -> "퍼즐"
            "퍼즐" -> "모험"
            "차량" -> "설정"
            "동요 앨범" -> "홈"
            else -> "홈"
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page)
        outState.putString("aiKind", aiKind)
        outState.putInt("aiStage", aiStage)
        outState.putInt("aiPuzzle", aiPuzzle)
        turnPuzzle?.let {
            outState.putString("turnStep", it.step.name)
            outState.putString("turnSignal", it.signal.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        active = true
        restrictionState = readRestrictionState()
        draw()
    }

    override fun onPause() {
        active = false
        stopInteractiveWork()
        super.onPause()
    }

    override fun onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 33) backCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        backCallback = null
        gameAudio?.release()
        gameAudio = null
        quizTts?.shutdown()
        quizTts = null
        familyAi.close()
        activityScope.cancel()
        runCatching { restrictions?.unregisterListener() }
        car?.disconnect()
        worker.execute { store.close() }
        worker.shutdown()
        super.onDestroy()
    }

    private fun connectRestrictions() {
        runCatching {
            car = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connection, ready ->
                if (isDestroyed) return@createCar
                runCatching { restrictions?.unregisterListener() }
                restrictions = if (ready) runCatching {
                    val manager = connection.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                        as? CarUxRestrictionsManager
                    manager?.registerListener { state ->
                        onRestrictionChanged(ParkingAccess.fromPlatform(state.isRequiresDistractionOptimization))
                    }
                    manager
                }.getOrNull() else null
                onRestrictionChanged(readRestrictionState())
            }
        }
    }

    private fun readRestrictionState(): ParkingAccess = ParkingAccess.fromPlatform(
        runCatching {
            restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization
        }.getOrNull(),
    )

    internal fun onRestrictionChanged(state: ParkingAccess) {
        restrictionState = state
        if (!state.allowsGame) {
            stopInteractiveWork()
            if (SongPlayback.playing) songCommand("pause")
        }
        if (active) draw()
    }

    private fun stopVoice() {
        speechId = null
        quizTts?.stop()
        familyVoice.stop()
        voiceListening = false
        voiceText = ""
        voiceStatus = ""
    }

    private fun cancelAi() {
        aiGeneration++
        aiJob?.cancel()
        aiJob = null
        aiBusy = false
    }

    private fun stopInteractiveWork() {
        cancelAi()
        aiDraft = ""
        aiOutput = ""
        songPreview = null
        aiVoicePending = ""
        stopVoice()
        gameAudio?.stopTone()
        idle?.cancel()
        openDialog?.dismiss()
        openDialog = null
        clearSnapshot()
    }

    private fun playEffect(tone: Int, durationMs: Int) {
        if (!settings.sound || !canInteract()) return
        runCatching {
            val audio = gameAudio ?: ToneGenerator(AudioManager.STREAM_MUSIC, 25).also { gameAudio = it }
            audio.stopTone()
            audio.startTone(tone, durationMs)
        }
    }

    private fun canInteract(): Boolean =
        active && restrictionState.allowsGame && readRestrictionState().allowsGame

    private fun showParkedDialog(builder: AlertDialog.Builder) {
        if (!canInteract()) return
        openDialog?.dismiss()
        openDialog = builder.create().also { dialog ->
            dialog.window?.setBackgroundDrawable(panel(0xFF252747.toInt()))
            dialog.show()
            dialog.window?.setBackgroundDrawable(panel(0xFF252747.toInt()))
            dialog.window?.setLayout(dp(minOf(availableWidthDp() - 32, if (settings.largeText) 760 else 620)), -2)
            fun style(view: android.view.View) {
                if (view is TextView) {
                    view.setTextColor(ink)
                    if (view.id == android.R.id.message) {
                        view.textSize = if (settings.largeText) 22f else 18f
                        view.setLineSpacing(dp(4).toFloat(), 1.05f)
                    }
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
            }
            style(dialog.window!!.decorView)
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
                dialog.getButton(which)?.apply {
                    GameUi.style(this, which == AlertDialog.BUTTON_POSITIVE, settings.largeText)
                    (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                        it.setMargins(dp(6), dp(8), dp(6), dp(8))
                        layoutParams = it
                    }
                }
            }
        }
    }

    private fun clearSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = null
        readingSnapshot = false
        snapshot = null
    }

    private fun readSnapshot() {
        if (readingSnapshot || !canInteract()) return
        val demo = settings.demo
        val provider = if (demo) DemoVehicleProvider else PleosVehicleProvider(this, ::canInteract)
        readingSnapshot = true
        snapshot = null
        draw()
        snapshotJob = activityScope.launch {
            val result = provider.read()
            readingSnapshot = false
            snapshot = result
            if (active && page == "차량") draw()
        }
    }

    private fun loadSave() {
        loading = true
        storageError = false
        worker.execute {
            val result = runCatching { store.load() to runBlocking { settingsStore.read() } }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loading = false
                storageError = result.isFailure
                result.onSuccess { (adventure, preferences) ->
                    save = adventure
                    settings = preferences
                    stageIndex = adventure.stage ?: 0
                }
                if (active) draw()
            }
        }
    }

    private fun persist(next: AdventureSave, afterSave: () -> Unit = {}) {
        val previous = save
        submit({ store.update(previous, next) }) {
            save = it
            afterSave()
        }
    }

    private fun <T> submit(write: () -> T, onSaved: (T) -> Unit) {
        if (saving) return
        saving = true
        worker.execute {
            val result = runCatching(write)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saving = false
                storageError = result.isFailure
                result.onSuccess(onSaved)
                if (active) draw()
            }
        }
    }

    private fun availableWidthDp(): Int {
        val pixels = if (android.os.Build.VERSION.SDK_INT >= 30)
            windowManager.currentWindowMetrics.bounds.width() else window.decorView.width
        return if (pixels > 0) (pixels / resources.displayMetrics.density).toInt()
            else resources.configuration.screenWidthDp
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun panel(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), 0x336E7198)
    }

    private fun label(parent: LinearLayout, value: String, size: Float, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = if (settings.largeText) size * 1.2f else size
            setTextColor(ink)
            if (bold) setTypeface(null, Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1.08f)
            setPadding(0, dp(4), 0, dp(8))
        })
    }

    private fun button(parent: LinearLayout, value: String, enabled: Boolean = true, primary: Boolean = false, icon: GameIcon? = null, action: () -> Unit) {
        val params = if (parent.orientation == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams(0, -2, 1f)
        } else {
            LinearLayout.LayoutParams(-1, -2)
        }
        params.setMargins(dp(4), dp(4), dp(4), dp(4))
        parent.addView(Button(this).apply {
            text = value
            textSize = if (settings.largeText) 20f else 17f
            minHeight = dp(if (icon != null) 94 else 58)
            minimumWidth = 0
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            isEnabled = enabled
            setTextColor(if (enabled) Color.WHITE else ink)
            icon?.let {
                setCompoundDrawables(null, GameTabIcon(it, 0xFFBCEFFF.toInt()).apply {
                    setBounds(0, 0, dp(36), dp(36))
                }, null, null)
                compoundDrawablePadding = dp(4)
            }
            backgroundTintList = null
            stateListAnimator = null
            background = controlBackground(enabled, false, primary)
            isFocusable = enabled
            setOnFocusChangeListener { _, focused ->
                background = controlBackground(enabled, focused, primary)
            }
            setOnClickListener { if (canInteract() && !saving) action() }
        }, params)
    }

    private fun controlBackground(enabled: Boolean, focused: Boolean, primary: Boolean = false, radius: Int = 18): RippleDrawable =
        GameUi.control(this, enabled, focused, primary, radius)

    private fun navigation(parent: LinearLayout) {
        val tabs = listOf("홈" to GameIcon.HOME, "모험" to GameIcon.MAP,
            "친구들" to GameIcon.GIFT, "도감" to GameIcon.BOOK, "설정" to GameIcon.SETTINGS)
        val selected = when (page) {
            "퍼즐", "AI 친구" -> "모험"
            "차량" -> "설정"
            "동요 앨범", "놀이 안내" -> "홈"
            else -> page
        }
        val tabHeight = if (settings.largeText) 96 else 84
        val iconSize = 30
        tabs.forEach { (tab, icon) ->
            val current = selected == tab
            parent.addView(Button(this).apply {
                text = tab
                isAllCaps = false
                textSize = if (settings.largeText) 18f else 15f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                minimumWidth = 0
                setPadding(dp(2), dp(10), dp(2), dp(8))
                val color = if (current) 0xFFAAEEFF.toInt() else 0xFFD2CBEF.toInt()
                setTextColor(color)
                setCompoundDrawables(null, GameTabIcon(icon, color).apply {
                    setBounds(0, 0, dp(iconSize), dp(iconSize))
                }, null, null)
                compoundDrawablePadding = dp(7)
                isSelected = current
                stateListAnimator = null
                background = navigationBackground(current, false)
                setOnFocusChangeListener { _, focused -> background = navigationBackground(current, focused) }
                setOnClickListener { if (canInteract() && !saving && page != tab) navigate(tab) }
            }, LinearLayout.LayoutParams(0, dp(tabHeight), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
    }

    private fun navigationBackground(selected: Boolean, focused: Boolean): RippleDrawable {
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (selected) intArrayOf(0xFF353A69.toInt(), 0xFF252C51.toInt())
            else intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT),
        ).apply {
            cornerRadius = dp(12).toFloat()
            if (focused) setStroke(dp(2), 0xFFFFD57E.toInt())
        }
        return RippleDrawable(ColorStateList.valueOf(0x448FBDFF), fill, panel(Color.WHITE))
    }

    private fun image(parent: LinearLayout, resource: Int, description: String, height: Int) {
        parent.addView(ImageView(this).apply {
            setImageResource(resource)
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(-1, dp(height)))
    }

    private fun navigate(target: String) {
        spokenQuestion = null
        cancelAi()
        stopVoice()
        if (page == "차량") clearSnapshot()
        page = target
        turnPuzzle = null
        previewItem = null
        unopenedRewardStage = null
        scroll = null
        feedback = ""
        draw()
    }

    private fun draw() {
        idle?.cancel()
        idle = null
        val previousScroll = scroll?.scrollY ?: 0
        root = vertical().apply {
            setBackgroundColor(cream)
            setPadding(dp(20), dp(12), dp(20), dp(8))
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(
                    dp(20) + insets.systemWindowInsetLeft,
                    dp(12) + insets.systemWindowInsetTop,
                    dp(20) + insets.systemWindowInsetRight,
                    dp(8) + insets.systemWindowInsetBottom,
                )
                insets
            }
        }
        if (!restrictionState.allowsGame) {
            label(root, "TAMACAPE", 25f, true)
            label(root, if (restrictionState == ParkingAccess.RESTRICTED) {
                "주행 제한으로 게임이 일시정지됐어요. 주차 후 이어서 플레이할 수 있어요."
            } else {
                "차량의 주차 상태를 확인하고 있어요."
            }, 20f)
            setContentView(root)
            return
        }
        if (loading || storageError) {
            label(root, if (loading) "진행을 불러오는 중…" else "진행을 저장하거나 불러오지 못했어요.", 21f)
            if (storageError) button(root, "다시 불러오기") { loadSave(); draw() }
            setContentView(root)
            return
        }
        val stats = save.progress
        val hud = (if (availableWidthDp() < 680) vertical() else horizontal()).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.rgb(29, 32, 64))
            setPadding(dp(15), dp(7), dp(15), dp(5))
        }
        root.addView(hud, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        hud.addView(TextView(this).apply {
            text = "TAMACAPE"
            textSize = 21f
            setTextColor(0xFFBCEFFF.toInt())
            setTypeface(null, Typeface.BOLD)
        }, if (hud.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f)
            else LinearLayout.LayoutParams(-1, -2))
        label(hud, "Lv.${stats.level}   ·   XP ${stats.xp}   ·   코인 ${stats.coins}   ·   별 ${save.totalStars}", 16f, true)
        if (settings.demo) label(root, "DEMO DATA · 차량 샘플 모드", 14f, true)
        val body = vertical()
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }
        scroll = scroller
        if (page in listOf("홈", "모험", "친구들", "퍼즐", "설정", "도감")) body.gravity = Gravity.CENTER_VERTICAL
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = horizontal().apply {
            gravity = Gravity.CENTER
            background = panel(Color.rgb(25, 28, 57))
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        root.addView(nav)
        navigation(nav)
        when (page) {
            "모험" -> map(body)
            "퍼즐" -> puzzle(body)
            "친구들" -> wardrobe(body)
            "AI 친구" -> familyAiPage(body)
            "도감" -> guide(body)
            "설정" -> settingsPage(body)
            "차량" -> vehiclePage(body)
            "동요 앨범" -> songAlbumPage(body)
            "놀이 안내" -> introduction(body)
            else -> home(body)
        }
        setContentView(root)
        if (previousScroll > 0) scroller.post { scroller.scrollTo(0, previousScroll) }
    }

    private fun columns(parent: LinearLayout): Pair<LinearLayout, LinearLayout> {
        val landscape = availableWidthDp() >= (if (settings.largeText) 900 else 680)
        val row = if (landscape) horizontal() else vertical()
        if (landscape) row.gravity = Gravity.TOP
        parent.addView(row)
        val left = vertical().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panel(lavender)
        }
        val right = vertical().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = panel(Color.rgb(29, 32, 62))
        }
        for (column in listOf(left, right)) {
            row.addView(column, (if (landscape) LinearLayout.LayoutParams(0, -2, 1f)
                else LinearLayout.LayoutParams(-1, -2)).apply {
                if (landscape) marginEnd = dp(6) else bottomMargin = dp(8)
            })
        }
        return left to right
    }

    private fun avatar(parent: LinearLayout, height: Int, friend: Companion = save.activeCompanion, animate: Boolean) {
        val growth = save.progress.growth
        val wall = when (growth) {
            MobiGrowth.SPROUT -> Color.rgb(231, 222, 251)
            MobiGrowth.EXPLORER -> Color.rgb(209, 238, 229)
            MobiGrowth.SAFETY_EXPLORER -> Color.rgb(191, 232, 217)
            MobiGrowth.MASTER -> Color.rgb(253, 219, 143)
        }
        val room = FrameLayout(this).apply {
            if (!animate) background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(wall, Color.rgb(255, 242, 218)),
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        parent.addView(room, LinearLayout.LayoutParams(-1, dp(height)))
        val width = resources.configuration.screenWidthDp
        val availableWidth = if (width >= 680) width / 2 - 56 else width - 68
        val avatarSize = dp(minOf((height * 0.82f).toInt(), availableWidth.coerceAtLeast(100)))
        val figure = FrameLayout(this)
        room.addView(figure, FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.CENTER))
        figure.addView(ImageView(this).apply {
            setImageResource(friend.art)
            contentDescription = friend.name
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(-1, -1))
        if (animate && active) {
            idle = ObjectAnimator.ofFloat(figure, "translationY", 0f, -dp(6).toFloat(), 0f).apply {
                duration = 2700
                repeatCount = 1
                start()
            }
        }
    }

    private fun introduction(parent: LinearLayout) {
        val (welcome, steps) = columns(parent)
        avatar(welcome, 260, animate = true)
        label(welcome, "우리 가족의 첫 모험", 30f, true)
        label(welcome, "차가 안전하게 주차된 동안 부모님과 함께 놀아요", 20f)
        label(steps, "1  그림을 보고 이야기해요", 23f, true)
        label(steps, "아이의 생각을 먼저 들어 주세요. 모르는 문제는 힌트를 함께 봐도 괜찮아요.", 18f)
        label(steps, "2  안전 약속을 알아봐요", 23f, true)
        label(steps, "틀려도 별이 줄어들지 않아요. 실제 차를 조작하지 않고 화면 속 퀴즈로 배워요.", 18f)
        label(steps, "3  친구와 다시 모험해요", 23f, true)
        label(steps, "완료한 퀴즈는 언제든 다시 풀 수 있어요. 보상은 처음 완료할 때 한 번 받아요.", 18f)
        label(steps, "AI와 음성은 선택 기능이에요. 연결 없이도 모든 기본 퀴즈를 즐길 수 있어요.", 17f)
        button(steps, "함께 시작하기", primary = true) {
            submit({ runBlocking { settingsStore.write(settings.copy(introductionSeen = true)) } }) {
                settings = settings.copy(introductionSeen = true)
                page = "모험"
                scroll = null
            }
        }
        button(steps, "홈으로") { navigate("홈") }
    }

    private fun home(parent: LinearLayout) {
        val scene = FrameLayout(this).apply {
            background = panel(lavender)
            clipToOutline = true
        }
        parent.addView(scene, LinearLayout.LayoutParams(-1, -2))
        scene.addView(ImageView(this).apply {
            setImageResource(R.drawable.mobi_room_scene)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        val content = vertical().apply { setPadding(dp(18), dp(16), dp(18), dp(16)) }
        scene.addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val (room, details) = columns(content)
        room.background = null
        details.background = panel(0xEB22213F.toInt())
        details.gravity = Gravity.CENTER_VERTICAL
        val stacked = availableWidthDp() < (if (settings.largeText) 900 else 680)
        val heroHeight = if (stacked) 180 else
            (resources.configuration.screenHeightDp * 0.45f).toInt().coerceIn(220, 440)
        avatar(room, heroHeight, animate = true)
        label(details, "우리 가족의 친구", 36f, true)
        val stats = save.progress
        label(details, "${save.activeCompanion.name}  ·  Lv.${stats.level}", 22f, true)
        label(details, if (stats.cleared >= stages.size) "모든 모험을 함께 해냈어요!" else "오늘도 같이 놀아요!", 20f)
        val nextXp = stats.nextLevelXp
        val baseXp = stats.levelStartXp
        label(details, "다음 레벨까지 ${nextXp - stats.xp} XP", 16f)
        details.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = nextXp - baseXp
            progress = stats.xp - baseXp
            progressTintList = ColorStateList.valueOf(mint)
            contentDescription = "누적 경험치 ${stats.xp} / $nextXp"
        }, LinearLayout.LayoutParams(-1, dp(14)).apply { bottomMargin = dp(12) })
        label(details, "엄마·아빠와 함께 이야기하고 퀴즈를 풀어요", 18f)
        val activeStage = save.stage?.takeIf { save.checkpoint(it).puzzle < stages[it].puzzles.size }
        button(details, when {
            !settings.introductionSeen -> "처음 함께 놀기"
            activeStage != null -> "모험 이어하기"
            stats.cleared >= stages.size -> "좋아하는 퀴즈 다시 풀기"
            else -> "모험하기"
        }, primary = true) {
            if (!settings.introductionSeen) navigate("놀이 안내")
            else if (activeStage != null) openStage(activeStage) else navigate("모험")
        }
        val newFriend = companions.firstOrNull {
            !save.hasCompanion(it) && stats.cleared >= it.clears && stats.coins >= it.cost
        }
        if (newFriend != null) button(details, "${newFriend.name}를 만날 수 있어요 · 친구 앨범") {
            navigate("친구들")
            previewItem = newFriend.id
            draw()
        }
        button(details, "동요 앨범") { navigate("동요 앨범") }
        if (stats.cleared >= stages.size) button(details, "함께 배운 안전 약속 보기") { navigate("도감") }
    }

    private fun map(parent: LinearLayout) {
        val stats = save.progress
        val mapHeader = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        parent.addView(mapHeader)
        val heading = vertical().apply { setPadding(dp(12), 0, dp(12), 0) }
        mapHeader.addView(heading, LinearLayout.LayoutParams(0, -2, 1.6f))
        label(heading, "별빛 차고의 비밀", 29f, true)
        label(heading, "완료 ${stats.cleared} / ${stages.size}  ·  별 ${save.totalStars}개", 16f)
        val board = FrameLayout(this).apply {
            background = panel(lavender)
            clipToOutline = true
        }
        val mapHeight = (resources.configuration.screenHeightDp * 0.59f).toInt().coerceIn(270, 680)
        parent.addView(board, LinearLayout.LayoutParams(-1, dp(mapHeight)))
        board.addView(ImageView(this).apply {
            setImageResource(R.drawable.world_scene)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        val locations = listOf(0.16f to 0.69f, 0.40f to 0.58f, 0.54f to 0.78f,
            0.77f to 0.63f, 0.83f to 0.29f)
        stages.forEachIndexed { index, stage ->
            val grade = save.starsFor(index)
            val unlocked = stats.isUnlocked(index)
            val current = index == stats.cleared
            val status = when {
                grade > 0 -> "완료 · 별 ${grade}개"
                unlocked -> "열림"
                else -> "잠김"
            }
            val node = Button(this).apply {
                text = if (grade > 0) "${index + 1}\n${"★".repeat(grade)}" else "${index + 1}"
                contentDescription = "${index + 1}. ${stage.name} · $status"
                isAllCaps = false
                isEnabled = true
                minimumWidth = 0
                minHeight = 0
                textSize = 19f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                setTextColor(if (grade > 0) 0xFFFFDF89.toInt() else ink)
                setTypeface(null, Typeface.BOLD)
                background = controlBackground(unlocked, false, current, radius = 32)
                setOnFocusChangeListener { _, focused ->
                    background = controlBackground(unlocked, focused, current, radius = 32)
                }
                setOnClickListener {
                    if (!canInteract() || saving) return@setOnClickListener
                    if (!unlocked) {
                        showParkedDialog(AlertDialog.Builder(this@MainActivity)
                            .setTitle("아직 잠긴 모험이에요")
                            .setMessage("${stages[index - 1].name}을 완료하면 열려요.")
                            .setPositiveButton("확인", null))
                        return@setOnClickListener
                    }
                    showStageCard(index)
                }
            }
            val size = dp((mapHeight * 0.17f).toInt().coerceIn(64, 104))
            val (x, y) = locations[index]
            board.addView(node, FrameLayout.LayoutParams(size, size))
            board.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                node.translationX = (view.width * x - size / 2f).coerceIn(0f, (view.width - size).coerceAtLeast(0).toFloat())
                node.translationY = (view.height * y - size / 2f).coerceIn(0f, (view.height - size).coerceAtLeast(0).toFloat())
            }
        }
    }

    private fun stageArtwork(parent: LinearLayout, stage: Int, compact: Boolean = false) {
        val art = listOf(R.drawable.garage_scene, R.drawable.stage_fog, R.drawable.stage_town,
            R.drawable.stage_workshop, R.drawable.stage_castle)[stage]
        parent.addView(ImageView(this).apply {
            setImageResource(art)
            contentDescription = stages[stage].name + "의 이야기 장면"
            maxHeight = dp(if (compact) 170 else 300)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = panel(lavender)
            clipToOutline = true
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun showStageCard(index: Int) {
        val stage = stages[index]
        val checkpoint = save.checkpoint(index)
        val card = vertical().apply { setPadding(dp(24), dp(20), dp(24), dp(20)) }
        label(card, "가족 퀴즈  ·  CHAPTER ${index + 1}", 16f, true)
        stageArtwork(card, index, compact = true)
        label(card, stage.name, 27f, true)
        label(card, stage.intro, 18f)
        label(card, "엄마·아빠와 함께 ${stage.puzzles.size}문제를 생각해요", 18f)
        label(card, "첫 완료 선물  ·  100 XP  /  100 코인  /  ★★★", 16f, true)
        label(card, "도움을 받아도 괜찮아요. 끝까지 함께 풀면 별 3개!", 15f)
        button(card, if (checkpoint.puzzle == stage.puzzles.size) "완료한 퀴즈 보기" else
            if (checkpoint.puzzle > 0 || checkpoint.selection.isNotEmpty()) "이어서 함께 풀기" else "함께 시작하기", primary = true) {
            openDialog?.dismiss()
            openStage(index)
        }
        if (checkpoint.puzzle > 0 || checkpoint.selection.isNotEmpty()) {
            button(card, "처음부터 다시 풀기") { openDialog?.dismiss(); restartStage(index) }
        }
        button(card, "지도 돌아가기") { openDialog?.dismiss() }
        showParkedDialog(AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(card) }))
    }

    private fun turnPuzzleControls(dash: LinearLayout, controls: LinearLayout, practice: SignalPractice, onComplete: () -> Unit) {
        fun selectSignal(signal: TurnSignal) {
            val next = practice.select(signal)
            turnPuzzle = next
            if (next.step == SignalStep.COMPLETE) onComplete() else draw()
        }
        label(dash, "가상 계기판", 25f, true)
        val indicators = horizontal().apply {
            background = panel(Color.rgb(36, 44, 63))
            gravity = Gravity.CENTER
        }
        dash.addView(indicators, LinearLayout.LayoutParams(-1, dp(92)))
        listOf(TurnSignal.LEFT to "⬅", TurnSignal.RIGHT to "➡").forEach { (side, glyph) ->
            val on = practice.signal == side
            val arrow = TextView(this).apply {
                text = glyph
                textSize = 46f
                setTextColor(if (on) Color.rgb(158, 245, 191) else Color.rgb(118, 128, 147))
                gravity = Gravity.CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            indicators.addView(arrow, LinearLayout.LayoutParams(0, -1, 1f))
            if (on) {
                idle = ObjectAnimator.ofFloat(arrow, "alpha", 1f, 0.30f, 1f, 0.30f, 1f).apply {
                    duration = 1400
                    start()
                }
            }
        }
        label(dash, if (practice.signal == TurnSignal.OFF) "방향지시등 꺼짐"
            else "${practice.signal.title} 방향지시등 켜짐", 18f, true)

        label(controls, "보행자가 모두 건넜어요", 25f, true)
        when (practice.step) {
            SignalStep.APPROACH -> {
                label(controls, "가상 교차로에서 오른쪽으로 돌 예정이에요. 어느 쪽 깜빡이를 켤까요?", 20f)
                signalSwitch(controls, practice.signal, ::selectSignal)
                if (practice.signal == TurnSignal.LEFT) {
                    label(controls, "가려는 방향과 불이 켜진 쪽을 비교해 보세요.", 17f)
                }
                button(controls, "주변 확인 후 우회전", practice.signal == TurnSignal.RIGHT) {
                    turnPuzzle = practice.turn()
                    draw()
                }
            }
            SignalStep.TURNED -> {
                label(controls, "우회전을 마쳤어요. 이제 깜빡이를 꺼 주세요.", 20f)
                signalSwitch(controls, practice.signal, ::selectSignal)
            }
            SignalStep.COMPLETE -> Unit
        }
    }

    private fun signalSwitch(parent: LinearLayout, current: TurnSignal, select: (TurnSignal) -> Unit) {
        val switches = horizontal()
        parent.addView(switches)
        listOf(TurnSignal.LEFT to "왼쪽 켜기", TurnSignal.OFF to "끄기", TurnSignal.RIGHT to "오른쪽 켜기")
            .forEach { (signal, title) -> button(switches, title, current != signal) { select(signal) } }
    }

    private fun restartStage(index: Int) {
        val checkpoint = save.checkpoint(index)
        val inProgress = checkpoint.puzzle < stages[index].puzzles.size &&
            (checkpoint.puzzle > 0 || checkpoint.selection.isNotEmpty() || checkpoint.explained)
        if (inProgress) {
            showParkedDialog(AlertDialog.Builder(this)
                .setTitle("처음부터 다시 도전할까요?")
                .setMessage("이번 도전의 답안과 진행이 초기화돼요. 기존 최고 별 기록과 받은 보상은 유지돼요.")
                .setNegativeButton("계속 풀기", null)
                .setPositiveButton("처음부터") { _, _ -> if (canInteract() && !saving) beginStageAgain(index) })
        } else beginStageAgain(index)
    }

    private fun beginStageAgain(index: Int) {
        spokenQuestion = null
        stopVoice()
        persist(save.restartStage(index)) {
            stageIndex = index
            page = "퍼즐"
            scroll = null
            feedback = ""
            turnPuzzle = null
            unopenedRewardStage = null
        }
    }

    private fun openStage(index: Int) {
        spokenQuestion = null
        stopVoice()
        persist(save.copy(stage = index)) {
            stageIndex = index
            page = "퍼즐"
            scroll = null
            feedback = ""
            turnPuzzle = null
            unopenedRewardStage = null
        }
    }

    private fun learningFeedback(parent: LinearLayout, title: String, message: String, success: Boolean) {
        val card = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(if (success) 0xFF284B50.toInt() else 0xFF3C355A.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10); bottomMargin = dp(10)
        })
        card.addView(ImageView(this).apply {
            setImageResource(if (success) save.activeCompanion.celebrateArt else save.activeCompanion.thinkingArt)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(72), dp(80)).apply { marginEnd = dp(12) })
        val copy = vertical()
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        label(copy, title, 22f, true)
        label(copy, message, 18f)
    }

    private fun completionStars(parent: LinearLayout, grade: Int) {
        val stars = TextView(this).apply {
            text = "★".repeat(grade) + "☆".repeat(3 - grade)
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFDF89.toInt())
            contentDescription = "최고 기록 별 ${grade}개"
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        parent.addView(stars, LinearLayout.LayoutParams(-1, -2))
        if (active && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            idle = ObjectAnimator.ofPropertyValuesHolder(stars,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.85f, 1.06f, 1f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.85f, 1.06f, 1f)).apply {
                duration = 650
                start()
            }
        }
    }

    private fun rewardChest(parent: LinearLayout) {
        val gold = Color.rgb(248, 187, 62)
        val violet = Color.rgb(108, 85, 170)
        val chest = FrameLayout(this).apply {
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        parent.addView(chest, LinearLayout.LayoutParams(dp(124), dp(82)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        chest.addView(android.view.View(this).apply { background = panel(violet) },
            FrameLayout.LayoutParams(dp(110), dp(58), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        chest.addView(android.view.View(this).apply { background = panel(gold) },
            FrameLayout.LayoutParams(dp(124), dp(29), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(10)
            })
        chest.addView(android.view.View(this).apply { background = panel(gold) },
            FrameLayout.LayoutParams(dp(16), dp(70), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    private fun puzzle(parent: LinearLayout) {
        val stage = stages[stageIndex]
        val checkpoint = save.checkpoint(stageIndex)
        val index = checkpoint.puzzle
        val (scene, controls) = columns(parent)
        label(scene, stage.name, 27f, true)
        label(scene, stage.intro, 18f)
        stageArtwork(scene, stageIndex)
        label(scene, "${save.activeCompanion.name}와 함께 · 화면 속 상황을 보고 이야기해요", 16f)
        if (index == stage.puzzles.size) {
            label(controls, "스테이지 완료!", 26f, true)
            val grade = save.starsFor(stageIndex)
            if (unopenedRewardStage == stageIndex) {
                rewardChest(controls)
                label(controls, "별빛 보상 상자", 21f, true)
                button(controls, "보상 상자 열기", primary = true) {
                    unopenedRewardStage = null
                    playEffect(ToneGenerator.TONE_PROP_ACK, 180)
                    draw()
                }
            } else {
                completionStars(controls, grade)
                learningFeedback(controls, "${save.activeCompanion.name}와 함께 해냈어요!",
                    "서로의 생각을 듣고 끝까지 함께한 가족에게 박수를 보내요.", success = true)
                label(controls, "저장된 최고 기록 · 별 ${grade}개", 18f)
                if (grade < 3) label(controls, "이전 버전에서 모은 별이에요. 다시 끝까지 풀면 별 3개로 갱신돼요.", 16f)
                label(controls, "함께 끝까지 생각한 선물이에요 · 다시 풀어도 보상은 한 번만 받아요", 16f)
                if (stageIndex + 1 in stages.indices && save.progress.isUnlocked(stageIndex + 1)) {
                    button(controls, "다음 스테이지", primary = true) { openStage(stageIndex + 1) }
                }
                button(controls, "처음부터 재도전", primary = stageIndex == stages.lastIndex) { restartStage(stageIndex) }
                button(controls, "우리만의 동요 만들기") { openFamilyAi("song", stageIndex, 0) }
                val availableFriend = companions.firstOrNull {
                    !save.hasCompanion(it) && save.progress.cleared >= it.clears && save.progress.coins >= it.cost
                }
                if (availableFriend != null) button(controls, "새 친구 ${availableFriend.name} 만나러 가기") {
                    navigate("친구들")
                    previewItem = availableFriend.id
                    draw()
                }
                button(controls, "배운 안전 약속 다시 보기") { navigate("도감") }
                button(controls, "모험 지도로") { navigate("모험") }
            }
            return
        }

        val challenge = stage.puzzles[index]
        val hasTurn = stageIndex == 2 && index == 0
        fun explain() {
            stopVoice()
            persist(save.withCheckpoint(stageIndex, checkpoint.copy(explained = true))) {
                turnPuzzle = null
                playEffect(ToneGenerator.TONE_PROP_BEEP, 100)
            }
        }
        val turn = turnPuzzle
        if (hasTurn && !checkpoint.explained && turn != null) {
            turnPuzzleControls(scene, controls, turn, ::explain)
            return
        }
        val selected = checkpoint.selection.toMutableList()
        label(controls, "퍼즐 ${index + 1} / ${stage.puzzles.size}", 16f, true)
        controls.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = stage.puzzles.size
            progress = index
            progressTintList = ColorStateList.valueOf(0xFF9BE8D3.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(lavender)
            contentDescription = "퍼즐 ${stage.puzzles.size}개 중 ${index}개 완료"
        }, LinearLayout.LayoutParams(-1, dp(6)))
        label(controls, "함께 생각해 볼까요?", 17f)
        label(controls, challenge.prompt, 21f, true)
        if (checkpoint.explained) {
            learningFeedback(controls, "함께 알아냈어요!", challenge.explanation, success = true)
            label(controls, "엄마·아빠와 이야기해요", 18f, true)
            label(controls, familyQuestions[stageIndex][index], 19f)
            button(controls, "AI 친구와 더 이야기하기") { openFamilyAi("coach", stageIndex, index) }
            referenceButton(controls, stageIndex, index)
            button(controls, "계속") { advance(index) }
            return
        }
        fun select(option: Int) {
            stopVoice()
            feedback = ""
            if (!selected.remove(option)) {
                if (challenge.answer.size == 1) selected.clear()
                selected.add(option)
            }
            persist(save.withCheckpoint(stageIndex, checkpoint.copy(selection = selected.toList())))
        }

        val voiceEligible = !challenge.garageScene && !challenge.alleyScene && !challenge.weatherScene
        if (settings.voiceQuiz && spokenQuestion != (stageIndex to index)) {
            val stage = stageIndex
            parent.post {
                if (quizQuestionActive(stage, index) && spokenQuestion != (stage to index))
                    speakQuizQuestion(stage, index, challenge, voiceEligible)
            }
        }
        if (voiceEligible || settings.voiceQuiz) {
            val voiceRow = horizontal()
            controls.addView(voiceRow)
            button(voiceRow, when {
                voiceListening || speechId != null -> "음성 취소"
                settings.voiceQuiz -> "질문 다시 듣기"
                else -> "말로 답하기"
            }) {
                if (voiceListening || speechId != null) { stopVoice(); draw() }
                else if (settings.voiceQuiz) speakQuizQuestion(stageIndex, index, challenge, voiceEligible)
                else listenToQuizAnswer(stageIndex, index)
            }
            if (voiceStatus.isNotEmpty()) label(controls, voiceStatus, 16f)
            if (voiceText.isNotEmpty() && !voiceListening) {
                label(controls, "“$voiceText”", 20f, true)
                val option = spokenOption(voiceText, challenge.options)
                if (option != null) button(controls, "이 답 선택하기", primary = true) {
                    stopVoice()
                    select(option)
                } else label(controls, "답과 정확히 연결되지 않았어요. 아래에서 골라 주세요.", 16f)
                button(controls, "인식한 말 지우기") { stopVoice(); draw() }
            }
        }
        if (challenge.ordered) {
            label(controls, "순서대로 선택 · 다시 누르면 선택을 취소해요", 15f)
            label(controls, if (selected.isEmpty()) "아직 선택한 단계가 없어요" else
                selected.mapIndexed { order, option -> "${order + 1}. ${challenge.options[option]}" }.joinToString("  →  "), 17f, true)
        } else if (challenge.answer.size > 1 && !challenge.weatherScene && !challenge.alleyScene) {
            label(controls, "해당하는 항목을 모두 선택해 주세요", 15f)
        }
        if (challenge.weatherScene) {
            weatherControls(controls, selected, ::select)
        } else if (challenge.garageScene) {
            garageControls(controls, 1 in selected) {
                feedback = ""
                select(1)
            }
        } else if (challenge.alleyScene) {
            alleyHotspots(controls, challenge.options, selected, ::select)
        } else {
            challenge.options.forEachIndexed { option, text ->
                val position = selected.indexOf(option)
                val prefix = when {
                    position < 0 -> ""
                    challenge.ordered -> "${position + 1}. "
                    else -> "✓ "
                }
                button(controls, "${option + 1}번 · " + prefix + text, primary = position >= 0) { select(option) }
            }
        }
        if (challenge.ordered) {
            val editActions = horizontal()
            controls.addView(editActions)
            button(editActions, "한 단계 되돌리기", selected.isNotEmpty()) {
                feedback = ""
                persist(save.withCheckpoint(stageIndex, checkpoint.copy(selection = selected.dropLast(1))))
            }
            button(editActions, "선택 비우기", selected.isNotEmpty()) {
                feedback = ""
                persist(save.withCheckpoint(stageIndex, checkpoint.copy(selection = emptyList())))
            }
        }
        if (feedback.isNotEmpty()) learningFeedback(controls, "함께 생각해요", feedback, success = false)
        val actions = horizontal()
        controls.addView(actions)
        button(actions, "함께 생각하기") {
            if (checkpoint.usedHint) {
                feedback = challenge.hint
                draw()
            } else {
                persist(save.withCheckpoint(stageIndex, checkpoint.copy(usedHint = true))) {
                    feedback = challenge.hint
                }
            }
        }
        button(actions, "확인", if (challenge.garageScene) 1 in selected else selected.isNotEmpty(), primary = true) {
            if (challenge.accepts(selected)) {
                if (hasTurn) {
                    turnPuzzle = SignalPractice()
                    draw()
                } else {
                    explain()
                }
            } else {
                val message = "괜찮아요! 엄마·아빠와 다시 생각해 봐요. ${challenge.hint}"
                if (checkpoint.madeMistake) {
                    feedback = message
                    draw()
                } else {
                    persist(save.withCheckpoint(stageIndex, checkpoint.copy(madeMistake = true))) {
                        feedback = message
                    }
                }
            }
        }
    }

    private fun weatherControls(parent: LinearLayout, selected: List<Int>, select: (Int) -> Unit) {
        val slowed = 0 in selected
        val spaced = 1 in selected
        val dashboard = vertical().apply {
            background = panel(lavender)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        parent.addView(dashboard)
        label(dashboard, "가상 운전석 · 안개 구간", 18f, true)
        dashboard.addView(FogRoadScene(this, slowed, spaced),
            LinearLayout.LayoutParams(-1, dp(148)))
        label(dashboard, if (slowed && spaced) "준비 완료 · 확보한 간격을 유지해요" else
            "시야가 짧아요 · 속도와 차간거리를 살펴 주세요", 16f)
        val switches = horizontal()
        dashboard.addView(switches)
        button(switches, if (slowed) "✓ 감속 완료" else "가속 페달에서 발 떼기", primary = slowed) {
            feedback = ""
            select(0)
        }
        button(switches, if (spaced) "✓ 간격 확보" else "앞차와 간격 넓히기", primary = spaced) {
            feedback = ""
            select(1)
        }
    }

    private fun garageControls(parent: LinearLayout, doorClosed: Boolean, toggleDoor: () -> Unit) {
        val dashboard = vertical().apply {
            background = panel(lavender)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        parent.addView(dashboard)
        dashboard.addView(GarageCar(this, doorClosed) { if (canInteract() && !saving) toggleDoor() },
            LinearLayout.LayoutParams(-1, dp(148)))
        label(dashboard, if (doorClosed) "계기판 · 도어 닫힘" else "계기판 · 도어 열림", 17f, true)
        val controls = horizontal()
        dashboard.addView(controls)
        button(controls, "안전벨트 확인", icon = GameIcon.BELT) { feedback = "안전벨트는 이미 채워져 있어요."; draw() }
        button(controls, if (doorClosed) "가상 도어 열기" else "가상 도어 닫기", icon = GameIcon.DOOR) {
            toggleDoor()
        }
        button(controls, "경고등 확인", icon = GameIcon.WARNING) {
            feedback = if (doorClosed) "도어 열림 표시가 꺼졌어요." else "도어 열림 표시가 켜져 있어요."
            draw()
        }
    }

    private fun alleyHotspots(
        parent: LinearLayout,
        options: List<String>,
        selected: List<Int>,
        select: (Int) -> Unit,
    ) {
        val board = FrameLayout(this)
        parent.addView(board, LinearLayout.LayoutParams(-1, dp(172)))
        board.addView(AlleyScene(this) { index ->
            if (canInteract() && !saving) select(index)
        }.apply {
            background = panel(lavender)
            clipToOutline = true
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        val markers = horizontal().apply { gravity = Gravity.CENTER }
        board.addView(markers, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        options.forEachIndexed { index, option ->
            button(markers, if (index in selected) "✓ $option" else option) { select(index) }
        }
    }

    private fun referenceButton(parent: LinearLayout, stage: Int, puzzle: Int) {
        showReferences(parent, learningReferences[stage][puzzle])
    }

    private fun showReferences(parent: LinearLayout, references: List<LearningReference>) {
        button(parent, "해설 출처") {
            val text = references.joinToString("\n\n") {
                "${it.title}\n${it.url}"
            }
            showParkedDialog(AlertDialog.Builder(this)
                .setTitle("안전운전 해설 출처")
                .setMessage("일반 안전운전 참고 자료 · 확인 2026-11-12\n\n$text")
                .setPositiveButton("닫기", null))
        }
    }

    private fun quizQuestionActive(stage: Int, index: Int): Boolean =
        canInteract() && page == "퍼즐" && stageIndex == stage && turnPuzzle == null &&
            save.checkpoint(stage).let { it.puzzle == index && !it.explained }

    private fun listenToQuizAnswer(stage: Int, index: Int) {
        if (!quizQuestionActive(stage, index)) return
        voiceText = ""
        voiceStatus = "듣고 있어요 · 답의 번호나 내용을 말해 주세요"
        voiceListening = true
        draw()
        familyVoice.start({ text, complete ->
            if (quizQuestionActive(stage, index)) {
                voiceText = text
                voiceListening = !complete
                voiceStatus = if (complete) "이렇게 들었어요 · 맞는지 확인해 주세요" else "듣고 있어요…"
                draw()
            }
        }, { message ->
            if (quizQuestionActive(stage, index)) {
                voiceListening = false
                voiceStatus = message
                draw()
            }
        })
    }

    private fun speakQuizQuestion(stage: Int, index: Int, challenge: Puzzle, listen: Boolean) {
        if (!quizQuestionActive(stage, index) || !settings.voiceQuiz) return
        stopVoice()
        spokenQuestion = stage to index
        val id = java.util.UUID.randomUUID().toString()
        speechId = id
        if (SongPlayback.playing) songCommand("pause")
        voiceStatus = "질문을 읽어 주고 있어요."
        fun finish(success: Boolean) {
            if (speechId != id || !quizQuestionActive(stage, index)) return
            speechId = null
            if (success && listen) listenToQuizAnswer(stage, index)
            else {
                voiceStatus = if (success) "화면에서 가상 조작을 해 보세요."
                    else "한국어 음성을 사용할 수 없어요. 화면에서 답을 선택할 수 있어요."
                draw()
            }
        }
        fun speak() {
            if (speechId != id || !quizQuestionActive(stage, index)) return
            val engine = quizTts
            if (quizTtsReady != true || engine == null) { finish(false); return }
            engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id) runOnUiThread { finish(true) } }
                @Deprecated("Android legacy listener callback")
                override fun onError(utteranceId: String?) { if (utteranceId == id) runOnUiThread { finish(false) } }
            })
            val text = if (listen) challenge.prompt + " " +
                challenge.options.mapIndexed { option, answer -> "${option + 1}번. $answer." }.joinToString(" ") +
                " 답의 번호나 내용을 말해 주세요." else challenge.prompt
            if (engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, id) !=
                android.speech.tts.TextToSpeech.SUCCESS) finish(false)
        }
        draw()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (speechId == id) { quizTts?.stop(); finish(false) }
        }, 30000)
        if (quizTts == null) {
            quizTts = android.speech.tts.TextToSpeech(this) { status ->
                runOnUiThread {
                    val engine = quizTts
                    quizTtsReady = status == android.speech.tts.TextToSpeech.SUCCESS &&
                        engine != null && engine.setLanguage(java.util.Locale.KOREAN) >= 0
                    speak()
                }
            }
        } else speak()
    }

    private fun advance(index: Int) {
        stopVoice()
        val checkpoint = save.checkpoint(stageIndex)
        if (checkpoint.puzzle != index || !checkpoint.explained) return
        val updated = save.advancePuzzle(stageIndex)
        val completed = updated.progress.cleared > save.progress.cleared
        persist(updated) {
            feedback = ""
            if (completed) unopenedRewardStage = stageIndex
        }
    }

    private fun wardrobe(parent: LinearLayout) {
        val (preview, catalog) = columns(parent)
        val friend = companions.firstOrNull { it.id == previewItem } ?: save.activeCompanion
        label(preview, "우리 가족의 친구들", 28f, true)
        avatar(preview, if (availableWidthDp() < 900) 180 else 280, friend, animate = true)
        label(preview, friend.name, 28f, true)
        label(preview, friend.description, 18f)
        val owned = save.hasCompanion(friend)
        val eligible = save.progress.cleared >= friend.clears
        val selected = save.activeCompanion.id == friend.id
        label(preview, if (owned) "우리 가족과 만난 친구" else
            "퀴즈 ${friend.clears}개 스테이지 완료 · ${friend.cost} 코인", 17f)
        button(preview, when {
            selected -> "함께하는 중"
            owned -> "함께하기"
            !eligible -> "모험에서 친구를 만나 보세요"
            save.progress.coins < friend.cost -> "코인 ${friend.cost - save.progress.coins}개 더 필요해요"
            else -> "${friend.cost} 코인으로 친구 만나기"
        }, !selected && (owned || eligible && save.progress.coins >= friend.cost), primary = true) {
            persist(save.chooseCompanion(friend.id))
        }
        label(catalog, "친구 앨범", 24f, true)
        label(catalog, "퀴즈를 함께 풀고 새로운 친구를 만나요", 17f)
        companions.forEach { item ->
            val card = horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = panel(if (item.id == friend.id) 0xFF494477.toInt() else lavender)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            catalog.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            card.addView(ImageView(this).apply {
                setImageResource(item.art)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(104), dp(112)))
            val detail = vertical()
            card.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            label(detail, item.name, 23f, true)
            label(detail, if (save.hasCompanion(item)) "만난 친구" else "${item.clears}스테이지 완료 · ${item.cost} 코인", 16f)
            button(detail, if (item.id == friend.id) "보고 있는 친구" else "만나 보기", item.id != friend.id) {
                previewItem = item.id
                draw()
            }
        }
    }

    private fun openFamilyAi(kind: String, stage: Int, puzzle: Int) {
        cancelAi()
        stopVoice()
        songPreview = null
        aiKind = kind
        aiStage = stage
        aiPuzzle = puzzle
        aiDraft = ""
        aiOutput = ""
        aiMessage = ""
        aiVoicePending = ""
        navigate("AI 친구")
    }

    private fun configureFamilyAi() {
        cancelAi()
        stopVoice()
        val card = vertical().apply { setPadding(dp(24), dp(20), dp(24), dp(20)) }
        label(card, "부모님 AI 연결 설정", 25f, true)
        label(card, "AI를 켜면 확인한 답변 텍스트와 퀴즈 번호·선택 친구를 중계 서버와 OpenAI에 전송해요. 이름·주소 같은 개인정보는 적지 마세요.", 17f)
        label(card, "동요를 만들면 연령대와 퀴즈 주제가 OpenAI에, 생성된 가사는 ElevenLabs에 전송돼요.", 16f)
        label(card, "앱은 원음·대화를 저장하지 않아요. 외부 서비스의 데이터 처리는 제공자 정책을 따라요. 연결 정보는 앱을 종료하면 지워져요.", 16f)
        val endpoint = android.widget.EditText(this).apply {
            hint = "중계 서버 주소 (https://…/v1/family)"
            setText(aiEndpoint)
            setTextColor(ink)
            setHintTextColor(0xFFBDB5D6.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        card.addView(endpoint)
        val access = android.widget.EditText(this).apply {
            hint = "부모님 접속 코드 · OpenAI API 키가 아니에요"
            setText(aiAccess)
            setTextColor(ink)
            setHintTextColor(0xFFBDB5D6.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false
        }
        card.addView(access)
        button(card, "전송 안내 확인 · 연결 설정 적용", primary = true) {
            val url = endpoint.text.toString().trim()
            val code = access.text.toString().trim()
            when {
                !validFamilyEndpoint(url) -> endpoint.error = "HTTPS 주소와 /v1/family 경로를 확인해 주세요."
                code.length !in 32..200 || code.startsWith("sk-") -> access.error = "API 키 대신 중계 서버용 접속 코드를 입력해 주세요."
                else -> {
                    aiEndpoint = url
                    aiAccess = code
                    aiMessage = "연결 설정을 적용했어요. 전송하면 서버 응답을 확인해요."
                    openDialog?.dismiss()
                    draw()
                }
            }
        }
        button(card, "AI 연결 해제") {
            aiEndpoint = FAMILY_AI_ENDPOINT
            aiAccess = ""
            songPreview = null
            latestDiscussion = null
            aiDraft = ""
            aiOutput = ""
            aiVoicePending = ""
            aiMessage = "AI 연결을 해제했어요."
            openDialog?.dismiss()
            draw()
        }
        button(card, "닫기") { openDialog?.dismiss() }
        showParkedDialog(AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(card) }))
    }

    private fun familyAiPage(parent: LinearLayout) {
        val (scene, content) = columns(parent)
        if (aiKind == "song" && aiBusy) avatar(scene, 220, animate = true)
        else image(scene, save.activeCompanion.art, save.activeCompanion.name, 260)
        label(scene, if (aiKind == "song") "우리만의 동요 만들기" else "AI 친구와 이야기해요", 27f, true)
        label(scene, stages[aiStage].name, 19f, true)
        label(scene, "기억할 안전 약속", 18f, true)
        label(scene, stages[aiStage].puzzles[aiPuzzle].explanation, 18f)
        if (aiKind == "song") label(scene, stages[aiStage].puzzles[1].explanation, 18f)
        label(content, if (aiAccess.isBlank()) "AI 연결 전" else "외부 AI · 부모님과 함께", 19f, true)
        if (aiAccess.isBlank()) {
            label(content, "부모님이 AI 연결을 설정하면 친구와 이야기하고 동요를 만들 수 있어요. 기본 퀴즈는 연결 없이도 계속할 수 있어요.", 18f)
            button(content, "부모님 AI 연결 설정", primary = true) { configureFamilyAi() }
        } else {
            if (aiKind == "coach") {
                label(content, familyQuestions[aiStage][aiPuzzle], 20f, true)
                val input = android.widget.EditText(this).apply {
                    hint = "우리 가족의 생각을 적거나 말해 주세요"
                    setText(aiDraft)
                    setTextColor(ink)
                    setHintTextColor(0xFFBDB5D6.toInt())
                    minLines = 2
                    maxLines = 4
                    filters = arrayOf(android.text.InputFilter.LengthFilter(300))
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                    isSaveEnabled = false
                    isEnabled = !aiBusy && !voiceListening
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { aiDraft = s.toString() }
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                }
                content.addView(input, LinearLayout.LayoutParams(-1, -2))
                button(content, if (voiceListening) "듣기 취소" else "말로 생각 전하기", !aiBusy) {
                    if (voiceListening) { stopVoice(); draw() }
                    else {
                        aiVoicePending = ""
                        voiceListening = true
                        voiceStatus = "듣고 있어요…"
                        draw()
                        familyVoice.start({ text, complete ->
                            if (canInteract() && page == "AI 친구") {
                                if (complete) aiVoicePending = text
                                voiceListening = !complete
                                voiceStatus = if (complete) "이렇게 들었어요. 확인 후 답변에 넣어 주세요." else "듣고 있어요…"
                                draw()
                            }
                        }, { message ->
                            voiceListening = false
                            voiceStatus = message
                            if (canInteract()) draw()
                        })
                    }
                }
                if (voiceStatus.isNotEmpty()) label(content, voiceStatus, 16f)
                if (aiVoicePending.isNotBlank() && !voiceListening) {
                    label(content, aiVoicePending, 19f, true)
                    button(content, "이 말로 답변 채우기") {
                        aiDraft = aiVoicePending
                        aiVoicePending = ""
                        stopVoice()
                        draw()
                    }
                    button(content, "인식한 말 지우기") { aiVoicePending = ""; stopVoice(); draw() }
                }
            } else {
                label(content, "이번 안전 약속으로 30초 동요를 만들어요.", 20f)
                button(content, "연령대 · ${songAge}세", !aiBusy) {
                    songPreview = null
                    aiOutput = ""
                    songAge = if (songAge == "4-6") "7-9" else "4-6"
                    draw()
                }
                button(content, "스타일 · " + if (songStyle == "bright") "발랄한 실로폰" else "포근한 피아노", !aiBusy) {
                    songPreview = null
                    aiOutput = ""
                    songStyle = if (songStyle == "bright") "gentle" else "bright"
                    draw()
                }
                if (latestDiscussion?.first == aiStage) label(content, "대화에서 찾은 안전 주제를 함께 넣어요.", 16f)
            }
            button(content, if (aiBusy) "친구가 생각하는 중…" else
                if (aiKind == "song") (if (songPreview == null) "가사 미리보기" else "이 가사로 동요 만들기") else "확인한 생각 보내기", !aiBusy && !voiceListening, primary = true) {
                if (aiKind == "coach" && aiDraft.isBlank()) {
                    aiMessage = "먼저 생각을 적거나 말해 주세요."
                    draw()
                } else requestFamilyAi()
            }
            if (aiBusy) button(content, "요청 취소") { cancelAi(); aiMessage = "요청을 취소했어요."; draw() }
            button(content, "AI 연결 설정") { configureFamilyAi() }
        }
        if (aiMessage.isNotBlank()) label(content, aiMessage, 17f)
        if (aiOutput.isNotBlank()) {
            label(content, "AI가 만든 " + if (aiKind == "song") "동요 가사" else "친구의 이야기", 21f, true)
            label(content, aiOutput, 21f)
            label(content, "부모님과 함께 읽어요. 안전 약속은 왼쪽의 검수된 설명을 기준으로 해요.", 16f)
        }
        if (aiKind == "song" && songPreview != null && !aiBusy) {
            button(content, "가사 미리보기 다시 만들기") {
                songPreview = null
                aiOutput = ""
                requestFamilyAi()
            }
        }
        if (aiKind == "song") button(content, "동요 앨범") { navigate("동요 앨범") }
        button(content, "퀴즈로 돌아가기") { navigate("퍼즐") }
    }

    private fun requestFamilyAi() {
        if (!canInteract() || aiBusy || (aiKind == "song" && aiStage >= save.progress.cleared)) return
        stopVoice()
        aiBusy = true
        aiMessage = ""
        if (songPreview == null) aiOutput = ""
        val preview = songPreview
        val kind = if (aiKind == "song" && preview == null) "lyrics" else aiKind
        val request = FamilyAiRequest(kind, aiStage, aiPuzzle, save.activeCompanion.id,
            if (aiKind == "coach") aiDraft.trim() else "", songAge, songStyle,
            latestDiscussion?.takeIf { it.first == aiStage }?.second, preview?.first, preview?.second)
        val generation = ++aiGeneration
        aiJob = activityScope.launch {
            try {
                when (val result = familyAi.generate(aiEndpoint, aiAccess, request)) {
                    is FamilyAiResult.Success -> {
                        if (generation != aiGeneration || !canInteract()) return@launch
                        if (request.kind == "lyrics") {
                            val signature = checkNotNull(result.signature)
                            val lyrics = org.json.JSONObject(result.text)
                            aiOutput = lyrics.getString("title") + "\n\n" +
                                listOf("verse", "chorus").joinToString("\n\n") { section ->
                                    val lines = lyrics.getJSONArray(section)
                                    (0 until lines.length()).joinToString("\n") { lines.getString(it) }
                                }
                            songPreview = result.text to signature
                            aiMessage = "가사를 읽어 보고 이 가사로 동요를 만들 수 있어요."
                        } else if (request.kind == "song") {
                            songPreview = null
                            val song = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                songAlbum.save(result.text, checkNotNull(result.audio), request.stage, request.companion)
                            }
                            aiOutput = song.title + "\n\n" + song.lyrics
                            aiMessage = "동요를 앨범에 저장했어요."
                            // Album entry is the recovery evidence if the app closes before XP is saved.
                            if (canInteract()) {
                                val rewarded = save.awardFirstSong(true)
                                if (rewarded != save) persist(rewarded)
                                runCatching { songCommand("play", song.id) }.onFailure {
                                    aiMessage = "동요는 저장됐어요. 앨범에서 재생을 다시 눌러 주세요."
                                }
                            }

                        } else {
                            latestDiscussion = discussionTopic(request.stage, request.answer)?.let { request.stage to it }
                            aiOutput = result.text
                        }
                    }
                    is FamilyAiResult.Failure -> aiMessage = result.message
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                aiMessage = "응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                aiMessage = "동요 파일을 저장하지 못했어요. 자동으로 다시 생성하지 않아요."
            } finally {
                if (generation == aiGeneration) aiBusy = false
            }
            if (generation == aiGeneration && canInteract() && page == "AI 친구") draw()
        }
        draw()
    }

    private fun songCommand(action: String, id: String? = null) {
        startService(android.content.Intent(this, SongPlayback::class.java).setAction(action).putExtra("id", id))
    }

    private fun songAlbumPage(parent: LinearLayout) {
        label(parent, "우리 가족 동요 앨범", 29f, true)
        label(parent, "저장한 동요는 인터넷 없이 들을 수 있어요.", 17f)
        val songs = songAlbum.list()
        val rewarded = save.awardFirstSong(songs.any { !it.example })
        if (rewarded != save && !saving && canInteract()) persist(rewarded)
        if (songs.none { it.example && it.title == "기다려요 횡단보도" }) {
            button(parent, "예시 동요 · 기다려요 횡단보도 듣기") {
                submit({
                    val audio = resources.openRawResource(R.raw.crosswalk_example).use { it.readBytes() }
                    val lyrics = resources.openRawResource(R.raw.crosswalk_example_lyrics)
                        .bufferedReader().use { it.readText() }
                    songAlbum.save(lyrics, audio, 2, "lulu", example = true)
                }) { song ->
                    if (canInteract()) runCatching { songCommand("play", song.id) }
                }
            }
            label(parent, "루루의 30초 예시 동요 · 인터넷 없이 재생 · 생성 보상 제외", 16f)
        }
        if (songs.isEmpty()) label(parent, "완료한 퀴즈에서 우리만의 동요를 만들어 보세요.", 18f)
        val (playerColumn, albumColumn) = columns(parent)
        if (songs.isNotEmpty()) {
            playerColumn.addView(SongPlayerView(this, songs, settings.largeText, ::canInteract),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        }
        label(albumColumn, "모아 둔 동요", 23f, true)
        songs.forEach { song ->
            val card = vertical().apply {
                background = panel(0xFF34364F.toInt())
                setPadding(dp(18), dp(14), dp(18), dp(14))
            }
            albumColumn.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            label(card, (if (song.favorite) "★ " else "") + song.title + if (song.example) " · 예시" else "", 22f, true)
            label(card, "${stages[song.stage].name} · ${companions.firstOrNull { it.id == song.companion }?.name.orEmpty()} · ${song.duration / 1000}초", 17f)

            val actions = horizontal()
            card.addView(actions)
            button(actions, "재생") { songCommand("play", song.id) }
            button(actions, if (song.favorite) "즐겨찾기 해제" else "즐겨찾기") {
                runCatching { songAlbum.write(song.copy(favorite = !song.favorite)) }
                draw()
            }
            button(actions, "삭제") {
                showParkedDialog(AlertDialog.Builder(this).setTitle("이 동요를 삭제할까요?")
                    .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                        if (canInteract()) {
                            if (SongPlayback.currentId == song.id) songCommand("stop")
                            songAlbum.delete(song)
                            draw()
                        }
                    })
            }
        }
    }

    private fun settingToggle(parent: LinearLayout, title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
        val row = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(lavender)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val copy = vertical()
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        label(copy, title, 22f, true)
        label(copy, description, 16f)
        row.addView(android.widget.Switch(this).apply {
            isChecked = checked
            contentDescription = title
            minHeight = dp(56)
            minimumWidth = dp(72)
            thumbTintList = ColorStateList.valueOf(0xFFB8F3DD.toInt())
            trackTintList = ColorStateList.valueOf(if (checked) 0xFF7463CF.toInt() else 0xFF55536B.toInt())
            setOnCheckedChangeListener { _, value -> if (canInteract() && !saving) change(value) }
        })
    }

    private fun settingsPage(parent: LinearLayout) {
        label(parent, "우리 가족의 놀이 설정", 28f, true)
        label(parent, "편하게 듣고, 크게 보고, 함께 즐겨요", 18f)
        val (play, system) = columns(parent)
        image(play, save.activeCompanion.art, save.activeCompanion.name, 150)
        label(play, "놀이 환경", 22f, true)
        label(system, "차량과 저장 관리", 22f, true)
        label(system, "진행은 이 기기에 자동으로 저장돼요", 17f)
        settingToggle(play, "효과음", "정답과 선물을 소리로 알려줘요", settings.sound) {
            updateSettings(settings.copy(sound = it))
        }
        settingToggle(play, "말로 묻고 답하기", "주차 중 질문을 읽고, 선택형 퀴즈에서는 낭독 뒤 마이크를 열어요.", settings.voiceQuiz) {
            updateSettings(settings.copy(voiceQuiz = it))
        }
        settingToggle(play, "큰 글자", "질문과 설명을 더 크게 보여줘요", settings.largeText) {
            updateSettings(settings.copy(largeText = it))
        }
        settingToggle(system, "차량 샘플 모드", "실제 차량 대신 예시 데이터를 보여줘요", settings.demo) {
            updateSettings(settings.copy(demo = it))
        }
        button(play, "놀이 안내 다시 보기") { navigate("놀이 안내") }
        button(system, "부모님 AI 연결 설정") { configureFamilyAi() }
        button(system, "차량 데이터") { navigate("차량") }
        button(system, "게임 진행 초기화") {
            showParkedDialog(AlertDialog.Builder(this)
                .setTitle("게임 진행을 초기화할까요?")
                .setMessage("퀴즈 진행과 보상, 해금한 친구들이 삭제됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화") { _, _ ->
                    if (canInteract()) {
                        val previous = save
                        submit({ store.reset(previous) }) {
                            save = it
                            stageIndex = 0
                            unopenedRewardStage = null
                            page = "홈"
                            scroll = null
                            feedback = ""
                        }
                    }
                })
        }
    }

    private fun vehiclePage(parent: LinearLayout) {
        label(parent, "차량 데이터", 28f, true)
        label(parent, if (settings.demo) "DEMO DATA · 가상 스냅샷"
            else "Pleos · 정차 중 주행거리 읽기", 20f, true)
        val status = when (val value = snapshot) {
            is VehicleSnapshot.Demo -> "샘플 누적 거리 · ${value.kilometers} km"
            is VehicleSnapshot.Pleos -> "누적 주행거리 · ${value.totalDistance} (SDK 반환값)"
            VehicleSnapshot.Unavailable.PERMISSION -> "차량 조회 권한이 없어요."
            VehicleSnapshot.Unavailable.RESTRICTED -> "정차 상태를 확인할 수 없어요."
            VehicleSnapshot.Unavailable.UNSUPPORTED -> "이 차량에서 주행거리 조회를 지원하지 않아요."
            VehicleSnapshot.Unavailable.INVALID -> "유효한 주행거리 값이 없어요."
            VehicleSnapshot.Unavailable.TIMEOUT -> "차량 서비스 응답 시간이 초과됐어요."
            VehicleSnapshot.Unavailable.FAILURE -> "차량 서비스에서 주행거리를 가져오지 못했어요."
            null -> when {
                readingSnapshot -> "조회 중…"
                !settings.demo && !canInteract() -> "정차 상태가 확인되면 조회할 수 있어요."
                else -> "버튼을 누르면 현재 값을 한 번 조회해요."
            }
        }
        label(parent, status, 18f)
        label(parent, "안전운전 점수 · 연동 준비 중", 16f)
        button(parent, "스냅샷 조회", !readingSnapshot && (settings.demo || canInteract())) { readSnapshot() }
        val mission = vertical().apply {
            background = panel(lavender)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        parent.addView(mission)
        label(mission, "우리 가족 안전 동행", 23f, true)
        label(mission, "10km 동행 + 안전 점수 90점 이상 · 하루 한 번 성장 선물", 17f)
        label(mission, "평소 필요한 이동으로 천천히 채워요. 도착해 주차한 뒤 확인해 주세요.", 16f)
        if (settings.demo) {
            val today = LocalDate.now().toEpochDay()
            val distance = if (settings.demoJourneyDay == today) settings.demoJourneyKm else 0.0
            val claimed = settings.demoBonusDay == today
            label(mission, "시연 전용 · ${distance.toInt()} / 10km · 예시 점수 95점", 18f, true)
            label(mission, "실제 주행 기록이 아니며 게임 XP와 분리됩니다. 누적 ${settings.demoJourneyXp} 데모 XP", 16f)
            button(mission, "가상 주행 2km 체험", distance < JourneyMission.TARGET_KM && !claimed) {
                submit({ runBlocking {
                    settingsStore.addDemoJourney(today)
                    settingsStore.read()
                } }) { settings = it }
            }
            button(mission, if (claimed) "오늘의 선물 수령 완료 · +20 데모 XP" else "동행 선물 받기 · +20 데모 XP",
                JourneyMission.canClaim(distance, today, settings.demoBonusDay)) {
                submit({ runBlocking {
                    settingsStore.claimDemoBonus(today)
                    settingsStore.read()
                } }) { updated ->
                    settings = updated
                }
            }
        } else {
            label(mission, "실차 미션 준비 중 · 거리 단위·차량 식별·검증된 안전 점수 연동 후 열립니다.", 17f)
            label(mission, "누적거리 조회만으로 안전운전을 판정하거나 선물을 지급하지 않아요.", 16f)
        }
        if (snapshot != null) button(parent, "조회값 지우기") { clearSnapshot(); draw() }
        button(parent, "설정으로") { navigate("설정") }
    }

    private fun updateSettings(next: GameSettings) {
        submit({ runBlocking { settingsStore.write(next) } }) {
            settings = next
            if (!next.sound) gameAudio?.stopTone()
        }
    }

    private fun guide(parent: LinearLayout) {
        label(parent, "우리 가족의 안전 앨범", 29f, true)
        label(parent, "함께 알아낸 약속들을 다시 꺼내 보아요", 18f)
        val (achievements, lessons) = columns(parent)
        image(achievements, save.activeCompanion.art, save.activeCompanion.name, 180)
        label(achievements, "함께 모은 배지", 23f, true)
        adventureAchievements(save.progress.cleared, save.totalStars).forEach { achievement ->
            val badge = vertical().apply {
                background = panel(if (achievement.earned) 0xFF3D5363.toInt() else lavender)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            achievements.addView(badge, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            label(badge, (if (achievement.earned) "★ " else "☆ ") + achievement.title, 19f, true)
            label(badge, "${achievement.current} / ${achievement.target}  ·  " +
                if (achievement.earned) "함께 해냈어요" else "차근차근 모으는 중", 16f)
        }
        label(lessons, "안전 약속 카드", 23f, true)
        stages.forEachIndexed { index, stage ->
            val unlocked = index < save.progress.cleared
            button(lessons, "${index + 1}. ${stage.name}\n" +
                if (unlocked) "배운 약속 다시 보기  ★" else "이 퀴즈를 마치면 열려요", unlocked, icon = GameIcon.BOOK) {
                val card = vertical().apply { setPadding(dp(24), dp(20), dp(24), dp(20)) }
                label(card, stage.name, 25f, true)
                stage.puzzles.forEachIndexed { puzzleIndex, puzzle ->
                    label(card, "약속 ${puzzleIndex + 1}", 18f, true)
                    label(card, puzzle.explanation, 19f)
                    label(card, familyQuestions[index][puzzleIndex], 17f)
                    label(card, learningReferences[index][puzzleIndex].joinToString("\n") { it.url }, 13f)
                }
                button(card, "닫기", primary = true) { openDialog?.dismiss() }
                showParkedDialog(AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(card) }))
            }
        }
    }
}
