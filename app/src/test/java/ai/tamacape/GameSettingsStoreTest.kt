package ai.tamacape

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GameSettingsStoreTest {
    @Test fun journeyPersistsAndConcurrentRequestsCannotExceedDailyTarget() = runBlocking {
        val directory = Files.createTempDirectory("tamacape-settings").toFile()
        val file = directory.resolve("settings.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = GameSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            var store = open()
            assertTrue(store.read().largeText)
            store.addDemoJourney(100)
            assertEquals(0.0, store.read().demoJourneyKm, 0.0)
            store.write(GameSettings(demo = true, voiceQuiz = true))
            store.addDemoJourney(100)
            assertNull(store.claimDemoBonus(100))
            coroutineScope { repeat(20) { launch { store.addDemoJourney(100) } } }
            assertEquals(10.0, store.read().demoJourneyKm, 0.0)
            coroutineScope { repeat(20) { launch { store.claimDemoBonus(100) } } }
            assertEquals(100L, store.read().demoBonusDay)
            assertEquals(20L, store.read().demoJourneyXp)
            // A stale settings form must not overwrite mission progress or its reward record.
            store.write(GameSettings(demo = true, largeText = true, voiceQuiz = true))
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = open()
            assertEquals(100L, store.read().demoBonusDay)
            assertEquals(20L, store.read().demoJourneyXp)
            assertEquals(10.0, store.read().demoJourneyKm, 0.0)
            assertTrue(store.read().voiceQuiz)
            assertTrue(store.read().largeText)
            store.addDemoJourney(99)
            assertEquals(100L, store.read().demoJourneyDay)
            store.addDemoJourney(101)
            assertEquals(2.0, store.read().demoJourneyKm, 0.0)
            assertEquals(100L, store.claimDemoBonus(101))
            repeat(4) { store.addDemoJourney(101) }
            assertEquals(101L, store.claimDemoBonus(101))
            assertEquals(40L, store.read().demoJourneyXp)
            store.write(GameSettings(demo = false))
            store.addDemoJourney(102)
            assertEquals(101L, store.read().demoJourneyDay)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
