package ai.tamacape

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyAiClientTest {
    private class Connection(val code: Int, val body: String) : HttpsURLConnection(URL("https://example.test/v1/family")) {
        val written = ByteArrayOutputStream()
        var disconnected = false
        override fun getOutputStream() = written
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = object : ByteArrayInputStream(body.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 3))
        }
        override fun getResponseCode() = code
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private val request = FamilyAiRequest("coach", 0, 0, "mobi", "기다려요")
    private val endpoint = "https://example.test/v1/family"
    private val token = "test-access-" + "x".repeat(32)

    @Test fun successSendsOnlyBoundedContextAndNeverFollowsRedirects() = runBlocking {
        val conn = Connection(200, """{"version":1,"kind":"coach","source":"ai","text":"같이 생각해 보자!"}""")
        val client = FamilyAiClient { conn }
        try {
            assertEquals(FamilyAiResult.Success("같이 생각해 보자!"), client.generate(endpoint, token, request))
            val body = JSONObject(conn.written.toString("UTF-8"))
            assertEquals("기다려요", body.getString("answer"))
            assertEquals("coach", body.getString("kind"))
            assertFalse(body.has("key"))
            assertFalse(body.has("name"))
            assertFalse(conn.instanceFollowRedirects)
            assertTrue(conn.disconnected)
        } finally { client.close() }
    }

    @Test fun rejectsWrongKindAndOversizedResult() = runBlocking {
        for (body in listOf(
            """{"version":1,"kind":"story","source":"ai","text":"wrong context"}""",
            """{"version":1,"kind":"coach","source":"ai","text":"""" + "가".repeat(1501) + "\"}",
            "not json"
        )) {
            val conn = Connection(200, body)
            val client = FamilyAiClient { conn }
            try { assertTrue(client.generate(endpoint, token, request) is FamilyAiResult.Failure) }
            finally { client.close() }
        }
    }

    @Test fun fragmentedDailyLimitHasDistinctRecoverableMessage() = runBlocking {
        for ((body, expected) in listOf(
            "{\"error\":\"daily_limit\"}" to "오늘의 AI 이용 한도",
            "{\"error\":\"rate_limited\"}" to "조금 쉬었다",
            "broken" to "조금 쉬었다",
        )) {
            val connection = Connection(429, body)
            val client = FamilyAiClient { connection }
            try {
                val result = client.generate(endpoint, token, request) as FamilyAiResult.Failure
                assertTrue(result.message.contains(expected))
                assertTrue(connection.disconnected)
            } finally { client.close() }
        }
    }

    @Test fun musicErrorsDistinguishConfigurationRightsFormatAndQuota() = runBlocking {
        val cases = listOf(
            Triple(503, "music_not_configured", "생성이 꺼져"),
            Triple(503, "music_connection_failed", "음악 서비스에 연결하지"),
            Triple(503, "music_download_failed", "음원을 끝까지 받지"),
            Triple(503, "music_interrupted", "요청이 중단"),
            Triple(503, "music_access_denied", "인증 또는 이용 권한"),
            Triple(503, "music_request_rejected", "요청 형식"),
            Triple(503, "music_quota_or_rate_limit", "사용량 또는 요청 한도"),
            Triple(429, "music_limit", "오늘의 동요 생성 한도"),
            Triple(400, "invalid_preview", "가사 미리보기"),
        )
        for ((status, code, expected) in cases) {
            val connection = Connection(status, JSONObject().put("error", code).toString())
            val client = FamilyAiClient { connection }
            try {
                val song = request.copy(kind = "song", answer = "")
                val result = client.generate(endpoint, token, song) as FamilyAiResult.Failure
                assertTrue(result.message.contains(expected))
                assertTrue(connection.disconnected)
            } finally { client.close() }
        }
    }

    @Test fun unauthorizedAndRateLimitRemainRecoverable() = runBlocking {
        for (code in listOf(401, 429, 503, 302)) {
            val client = FamilyAiClient { Connection(code, "") }
            try { assertTrue(client.generate(endpoint, token, request) is FamilyAiResult.Failure) }
            finally { client.close() }
        }
    }
}
