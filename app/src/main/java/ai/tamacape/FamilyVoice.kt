package ai.tamacape

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/** One explicitly requested on-device session. No audio or transcript is persisted. */
internal class FamilyVoice(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var sdk: SpeechToText? = null
    private var generation = 0
    private var listener: ResultListener? = null
    private var timeout: Runnable? = null

    fun start(update: (String, Boolean) -> Unit, failure: (String) -> Unit) {
        stop()
        val ticket = generation
        fun deliver(action: () -> Unit) { main.post { if (ticket == generation) action() } }
        fun fail() = deliver {
            stop()
            failure("음성인식을 사용할 수 없어요. 화면의 답으로 계속할 수 있어요.")
        }
        val result = object : ResultListener {
            override fun onReady() = Unit
            override fun onStartedRecognition() = Unit
            override fun onUpdated(stt: String, completed: Boolean) = deliver {
                if (completed) stop()
                update(stt.take(300), completed)
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onEndedRecognition() = deliver {
                stop()
                failure("듣기가 끝났어요. 답이 보이지 않으면 다시 말해 주세요.")
            }
            override fun onError(errorType: ErrorType) = fail()
        }
        listener = result
        timeout = Runnable {
            if (ticket == generation) {
                stop()
                failure("듣는 시간이 끝났어요. 다시 시도하거나 화면에서 골라 주세요.")
            }
        }.also { main.postDelayed(it, 20000) }
        runCatching {
            val speech = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
            sdk = speech
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() = deliver {
                    runCatching {
                        speech.addListener(result)
                        speech.setLocale(Locale.KOREA)
                        speech.request()
                    }.onFailure { fail() }
                }
                override fun onFailure(errorType: ErrorType) = fail()
            })
        }.onFailure { fail() }
    }

    fun stop() {
        generation++
        timeout?.let(main::removeCallbacks)
        timeout = null
        val previous = sdk
        sdk = null
        runCatching { previous?.stop() }
        listener?.let { runCatching { previous?.removeListener(it) } }
        listener = null
        runCatching { previous?.release() }
    }
}

/** Conservative exact option matching; never lets generated text judge correctness. */
internal fun spokenOption(transcript: String, options: List<String>): Int? {
    fun normalized(text: String) = text.filterNot { it.isWhitespace() || it in ".,!?。？！" }
    val text = normalized(transcript)
    val number = mapOf("1번" to 0, "일번" to 0, "첫번째" to 0,
        "2번" to 1, "이번" to 1, "두번째" to 1,
        "3번" to 2, "삼번" to 2, "세번째" to 2)[text]
    if (number != null) return number.takeIf { it in options.indices }
    return options.indices.singleOrNull { normalized(options[it]) == text }
}
