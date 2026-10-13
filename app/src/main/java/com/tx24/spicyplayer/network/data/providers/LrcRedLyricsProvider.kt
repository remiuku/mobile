package com.tx24.spicyplayer.network.data.providers

import com.google.gson.Gson
import com.tx24.spicyplayer.network.data.*
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackCandidate
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackMatcher
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackResolution
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * lrc.red serves Apple Music's lyrics TTML by ISRC, word, line and unsynced alike, with Apple's
 * `itunes:` names renamed. Its site search (`search.json`) finds the ISRC from title and artist;
 * the hits go through the Spotify matcher, which already refuses other versions of a song.
 * Where Apple Music has no lyrics it serves a sync of its own making instead ([originOf]), so an
 * answer ranks as Apple Music's or as lrc.red's AI syncs.
 */
@Singleton
class LrcRedLyricsProvider @Inject constructor(private val client: OkHttpClient, private val gson: Gson) : RemoteLyricsProvider {
    override val descriptor = LyricsSourceDescriptor(
        RemoteLyricsSource.LRC_RED_ID, "lrc.red", 45,
        setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC, LyricsCapability.PLAIN_TEXT, LyricsCapability.TRANSLITERATION),
        upstreamFamily = "apple_music", releaseChannel = SourceReleaseChannel.EXTENDED,
    )

    override suspend fun fetch(request: LyricsLookupRequest): ProviderResult = guarded {
        val search = "$BASE/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", "${request.title} ${request.artist}").build()
        val candidates = client.json(search, gson).getAsJsonArray("hits").orEmpty().mapNotNull { element ->
            val hit = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            fun string(key: String) = hit.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            SpotifyTrackCandidate(
                id = string("isrc") ?: return@mapNotNull null,
                title = string("title") ?: return@mapNotNull null,
                artists = listOfNotNull(string("artist")),
                album = string("album").orEmpty(),
                durationMs = ((hit.get("duration")?.asDouble ?: 0.0) * 1000).toLong(),
            )
        }
        val local = LocalTrackMetadata(request.title, request.artist, request.album, request.durationSeconds * 1000L)
        val match = SpotifyTrackMatcher.resolve(local, candidates) as? SpotifyTrackResolution.Matched
            ?: return@guarded ProviderResult.Miss
        for (isrc in listOf(match.track, *match.alternates.toTypedArray()).map { it.candidate.id }) {
            val ttml = try {
                client.text("$BASE/s/$isrc.ttml")
            } catch (missing: HttpStatusException) {
                if (missing.code == 404) continue else throw missing
            }
            if (ttml.isBlank()) continue
            return@guarded ProviderResult.Hit(RemoteLyricsPayload(
                ttmlLyrics = appleTtml(ttml),
                sourceId = descriptor.id,
                attribution = LyricsAttribution("lrc.red", originName = originOf(ttml)),
            ))
        }
        ProviderResult.Miss
    }

    companion object {
        private const val BASE = "https://lrc.red"
        private val RENAMED_ATTRIBUTE = Regex("""(?<=\s)lrc:(?=[A-Za-z]+=)""")

        /**
         * Whose lyrics lrc.red's [ttml] are: Apple Music's always carry `leadingSilence` on their
         * metadata (and usually agents); the syncs lrc.red makes itself have neither. Read on the
         * TTML as served, before [appleTtml] renames anything.
         */
        internal fun originOf(ttml: String): String =
            if (ttml.contains("leadingSilence")) "Apple Music" else LyricsOrigins.LRC_RED_AI_NAME

        /** Puts back Apple's names (`itunes:timing`, `itunes:key`, `iTunesMetadata`) the parser reads. */
        internal fun appleTtml(ttml: String): String = ttml
            .replace("xmlns:lrc=", "xmlns:itunes=")
            .replace(RENAMED_ATTRIBUTE, "itunes:")
            .replace("<sourceMetadata", "<iTunesMetadata")
            .replace("</sourceMetadata>", "</iTunesMetadata>")
    }
}

private fun com.google.gson.JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList().orEmpty()
