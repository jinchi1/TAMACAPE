package ai.tamacape

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ai.tamacape.storage.AdventureDatabase

internal data class PuzzleCheckpoint(
    val puzzle: Int = 0,
    val explained: Boolean = false,
    val selection: List<Int> = emptyList(),
    val usedHint: Boolean = false,
    val madeMistake: Boolean = false,
    val stars: Int = 0,
)

internal data class AdventureSave(
    val progress: Progress = Progress(0, 0, 0),
    val stage: Int? = null,
    val costume: String? = null,
    val furniture: String? = null,
    val owned: Set<String> = emptySet(),
    val checkpoints: Map<Int, PuzzleCheckpoint> = emptyMap(),
    val revision: Int = 0,
    val companion: String = "mobi",
) {
    fun awardFirstSong(hasGeneratedSong: Boolean): AdventureSave {
        val badge = "song-first-created"
        if (!hasGeneratedSong || badge in owned) return this
        return copy(owned = owned + badge,
            progress = progress.copy(xp = Math.addExact(progress.xp, 20)))
    }

    fun checkpoint(stage: Int) = checkpoints[stage] ?: PuzzleCheckpoint()

    fun starsFor(stage: Int): Int =
        if (stage in 0 until progress.cleared) checkpoint(stage).stars.coerceAtLeast(1) else 0

    val totalStars: Int get() = stages.indices.sumOf(::starsFor)

    fun restartStage(stage: Int): AdventureSave {
        require(progress.isUnlocked(stage))
        return withCheckpoint(stage, PuzzleCheckpoint(stars = starsFor(stage))).copy(stage = stage)
    }

    fun advancePuzzle(stage: Int): AdventureSave {
        require(progress.isUnlocked(stage))
        val current = checkpoint(stage)
        if (!current.explained || current.puzzle >= stages[stage].puzzles.size) return this
        val next = current.puzzle + 1
        val finished = next == stages[stage].puzzles.size
        return withCheckpoint(stage, current.copy(
            puzzle = next, explained = false, selection = emptyList(),
            stars = if (finished) maxOf(current.stars, stageGrade(current.usedHint, current.madeMistake)) else current.stars,
        )).copy(progress = progress.complete(stage, next))
    }

    fun withCheckpoint(stage: Int, checkpoint: PuzzleCheckpoint) =
        copy(checkpoints = checkpoints + (stage to checkpoint))
}

