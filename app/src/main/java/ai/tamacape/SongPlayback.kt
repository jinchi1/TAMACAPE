package ai.tamacape

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

class SongPlayback : MediaBrowserService() {
    companion object {
        private var live: SongPlayback? = null
        internal fun position(): Long = runCatching { live?.player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

        internal var currentId: String? = null
            private set
        internal var playing = false
            private set
        internal var repeat = false
            private set
    }
    private lateinit var session: MediaSession
    private lateinit var album: SongAlbum
    private var player: MediaPlayer? = null
    private var title = ""
    private var pendingPlay: String? = null
    private var car: android.car.Car? = null
    private var restrictions: android.car.drivingstate.CarUxRestrictionsManager? = null
    private fun parked(): Boolean = runCatching {
        restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false
    }.getOrDefault(false)
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()

    override fun onCreate() {
        super.onCreate()
        live = this
        album = SongAlbum(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("songs", "타마카페 동요", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "TamaCafeSongs").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (player == null) move(1) else resume() }
                override fun onPause() = pause()
                override fun onStop() = clear()
                override fun onSkipToNext() = move(1)
                override fun onSkipToPrevious() = move(-1)
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { mediaId?.let(::open) }
                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    album.list().firstOrNull { it.title.contains(query.orEmpty(), ignoreCase = true) }
                        ?.let { open(it.id) }
                }
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        publish()
        runCatching {
            car = android.car.Car.createCar(this, null, android.car.Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connection, ready ->
                restrictions?.unregisterListener()
                restrictions = if (ready) connection.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE)
                    as? android.car.drivingstate.CarUxRestrictionsManager else null
                restrictions?.registerListener { if (it.isRequiresDistractionOptimization) pause() }
                val pending = pendingPlay
                pendingPlay = null
                if (!parked()) pause() else pending?.let(::open)
            }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? =
        if (clientUid == applicationInfo.uid) BrowserRoot("songs", null) else null

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(if (parentId != "songs") mutableListOf() else album.list().map { song ->
            MediaBrowser.MediaItem(android.media.MediaDescription.Builder().setMediaId(song.id)
                .setTitle(song.title).build(), MediaBrowser.MediaItem.FLAG_PLAYABLE)
        }.toMutableList())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "play" -> intent.getStringExtra("id")?.let { if (currentId == it && player != null) resume() else open(it) }
            "seek" -> if (parked()) {
                player?.let { it.seekTo(intent.getLongExtra("position", 0).coerceIn(0, it.duration.toLong()).toInt()) }
                publish()
            }
            "pause" -> pause()
            "next" -> move(1)
            "previous" -> move(-1)
            "repeat" -> { repeat = !repeat; player?.isLooping = repeat }
            "stop" -> clear()
        }
        return START_NOT_STICKY
    }

    private fun move(delta: Int) {
        val songs = album.list()
        if (songs.isEmpty()) return
        val index = songs.indexOfFirst { it.id == currentId }
        open(songs[if (index < 0) 0 else Math.floorMod(index + delta, songs.size)].id)
    }

    private fun open(id: String) {
        if (restrictions == null) { pendingPlay = id; return }
        if (!parked()) return
        val song = album.list().firstOrNull { it.id == id } ?: return
        clear()
        try {
            player = MediaPlayer()
            player?.apply {
                setAudioAttributes(attributes)
                setDataSource(song.file.absolutePath)
                prepare()
                isLooping = repeat
                setOnCompletionListener { pause(); seekTo(0) }
                setOnErrorListener { _, _, _ -> clear(); true }
            }
            title = song.title
            currentId = id
            session.setMetadata(android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, song.duration).build())
            resume()
        } catch (_: Exception) { clear() }
    }

    private fun resume() {
        val media = player ?: return
        if (!parked()) return
        // Establish foreground playback before requesting audio focus.
        startForeground(218, Notification.Builder(this, "songs")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build())
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pause()
            return
        }
        media.start()
        playing = true
        publish()
    }

    private fun publish() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                player?.currentPosition?.toLong() ?: 0L, if (playing) 1f else 0f).build())
    }

    private fun pause() {
        pendingPlay = null
        player?.let { if (it.isPlaying) it.pause() }
        playing = false
        audio.abandonAudioFocusRequest(focus)
        stopForeground(STOP_FOREGROUND_REMOVE)
        publish()
    }

    private fun clear() {
        player?.release()
        player = null
        currentId = null
        session.setMetadata(null)
        pause()
    }

    override fun onDestroy() {
        runCatching { restrictions?.unregisterListener() }
        car?.disconnect()
        clear()
        session.release()
        live = null
        super.onDestroy()
    }
}
