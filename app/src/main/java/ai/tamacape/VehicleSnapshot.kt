package ai.tamacape

import android.content.Context
import android.content.pm.PackageManager
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal sealed interface VehicleSnapshot {
    data class Demo(val kilometers: Double) : VehicleSnapshot
    data class Pleos(val totalDistance: Float) : VehicleSnapshot
    enum class Unavailable : VehicleSnapshot {
        RESTRICTED, PERMISSION, UNSUPPORTED, INVALID, TIMEOUT, FAILURE,
    }
}

internal fun interface VehicleSnapshotProvider {
    suspend fun read(): VehicleSnapshot
}

internal object DemoVehicleProvider : VehicleSnapshotProvider {
    override suspend fun read(): VehicleSnapshot = VehicleSnapshot.Demo(kilometers = 1234.5)
}

internal fun interface DrivingScoreProvider {
    suspend fun read(): Int?
}

internal object UnconnectedDrivingScoreProvider : DrivingScoreProvider {
    override suspend fun read(): Int? = null
}

internal class PleosVehicleProvider(context: Context, private val permittedWhileParked: () -> Boolean) :
    VehicleSnapshotProvider {
    private val app = context.applicationContext

    override suspend fun read(): VehicleSnapshot = readVehicleSnapshot(
        permittedWhileParked,
        { app.checkSelfPermission("pleos.car.permission.CAR_DRIVE") == PackageManager.PERMISSION_GRANTED },
        openSession = {
            val vehicle = Vehicle(app)
            object : VehicleReadSession {
                override suspend fun initialize() { vehicle.initialize() }
                override suspend fun supportsRead(): Boolean {
                    val config = await<CapabilityConfig>(vehicle.getOdometer()::checkCurrentDistanceDrivenCapabilities)
                    return config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                }
                override suspend fun distance(): Float? = await(vehicle.getOdometer()::getCurrentDistanceDriven)
                override fun close() { vehicle.release() }
            }
        },
    )

    private suspend fun <T> await(call: ((T) -> Unit, (Exception) -> Unit) -> Unit): T =
        suspendCancellableCoroutine { continuation ->
            val delivered = AtomicBoolean()
            call(
                { if (delivered.compareAndSet(false, true)) continuation.resume(it) },
                { if (delivered.compareAndSet(false, true)) continuation.resumeWithException(it) },
            )
        }
}

internal interface VehicleReadSession {
    suspend fun initialize()
    suspend fun supportsRead(): Boolean
    suspend fun distance(): Float?
    fun close()
}

/** Shared decision path for the real SDK and deterministic failure checks. */
internal suspend fun readVehicleSnapshot(
    parked: () -> Boolean,
    permission: () -> Boolean,
    timeoutMs: Long = 5_000,
    openSession: () -> VehicleReadSession,
): VehicleSnapshot {
    return try {
        if (!parked()) return VehicleSnapshot.Unavailable.RESTRICTED
        if (!permission()) return VehicleSnapshot.Unavailable.PERMISSION
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            val session = openSession()
            try {
                session.initialize()
                if (!parked()) return@withTimeoutOrNull VehicleSnapshot.Unavailable.RESTRICTED
                if (!session.supportsRead()) return@withTimeoutOrNull VehicleSnapshot.Unavailable.UNSUPPORTED
                if (!parked()) return@withTimeoutOrNull VehicleSnapshot.Unavailable.RESTRICTED
                val distance = session.distance()
                when {
                    !parked() -> VehicleSnapshot.Unavailable.RESTRICTED
                    distance == null || !distance.isFinite() || distance < 0 -> VehicleSnapshot.Unavailable.INVALID
                    else -> VehicleSnapshot.Pleos(distance)
                }
            } finally { runCatching { session.close() } }
        } ?: VehicleSnapshot.Unavailable.TIMEOUT
    } catch (_: SecurityException) {
        VehicleSnapshot.Unavailable.PERMISSION
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        VehicleSnapshot.Unavailable.FAILURE
    }
}
