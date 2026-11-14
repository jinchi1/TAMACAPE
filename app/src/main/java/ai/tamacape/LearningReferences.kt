package ai.tamacape

internal data class LearningReference(val title: String, val url: String)

private const val HIGHWAY_CODE = "https://www.gov.uk/guidance/the-highway-code/"
private val departure = LearningReference(
    "영국 교통부 · 출발 전 점검 및 안전벨트",
    HIGHWAY_CODE + "rules-for-drivers-and-motorcyclists-89-to-102#rule97",
)
private val weather = LearningReference(
    "영국 교통부 · 빗길 및 안개 운전",
    HIGHWAY_CODE + "driving-in-adverse-weather-conditions-226-to-237",
)
private val crossing = LearningReference(
    "영국 교통부 · 횡단보도와 가려진 시야",
    HIGHWAY_CODE + "using-the-road-159-to-203#rule193",
)
private val indicators = LearningReference(
    "현대자동차 사용설명서 · 경고등 및 표시등",
    "https://ownersmanual.hyundai.com/full_webhelp/MEa/2026/en_US/warning_indicator_lights.html",
)
private val tires = LearningReference(
    "미국 도로교통안전국 · 타이어 공기압과 TPMS",
    "https://www.nhtsa.gov/vehicle-safety/tires",
)

internal val signalReference = LearningReference(
    "영국 교통부 · 방향지시등 사용 및 해제",
    HIGHWAY_CODE + "general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158#rule103",
)

internal val learningReferences = listOf(
    listOf(listOf(departure), listOf(indicators)),
    listOf(listOf(weather), listOf(weather)),
    listOf(listOf(crossing, signalReference), listOf(crossing)),
    listOf(listOf(tires, indicators), listOf(indicators)),
    listOf(listOf(departure, weather), listOf(weather, crossing)),
)
