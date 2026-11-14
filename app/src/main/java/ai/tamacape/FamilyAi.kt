package ai.tamacape

import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

internal const val FAMILY_AI_ENDPOINT = "https://pleos-kids-ai.jinchi1.workers.dev/v1/family"

internal fun validFamilyEndpoint(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && uri.path == "/v1/family"
}.getOrDefault(false)

internal data class FamilyAiRequest(
    val kind: String, val stage: Int, val puzzle: Int, val companion: String, val answer: String,
    val age: String = "4-6", val style: String = "bright", val topic: Int? = null,
    val preview: String? = null, val signature: String? = null,
) {
    fun valid() = kind in listOf("coach", "song", "lyrics") && stage in stages.indices &&
        puzzle in stages[stage].puzzles.indices && companions.any { it.id == companion } &&
        answer.length <= 300 && if (kind == "coach") answer.isNotBlank() else answer.isEmpty() && age in listOf("4-6", "7-9") &&
            style in listOf("bright", "gentle") && (topic == null || topic in stages[stage].puzzles.indices)
}

internal sealed interface FamilyAiResult {
    data class Success(val text: String, val audio: ByteArray? = null, val signature: String? = null) : FamilyAiResult
    data class Failure(val message: String) : FamilyAiResult
}

internal class FamilyAiClient(
    private val openConnection: (String) -> HttpsURLConnection = {
        URI(it).toURL().openConnection() as HttpsURLConnection
    },
) {
    private val worker = Executors.newSingleThreadExecutor()

    suspend fun generate(endpoint: String, access: String, request: FamilyAiRequest): FamilyAiResult {
        if (!validFamilyEndpoint(endpoint) || access.length !in 32..200 || !request.valid())
            return FamilyAiResult.Failure("부모님 설정에서 AI 연결을 확인해 주세요.")
        return withTimeout(if (request.kind == "song") 120000 else 30000) {
            suspendCancellableCoroutine { continuation ->
                val connection = AtomicReference<HttpsURLConnection?>()
                val future = worker.submit {
                    var response: FamilyAiResult = FamilyAiResult.Failure("연결하지 못했어요. 잠시 후 다시 시도해 주세요.")
                    try {
                        if (!continuation.isActive) return@submit
                        val conn = openConnection(endpoint)
                        connection.set(conn)
                        if (!continuation.isActive) return@submit
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 8000
                        conn.readTimeout = if (request.kind == "song") 115000 else 28000
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("Authorization", "Bearer " + access)
                        val body = JSONObject().put("version", 1).put("kind", request.kind)
                            .put("stage", request.stage).put("puzzle", request.puzzle)
                            .put("companion", request.companion).put("answer", request.answer)
                            .apply { if (request.kind != "coach") {
                                put("age", request.age)
                                put("style", request.style)
                                put("topic", request.topic ?: JSONObject.NULL)
                                put("preview", request.preview)
                                put("signature", request.signature)
                            } }
                            .toString().toByteArray(Charsets.UTF_8)
                        conn.setFixedLengthStreamingMode(body.size)
                        conn.outputStream.use { it.write(body) }
                        response = if (conn.responseCode == 200) {
                            val limit = if (request.kind == "song") 1_350_000 else 16384
                            val bytes = conn.inputStream.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(2048)
                                while (output.size() <= limit) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            check(bytes.size <= limit)
                            val result = JSONObject(String(bytes, Charsets.UTF_8))
                            val text = result.getString("text")
                            check(result.getInt("version") == 1 && result.getString("kind") == request.kind &&
                                result.getString("source") == "ai" && text.isNotBlank() && text.length <= 1500)
                            FamilyAiResult.Success(text, if (request.kind == "song")
                                android.util.Base64.decode(result.getString("audio"), android.util.Base64.DEFAULT) else null,
                                result.optString("signature").takeIf { it.matches(Regex("[a-f0-9]{64}")) })
                        } else FamilyAiResult.Failure(errorMessage(conn.responseCode, conn))
                    } catch (_: Exception) {
                        // Never log response bodies, family text, or access credentials.
                    } finally {
                        connection.getAndSet(null)?.disconnect()
                    }
                    if (continuation.isActive) continuation.resume(response)
                }
                continuation.invokeOnCancellation {
                    connection.getAndSet(null)?.disconnect()
                    future.cancel(true)
                }
            }
        }
    }

    private fun errorMessage(status: Int, connection: HttpsURLConnection): String {
        val code = runCatching {
            connection.errorStream?.use { stream ->
                val buffer = ByteArray(1024)
                var count = 0
                while (count < buffer.size) {
                    val read = stream.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                JSONObject(String(buffer, 0, count, Charsets.UTF_8)).optString("error")
            }
        }.getOrNull()
        return when (code) {
            "invalid_preview" -> "가사 미리보기를 다시 만들어 주세요."
            "music_not_configured" -> "동요 음원 생성이 꺼져 있거나 서버 설정이 필요해요."
            "music_limit" -> if (status == 429) "오늘의 동요 생성 한도에 도달했어요."
                else "동요 생성 한도를 확인하지 못했어요."
            "music_access_denied" -> "음악 서비스 인증 또는 이용 권한을 확인해 주세요."
            "music_request_rejected" -> "음악 서비스에서 동요 요청 형식을 거부했어요."
            "music_quota_or_rate_limit" -> "음악 서비스 사용량 또는 요청 한도에 도달했어요."
            "music_unexpected_format", "invalid_audio" -> "재생할 수 있는 음원 파일을 받지 못했어요."
            "music_connection_failed" -> "음악 서비스에 연결하지 못했어요."
            "music_interrupted" -> "음원 생성 요청이 중단됐어요."
            "music_download_failed" -> "생성된 음원을 끝까지 받지 못했어요."
            "music_provider_failed" -> "음악 서비스가 음원을 생성하지 못했어요."
            "daily_limit" -> "오늘의 AI 이용 한도에 도달했어요. 기본 퀴즈는 계속 즐길 수 있어요."
            else -> when (status) {
                401 -> "접속 코드가 만료됐거나 맞지 않아요. 부모님 설정을 확인해 주세요."
                422 -> "안전 약속에 관한 생각을 다시 이야기해 볼까요?"
                429 -> "조금 쉬었다 이야기해요. 잠시 후 다시 시도해 주세요."
                else -> "AI 친구가 잠시 쉬고 있어요. 기본 퀴즈는 계속할 수 있어요."
            }
        }
    }

    fun close() = worker.shutdownNow()
}

/** Match only reviewed topics from the chapter. Never send the original conversation to music generation. */
internal fun discussionTopic(stage: Int, answer: String): Int? {
    val keywords = listOf(
        listOf(listOf("벨트", "거울", "좌석", "belt"), listOf("도어", "차 문", "문이", "door")),
        listOf(listOf("안개", "시야", "fog"), listOf("빗길", "빗물", "rain")),
        listOf(listOf("횡단보도", "건너", "신호"), listOf("골목", "모퉁이", "가려")),
        listOf(listOf("타이어", "공기압", "tire"), listOf("설명서", "경고등", "manual")),
        listOf(listOf("벨트", "물기", "출발"), listOf("안개", "횡단보도", "보행자")),
    ).getOrNull(stage) ?: return null
    val scores = keywords.map { group -> group.count { answer.contains(it, ignoreCase = true) } }
    return scores.indices.singleOrNull { scores[it] > 0 && scores[it] == scores.maxOrNull() }
}