internal class AdventureStore(
    context: Context,
    databaseName: String = "adventure.db",
    private val legacyName: String = "adventure",
) {
    private val app = context.applicationContext
    private val database = Room.databaseBuilder(app, AdventureDatabase::class.java, databaseName)
        .addMigrations(CheckpointGradesMigration, CompanionMigration)
        .build()
    private val dao = database.saves()

    fun load(): AdventureSave = database.runInTransaction<AdventureSave> {
        val profile = dao.profile()
        if (profile == null) {
            val legacy = readLegacy(app.getSharedPreferences(legacyName, Context.MODE_PRIVATE))
            repeat(legacy.progress.cleared) { recordReward(it) }
            write(legacy)
            legacy
        } else {
            AdventureSave(
                progress = Progress(profile.cleared, profile.xp, profile.coins),
                stage = profile.stage,
                costume = profile.costume,
                furniture = profile.furniture,
                owned = dao.inventory().map { it.name }.toSet(),
                checkpoints = dao.checkpoints().associate {
                    it.stage to PuzzleCheckpoint(
                        it.puzzle, it.explained, parseSelection(it.selection),
                        it.usedHint, it.madeMistake, it.stars,
                    )
                },
                revision = profile.revision,
                companion = profile.companion,
            )
        }
    }

    fun update(previous: AdventureSave, next: AdventureSave): AdventureSave =
        database.runInTransaction<AdventureSave> {
            check(dao.profile()?.revision == previous.revision) { "Save changed concurrently" }
            if (next.progress.cleared > previous.progress.cleared) {
                check(next.progress.cleared == previous.progress.cleared + 1)
                recordReward(previous.progress.cleared)
            }
            next.copy(revision = previous.revision + 1).also(::write)
        }

    fun reset(previous: AdventureSave): AdventureSave = database.runInTransaction<AdventureSave> {
        check(dao.profile()?.revision == previous.revision) { "Save changed concurrently" }
        dao.clearCheckpoints()
        dao.clearItems()
        dao.clearRewards()
        AdventureSave(revision = previous.revision + 1).also(::write)
    }

    fun close() = database.close()

    private fun recordReward(stage: Int) {
        dao.insertReward(AdventureDatabase.Reward().apply { eventKey = "stage-first-clear-$stage" })
    }

    private fun write(save: AdventureSave) {
        dao.putProfile(AdventureDatabase.Profile().apply {
            companion = save.companion
            revision = save.revision
            cleared = save.progress.cleared
            xp = save.progress.xp
            coins = save.progress.coins
            stage = save.stage
            costume = save.costume
            furniture = save.furniture
        })
        dao.putCheckpoints(save.checkpoints.map { (index, checkpoint) ->
            AdventureDatabase.Checkpoint().apply {
                stage = index
                puzzle = checkpoint.puzzle
                explained = checkpoint.explained
                selection = checkpoint.selection.joinToString(",")
                usedHint = checkpoint.usedHint
                madeMistake = checkpoint.madeMistake
                stars = checkpoint.stars
            }
        })
        dao.putItems(save.owned.map { item ->
            AdventureDatabase.OwnedItem().apply { name = item }
        })
    }

    private fun readLegacy(prefs: SharedPreferences): AdventureSave {
        val cleared = prefs.getInt("cleared", 0).coerceIn(0, stages.size)
        val owned = (costumes + furniture).filter { prefs.getBoolean("owned-$it", false) }.toSet()
        val refund = owned.sumOf { if (it in furniture) 75 else 50 }
        return AdventureSave(
            progress = Progress(cleared, prefs.getInt("xp", 0), prefs.getInt("coins", 0) + refund),
            stage = prefs.getInt("stage", -1).takeIf { it in stages.indices && it <= cleared },
            costume = null,
            furniture = null,
            owned = owned,
            checkpoints = stages.indices.associateWith { index ->
                val puzzles = stages[index].puzzles
                val puzzle = prefs.getInt("puzzle-$index", 0).coerceIn(0, puzzles.size)
                PuzzleCheckpoint(
                    puzzle,
                    puzzle < puzzles.size && prefs.getBoolean("explained-$index", false),
                    parseSelection(prefs.getString("selection-$index", "").orEmpty())
                        .filter { puzzles.getOrNull(puzzle)?.options?.indices?.contains(it) == true },
                )
            },
        )
    }

    private fun parseSelection(value: String) =
        value.split(',').mapNotNull { it.toIntOrNull() }.distinct()
}

private object CheckpointGradesMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Checkpoint ADD COLUMN usedHint INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Checkpoint ADD COLUMN madeMistake INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Checkpoint ADD COLUMN stars INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE Checkpoint SET stars = 1 WHERE stage < (SELECT cleared FROM Profile WHERE id = 0)")
    }
}

private object CompanionMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Profile ADD COLUMN companion TEXT NOT NULL DEFAULT 'mobi'")
        val refund = costumes.sumOf { item ->
            db.query("SELECT COUNT(*) FROM OwnedItem WHERE name = ?", arrayOf(item)).use { it.moveToFirst(); it.getInt(0) * 50 }
        } + furniture.sumOf { item ->
            db.query("SELECT COUNT(*) FROM OwnedItem WHERE name = ?", arrayOf(item)).use { it.moveToFirst(); it.getInt(0) * 75 }
        }
        db.execSQL("UPDATE Profile SET coins = coins + ?, costume = NULL, furniture = NULL", arrayOf(refund))
    }
}
