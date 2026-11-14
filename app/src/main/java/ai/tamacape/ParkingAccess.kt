package ai.tamacape

internal enum class ParkingAccess {
    UNKNOWN, RESTRICTED, PARKED;

    val allowsGame: Boolean get() = this == PARKED

    companion object {
        fun fromPlatform(requiresDistractionOptimization: Boolean?): ParkingAccess =
            when (requiresDistractionOptimization) {
                true -> RESTRICTED
                false -> PARKED
                null -> UNKNOWN
            }
    }
}
