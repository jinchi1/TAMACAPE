package ai.tamacape

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.gameSettings by preferencesDataStore(name = "game-settings")

internal data class GameSettings(
    val sound: Boolean = true,
    val largeText: Boolean = true,
    val demo: Boolean = false,
    val demoBonusDay: Long? = null,
    val introductionSeen: Boolean = false,
    val voiceQuiz: Boolean = false,
    val demoJourneyDay: Long? = null,
    val demoJourneyKm: Double = 0.0,
    val demoJourneyXp: Long = 0,
)

internal class GameSettingsStore(private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
    constructor(context: Context) : this(context.applicationContext.gameSettings)
    private val sound = booleanPreferencesKey("sound")
    private val largeText = booleanPreferencesKey("large_text")
    private val demo = booleanPreferencesKey("demo")
    private val introductionSeen = booleanPreferencesKey("introduction_seen")
    private val voiceQuiz = booleanPreferencesKey("voice_quiz")
    private val journeyDay = longPreferencesKey("demo_journey_day")
    private val journeyKm = doublePreferencesKey("demo_journey_km")
    private val journeyXp = longPreferencesKey("demo_journey_xp")
    private val bonusDay = longPreferencesKey("demo_bonus_day")

    suspend fun read(): GameSettings = store.data.first().let {
        GameSettings(it[sound] ?: true, it[largeText] ?: true, it[demo] ?: false, it[bonusDay], it[introductionSeen] ?: false, it[voiceQuiz] ?: false, it[journeyDay], it[journeyKm] ?: 0.0, it[journeyXp] ?: 0)
    }

    suspend fun claimDemoBonus(day: Long): Long? = store.edit {
        if (it[demo] == true && it[journeyDay] == day && JourneyMission.canClaim(it[journeyKm] ?: 0.0, day, it[bonusDay])) {
            it[bonusDay] = day
            it[journeyXp] = (it[journeyXp] ?: 0).coerceAtMost(Long.MAX_VALUE - JourneyMission.REWARD_XP) + JourneyMission.REWARD_XP
        }
    }[bonusDay]

    suspend fun addDemoJourney(day: Long) {
        store.edit {
            if (it[demo] != true || day < (it[journeyDay] ?: Long.MIN_VALUE)) return@edit
            val previous = if (it[journeyDay] == day) it[journeyKm] ?: 0.0 else 0.0
            val sample = JourneyEvidence("demo", 100.0, 102.0, 5.0, 95, true)
            it[journeyDay] = day
            it[journeyKm] = (previous + JourneyMission.eligibleKilometers(sample, "demo")).coerceAtMost(JourneyMission.TARGET_KM)
        }
    }

    suspend fun write(value: GameSettings) {
        store.edit {
            it[sound] = value.sound
            it[largeText] = value.largeText
            it[demo] = value.demo
            it[introductionSeen] = value.introductionSeen
            it[voiceQuiz] = value.voiceQuiz
        }
    }
}
