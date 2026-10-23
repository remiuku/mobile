package com.tx24.spicyplayer.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsFolder
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.network.data.TrackNameCleaner
import com.tx24.spicyplayer.network.data.measuredQuality
import java.security.MessageDigest

/*
 * What the lyrics screen and the home screen widget agree on: which session is followed, what the
 * song is called for lookup, and which of the user's own lyrics come before the sources.
 */

/** Neither playing nor stopped: the player is loading, connecting or changing songs. */
internal val IN_BETWEEN_STATES = setOf(
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
)

/**
 * The session to follow among [sessions]: the one followed now ([currentToken]) while [stickiness]
 * keeps it, else a playing local player over a playing remote mirror, else the one followed now,
 * else a local player, else any. The caller resets [stickiness] when the pick changes.
 */
internal fun bestSession(
    sessions: List<MediaController>,
    currentToken: MediaSession.Token?,
    stickiness: SessionStickiness,
    nowMs: Long,
): MediaController? {
    val current = sessions.firstOrNull { it.sessionToken == currentToken }
    val kept = current?.takeIf {
        val state = it.playbackState?.state
        stickiness.keep(state == PlaybackState.STATE_PLAYING || state in IN_BETWEEN_STATES, nowMs)
    }
    return kept ?: sessions.firstOrNull {
        it.playbackState?.state == PlaybackState.STATE_PLAYING &&
            it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
    } ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: current
        ?: sessions.firstOrNull {
            it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
        } ?: sessions.firstOrNull()
}

/** What lyrics are keyed on: title + artist only, because players fill metadata in across several updates. */
internal fun MediaMetadata?.lyricsKey(): String? = this?.let {
    val title = (getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        ?.trim()?.lowercase().orEmpty()
    val artist = (getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim()?.lowercase().orEmpty()
    if (title.isBlank() && artist.isBlank()) null else "$title\u001f$artist"
}

/** What the sources are asked for this song: its names cleaned up for lookup. */
internal fun MediaMetadata.lookupRequest(spotifyTrackId: String?): LyricsLookupRequest {
    val names = TrackNameCleaner.clean(
        title = getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
        artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
    )
    return LyricsLookupRequest(
        artist = names.artist,
        title = names.title,
        album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        durationSeconds = (getLong(MediaMetadata.METADATA_KEY_DURATION) / 1_000L).coerceAtLeast(0L).toInt(),
        spotifyTrackId = spotifyTrackId,
    )
}

/** The song's lyrics from the Lyrics Manager: a saved song, else a file in the lyrics folder. */
internal fun savedLyricsFor(request: LyricsLookupRequest, store: LocalLyricsStore, folder: LyricsFolder): RemoteLyricsSelection? =
    store.get(request.title, request.artist)?.let { savedSelection(it, persistent = true) }
        ?: folder.lyrics(request.title, request.artist)?.let { ttml ->
            val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = FOLDER_SOURCE.id)
            RemoteLyricsSelection(FOLDER_SOURCE, payload, payload.measuredQuality())
                .takeIf { it.quality != RemoteLyricsQuality.NONE }
        }

/** [ttml] from the Lyrics Manager as a pick: saved ([persistent]) or uploaded just once. */
internal fun savedSelection(ttml: String, persistent: Boolean): RemoteLyricsSelection {
    val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = LOCAL_SOURCE.id)
    return RemoteLyricsSelection(if (persistent) LOCAL_SOURCE else UPLOADED_SOURCE, payload, payload.measuredQuality())
}

/** The Spotify track ID the player gives for this song, if it gives one. */
internal fun MediaMetadata?.sessionSpotifyId(): String? = sequenceOf(
    this?.description?.mediaId,
    this?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
    this?.description?.mediaUri?.toString(),
).mapNotNull { it.spotifyTrackId() }.firstOrNull()

/** The key a manual Spotify ID is saved under (`spotify_id_overrides`): title, artist and length. */
internal fun MediaMetadata?.overrideKey(): String? = this?.let {
    val title = getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.lowercase().orEmpty()
    val artist = (getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim()?.lowercase().orEmpty()
    if (title.isBlank() || artist.isBlank()) return null
    val identity = "$title\u001f$artist\u001f${getLong(MediaMetadata.METADATA_KEY_DURATION)}"
    MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
