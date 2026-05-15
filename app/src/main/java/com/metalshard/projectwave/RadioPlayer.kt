package com.metalshard.projectwave

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RadioPlayer(context: Context) {
    private var controller: MediaController? = null

    private val _playbackInfo = MutableStateFlow("Stopped")
    val playbackInfo: StateFlow<String> = _playbackInfo

    private val _streamTitle = MutableStateFlow("")
    val streamTitle: StateFlow<String> = _streamTitle

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupControllerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val songTitle = mediaMetadata.title?.toString() ?: ""
                val stationName = mediaMetadata.artist?.toString() ?: ""

                _streamTitle.value = when {
                    songTitle.isNotBlank() && songTitle != stationName -> "$stationName - $songTitle"
                    else -> stationName
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _playbackInfo.value = "Buffering..."
                    Player.STATE_READY -> {
                        val format = controller?.currentTracks?.groups?.firstOrNull()?.getTrackFormat(0)

                        val codec = format?.sampleMimeType?.split("/")?.lastOrNull()?.uppercase()

                        val bitrate = if (format != null && format.bitrate > 0) "${format.bitrate / 1000}kbps" else null

                        _playbackInfo.value = when {
                            codec != null && bitrate != null -> "$codec $bitrate"
                            codec != null -> codec
                            bitrate != null -> bitrate
                            else -> "Playing"
                        }
                    }
                    else -> _playbackInfo.value = "Stopped"
                }
            }
        })
    }

    fun play(station: RadioStation) {
        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(station.name)
                    .setArtworkUri(android.net.Uri.parse(station.imageUrl))
                    .build()
            )
            .build()

        controller?.setMediaItem(mediaItem)
        controller?.prepare()
        controller?.play()
    }

    fun stop() {
        controller?.stop()
        _streamTitle.value = ""
    }
}