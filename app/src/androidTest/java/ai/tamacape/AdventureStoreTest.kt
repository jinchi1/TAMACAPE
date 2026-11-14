package ai.tamacape

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdventureStoreTest {
    @Test fun oldShopMigratesAndRefundsOnlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-" + UUID.randomUUID()
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `Profile` (`id` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `cleared` INTEGER NOT NULL, `xp` INTEGER NOT NULL, `coins` INTEGER NOT NULL, `stage` INTEGER, `costume` TEXT, `furniture` TEXT, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Checkpoint` (`stage` INTEGER NOT NULL, `puzzle` INTEGER NOT NULL, `explained` INTEGER NOT NULL, `selection` TEXT NOT NULL, `usedHint` INTEGER NOT NULL DEFAULT 0, `madeMistake` INTEGER NOT NULL DEFAULT 0, `stars` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`stage`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `OwnedItem` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Reward` (`eventKey` TEXT NOT NULL, PRIMARY KEY(`eventKey`))")
            db.execSQL("INSERT INTO Profile VALUES (0, 0, 3, 300, 25, 2, '안전모', '민트 러그')")
            db.execSQL("INSERT INTO OwnedItem VALUES ('안전모'), ('민트 러그')")
            db.version = 2
        }
        try {
            AdventureStore(context, name, name).let { store ->
                val loaded = store.load()
                assertEquals(150, loaded.progress.coins)
                assertEquals("mobi", loaded.activeCompanion.id)
                val chosen = store.update(loaded, loaded.chooseCompanion("lulu"))
                assertEquals(50, chosen.progress.coins)
                store.close()
            }
            AdventureStore(context, name, name).let { store ->
                assertEquals("lulu", store.load().activeCompanion.id)
                assertEquals(50, store.load().progress.coins)
                store.close()
            }
        } finally {
            context.deleteDatabase(name)
            context.deleteSharedPreferences(name)
        }
    }


    @Test fun replayRestoresCheckpointAndKeepsRewardBalance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "replay-${UUID.randomUUID()}"
        val store = AdventureStore(context, name, name)
        try {
            val initial = store.load()
            var saved = store.update(initial, initial.copy(progress = Progress(1, 100, 100))
                .withCheckpoint(0, PuzzleCheckpoint(puzzle = 2, stars = 1)))
            saved = store.update(saved, saved.restartStage(0))
            saved = store.update(saved, saved.withCheckpoint(0,
                saved.checkpoint(0).copy(selection = listOf(1), explained = true)).advancePuzzle(0))
            assertEquals(saved, store.load())
            assertEquals(1, store.load().checkpoint(0).puzzle)
            saved = store.update(saved, saved.withCheckpoint(0,
                saved.checkpoint(0).copy(explained = true)).advancePuzzle(0))
            assertEquals(3, store.load().starsFor(0))
            assertEquals(Progress(1, 100, 100), store.load().progress)
        } finally {
            store.close()
            context.deleteDatabase(name)
            context.deleteSharedPreferences(name)
        }
    }

    @Test fun concurrentClearAwardsOnceAndResetAllowsNewProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}"
        val first = AdventureStore(context, name, name)
        val second = AdventureStore(context, name, name)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val initial = first.load()
            val completed = initial.withCheckpoint(0, PuzzleCheckpoint(puzzle = 2))
                .copy(progress = initial.progress.complete(0, 2), stage = 0)
            val attempts = listOf(first, second).map { store ->
                pool.submit(Callable {
                    check(start.await(10, TimeUnit.SECONDS))
                    runCatching { store.update(initial, completed) }.isSuccess
                })
            }
            start.countDown()
            assertEquals(1, attempts.count { it.get(15, TimeUnit.SECONDS) })
            val rewarded = first.load()
            assertEquals(Progress(1, 100, 100), rewarded.progress)
            val equipped = first.update(rewarded, rewarded.copy(
                owned = setOf("안전모"), costume = "안전모",
                progress = rewarded.progress.copy(coins = 50),
            ))
            val reset = first.reset(equipped)
            assertEquals(AdventureSave(revision = reset.revision), second.load())
            assertFalse(runCatching { second.update(equipped, equipped) }.isSuccess)
            val replay = first.update(reset, reset.copy(progress = reset.progress.complete(0, 2)))
            assertEquals(Progress(1, 100, 100), replay.progress)
            assertTrue(first.load().owned.isEmpty())
        } finally {
            pool.shutdownNow()
            first.close()
            second.close()
            context.deleteDatabase(name)
            context.deleteSharedPreferences(name)
        }
    }
}
