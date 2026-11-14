package ai.tamacape

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VehicleSnapshotTest {
    private class Session : VehicleReadSession {
        var closed = false
        var supported = true
        var value: Float? = 25f
        var fail: Exception? = null
        var waitMs = 0L
        var onRead: () -> Unit = {}
        override suspend fun initialize() { fail?.let { throw it } }
        override suspend fun supportsRead() = supported
        override suspend fun distance(): Float? { delay(waitMs); onRead(); return value }
        override fun close() { closed = true }
    }
    @Test fun deniedAccessNeverOpensVehicle() = runBlocking {
        assertEquals(VehicleSnapshot.Unavailable.RESTRICTED,
            readVehicleSnapshot({ false }, { true }) { error("must not open") })
        assertEquals(VehicleSnapshot.Unavailable.PERMISSION,
            readVehicleSnapshot({ true }, { false }) { error("must not open") })
    }
    @Test fun validatesReturnedDistanceAndReleasesSession() = runBlocking {
        for (value in listOf(null, -1f, Float.NaN, Float.POSITIVE_INFINITY, 0f, 123f)) {
            val session = Session().apply { this.value = value }
            val result = readVehicleSnapshot({ true }, { true }) { session }
            assertEquals(if (value != null && value.isFinite() && value >= 0) VehicleSnapshot.Pleos(value)
                else VehicleSnapshot.Unavailable.INVALID, result)
            assertTrue(session.closed)
        }
    }
    @Test fun unsupportedFailuresAndTimeoutReleaseSession() = runBlocking {
        val cases = listOf(
            Session().apply { supported = false } to VehicleSnapshot.Unavailable.UNSUPPORTED,
            Session().apply { fail = SecurityException() } to VehicleSnapshot.Unavailable.PERMISSION,
            Session().apply { fail = IllegalStateException() } to VehicleSnapshot.Unavailable.FAILURE,
            Session().apply { waitMs = 1000 } to VehicleSnapshot.Unavailable.TIMEOUT,
        )
        for ((session, expected) in cases) {
            assertEquals(expected, readVehicleSnapshot({ true }, { true }, 30) { session })
            assertTrue(session.closed)
        }
    }
    @Test fun restrictionDuringReadDiscardsValue() = runBlocking {
        var parked = true
        val session = Session().apply { onRead = { parked = false } }
        assertEquals(VehicleSnapshot.Unavailable.RESTRICTED,
            readVehicleSnapshot({ parked }, { true }) { session })
        assertTrue(session.closed)
    }
    @Test fun callerCancellationPropagatesAndReleasesSession() = runBlocking {
        val session = Session().apply { waitMs = 10000 }
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            readVehicleSnapshot({ true }, { true }) { session }
            fail("cancelled read must not return a result")
        }
        job.cancelAndJoin()
        assertTrue(session.closed)
    }
}
