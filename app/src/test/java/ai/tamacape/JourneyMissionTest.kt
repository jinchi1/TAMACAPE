package ai.tamacape

import org.junit.Assert.*
import org.junit.Test

class JourneyMissionTest {
    private val trip = JourneyEvidence("car-a", 100.0, 110.0, 20.0, 95, true)

    @Test fun onlyVerifiedMatchingVehicleWithSafetyEvidenceQualifies() {
        assertEquals(10.0, JourneyMission.eligibleKilometers(trip, "car-a"), 0.0)
        for (invalid in listOf(trip.copy(verified = false), trip.copy(vehicle = "car-b"),
            trip.copy(safetyScore = null), trip.copy(safetyScore = 89), trip.copy(safetyScore = 101))) {
            assertEquals(0.0, JourneyMission.eligibleKilometers(invalid, "car-a"), 0.0)
        }
    }

    @Test fun invalidDecreasingOrJumpingOdometerDoesNotEarnProgress() {
        for (invalid in listOf(trip.copy(startKm = -1.0), trip.copy(endKm = 99.0),
            trip.copy(endKm = Double.NaN), trip.copy(endKm = Double.POSITIVE_INFINITY),
            trip.copy(endKm = 100.0), trip.copy(endKm = 1000.0), trip.copy(elapsedMinutes = 0.0),
            trip.copy(elapsedMinutes = 1.0), trip.copy(elapsedMinutes = Double.NaN))) {
            assertEquals(0.0, JourneyMission.eligibleKilometers(invalid, "car-a"), 0.0)
        }
    }

    @Test fun rewardNeedsTargetAndNewDay() {
        assertFalse(JourneyMission.canClaim(9.9, 100, null))
        assertFalse(JourneyMission.canClaim(Double.NaN, 100, null))
        assertFalse(JourneyMission.canClaim(10.0, 100, 100))
        assertFalse(JourneyMission.canClaim(10.0, 99, 100))
        assertTrue(JourneyMission.canClaim(10.0, 101, 100))
    }
}
