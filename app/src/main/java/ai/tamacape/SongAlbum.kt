package ai.tamacape

import android.content.Context
import android.media.MediaMetadataRetriever
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class SavedSong(val id: String, val title: String, val lyrics: String,
    val stage: Int, val companion: String, val created: Long, val duration: Long,
    val favorite: Boolean, val file: File, val example: Boolean = false)

internal class SongAlbum(context: Context) {
    private val folder = File(context.filesDir, "songs").apply { mkdirs() }

    fun list(): List<SavedSong> = folder.listFiles().orEmpty().filter { it.extension == "json" }
        .mapNotNull { metadata ->
            runCatching {
                val data = JSONObject(metadata.readText())
                val extension = if (data.optBoolean("sample")) "wav" else "mp3"
                val file = File(folder, metadata.nameWithoutExtension + "." + extension)
                check(file.isFile && file.length() in 1024..1000000)
                check(data.getInt("stage") in stages.indices)
                check(companions.any { it.id == data.getString("companion") })
                check(data.getLong("duration") in 19000..33000)
                SavedSong(metadata.nameWithoutExtension, data.getString("title"), data.getString("lyrics"),
                    data.getInt("stage"), data.getString("companion"), data.getLong("created"),
                    data.getLong("duration"), data.optBoolean("favorite"), file, data.optBoolean("example", data.optBoolean("sample")))
            }.getOrNull()
        }.sortedByDescending { it.created }

    fun save(text: String, audio: ByteArray, stage: Int, companion: String, sample: Boolean = false, example: Boolean = sample): SavedSong {
        require(audio.size in 1024..1000000)
        require(stage in stages.indices)
        require(companions.any { it.id == companion })
        val lyrics = JSONObject(text)
        val title = lyrics.getString("title")
        val lines = listOf("verse", "chorus").joinToString("\n\n") { section ->
            val array = lyrics.getJSONArray(section)
            (0 until array.length()).joinToString("\n") { array.getString(it) }
        }
        val id = UUID.randomUUID().toString()
        val extension = if (sample) "wav" else "mp3"
        val file = File(folder, "$id.$extension")
        val temporary = File(folder, "$id.part.$extension")
        try {
            temporary.writeBytes(audio)
            val duration = MediaMetadataRetriever().use {
                it.setDataSource(temporary.absolutePath)
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            }
            check(duration in 19000..33000)
            check(temporary.renameTo(file))
            return SavedSong(id, title, lines, stage, companion, System.currentTimeMillis(),
                duration, false, file, example).also(::write)
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally { temporary.delete() }
    }

    fun write(song: SavedSong) {
        val target = android.util.AtomicFile(File(folder, "${song.id}.json"))
        val output = target.startWrite()
        try {
            output.write(JSONObject().put("title", song.title).put("lyrics", song.lyrics)
                .put("stage", song.stage).put("companion", song.companion).put("created", song.created)
                .put("example", song.example).put("sample", song.file.extension == "wav").put("duration", song.duration).put("favorite", song.favorite).toString().toByteArray())
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }

    fun delete(song: SavedSong) {
        android.util.AtomicFile(File(folder, "${song.id}.json")).delete()
        song.file.delete()
    }
}
