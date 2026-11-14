package ai.tamacape

/** Normalized, verified trip evidence only. Raw SDK odometer values must not enter here. */
internal data class JourneyEvidence(
    val vehicle: String,
    val startKm: Double,
    val endKm: Double,
    val elapsedMinutes: Double,
    val safetyScore: Int?,
    val verified: Boolean,
)

internal object JourneyMission {
    const val TARGET_KM = 10.0
    const val MIN_SCORE = 90
    const val REWARD_XP = 20

    // This is an app mission threshold, not an official Pleos safety rating.
    fun eligibleKilometers(evidence: JourneyEvidence, expectedVehicle: String): Double {
        if (!evidence.verified || expectedVehicle.isBlank() || evidence.vehicle != expectedVehicle) return 0.0
        val (start, end, minutes) = listOf(evidence.startKm, evidence.endKm, evidence.elapsedMinutes)
        if (!start.isFinite() || !end.isFinite() || !minutes.isFinite() || start < 0 || end <= start || minutes <= 0) return 0.0
        val distance = end - start
        // Reject obviously inconsistent readings; never interpret this as proof of safe driving.
        if (distance > 500 || distance / minutes * 60 > 180) return 0.0
        if (evidence.safetyScore !in MIN_SCORE..100) return 0.0
        return distance
    }

    fun canClaim(kilometers: Double, day: Long, claimedDay: Long?): Boolean =
        kilometers.isFinite() && kilometers >= TARGET_KM && day > (claimedDay ?: Long.MIN_VALUE)
}
