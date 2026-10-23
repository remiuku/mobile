package com.tx24.spicyplayer.network.canvas

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.network.data.awaitResponse
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Finds a track's Spotify Canvas: the short portrait video loop Spotify plays behind a song, as
 * an MP4 URL. Asked by Spotify track ID of the lookup services in [baseUrls], in order: the
 * project's own caching Worker (`server/canvas/`) when the build has one, then the third-party
 * service it fronts ([FALLBACK_URL]).
 *
 * The services are unofficial and may vanish or slow down, so all of it is best effort: any
 * failure is no URL, and the cover stage falls back to what it would show otherwise.
 *
 * Answers are memoised per track ID in memory and in [memoFile], misses included, since about two
 * tracks in three have no canvas. A miss is retried after [MISS_TTL_MS], in case one was added. A
 * failed lookup (network, timeout, server error from every service) is not a miss and is not
 * remembered. With a single service it is asked once more after [RETRY_DELAY_MS] first. After a
 * failure nothing is asked for a pause that starts at [FAILURE_PAUSE_MS] and doubles with each
 * failure in a row up to [MAX_FAILURE_PAUSE_MS], so a service that is down isn't asked on every
 * song but one that blipped is back soon.
 */
class SpotifyCanvasFinder(
    private val client: OkHttpClient,
    private val memoFile: File?,
    private val now: () -> Long = System::currentTimeMillis,
    private val baseUrls: List<String> = defaultBaseUrls(),
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    private val lock = Mutex()
    private var memo: MutableMap<String, MemoEntry>? = null
    @Volatile private var failedAt = Long.MIN_VALUE / 2
    @Volatile private var pauseMs = 0L

    /** The canvas MP4's URL, or null when the track has none or the lookup failed. */
    suspend fun find(trackId: String): String? {
        if (!isTrackId(trackId)) return null
        lock.withLock { memo()[trackId] }?.let { known ->
            if (known.url.isNotEmpty()) return known.url
            if (now() - known.at < MISS_TTL_MS) return null
        }
        if (now() - failedAt < pauseMs) return null
        val answer = askAll(trackId)
        if (answer == null) {
            pauseMs = (pauseMs * 2).coerceIn(FAILURE_PAUSE_MS, MAX_FAILURE_PAUSE_MS)
            failedAt = now()
            return null
        }
        pauseMs = 0L
        val url = (answer as? Answer.Found)?.url
        lock.withLock {
            memo()[trackId] = MemoEntry(url.orEmpty(), now())
            save()
        }
        return url
    }

    /** Drops a remembered canvas that failed to play, so the next ask looks it up again. */
    suspend fun forget(trackId: String) {
        lock.withLock {
            if (memo().remove(trackId) != null) save()
        }
    }

    /**
     * The first readable answer from [baseUrls], or null when every one failed. A single service
     * gets a second try after [retryDelayMs]; with more, the next one is the second try.
     */
    private suspend fun askAll(trackId: String): Answer? {
        val attempts = if (baseUrls.size == 1) baseUrls + baseUrls else baseUrls
        attempts.forEachIndexed { index, baseUrl ->
            if (index > 0 && baseUrl == attempts[index - 1]) delay(retryDelayMs)
            try {
                lookup(baseUrl, trackId)?.let { return it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** What the service said, or null when it couldn't be asked or answered with an error. */
    private suspend fun lookup(baseUrl: String, trackId: String): Answer? {
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("trackId", trackId).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).awaitResponse().use { response ->
            return when {
                response.isSuccessful -> {
                    val body = withContext(Dispatchers.IO) { response.body.string() }
                    parse(body, trackId)
                }
                // Refused as a bad request: this ID will never have an answer.
                response.code == 400 -> Answer.None
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    // Built by hand rather than through Gson's reflection, which R8's renaming would break.
    private fun memo(): MutableMap<String, MemoEntry> = memo ?: run {
        val loaded = mutableMapOf<String, MemoEntry>()
        runCatching {
            JsonParser.parseString(memoFile!!.readText()).asJsonObject.entrySet().forEach { (key, value) ->
                val o = value.asJsonObject
                loaded[key] = MemoEntry(o.get("url").asString, o.get("at").asLong)
            }
        }
        loaded.also { memo = it }
    }

    private fun save() {
        val file = memoFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            val json = JsonObject()
            memo?.forEach { (key, entry) ->
                json.add(key, JsonObject().apply { addProperty("url", entry.url); addProperty("at", entry.at) })
            }
            tmp.writeText(json.toString())
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }
    }

    private class MemoEntry(val url: String, val at: Long)

    /** A readable answer from the service. */
    internal sealed interface Answer {
        data class Found(val url: String) : Answer
        data object None : Answer
    }

    companion object {
        /** The third-party lookup service, asked as `?trackId=<id>`, after the project's own Worker. */
        const val FALLBACK_URL = "https://spotify-canva.vercel.app/api/canvas"

        /** The build's own Worker (`SPOTIFY_CANVAS_URL`, blank for none), then [FALLBACK_URL]. */
        fun defaultBaseUrls(): List<String> = listOfNotNull(
            BuildConfig.SPOTIFY_CANVAS_URL.trim().takeIf { it.startsWith("https://") },
            FALLBACK_URL,
        ).distinct()

        /** How long a miss is trusted before the track is asked about again: a day, so a stretch where the service wrongly answered "none" doesn't cost canvases for long. */
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000

        /** How long nothing is asked after a first failed lookup; it doubles with each one after. */
        const val FAILURE_PAUSE_MS = 60L * 1000

        /** The longest pause after failed lookups in a row. */
        const val MAX_FAILURE_PAUSE_MS = 30L * 60 * 1000

        /** The wait before asking a lone service a second time. */
        const val RETRY_DELAY_MS = 1500L

        /** A Spotify track ID: 22 base-62 characters. */
        internal fun isTrackId(id: String) = id.length == 22 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }

        /**
         * Reads the service's answer: `{"canvasesList":[{"canvasUrl":"…mp4","trackUri":"spotify:track:…"}]}`.
         * The first entry for [trackId] with an HTTPS MP4 wins; an empty list is [Answer.None]. A body
         * that isn't that shape is null, a failure rather than a miss.
         */
        internal fun parse(json: String, trackId: String): Answer? = runCatching {
            val list = JsonParser.parseString(json).asJsonObject.getAsJsonArray("canvasesList") ?: return null
            val url = list.asSequence()
                .mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                .filter { entry ->
                    val uri = entry.get("trackUri")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    uri.isEmpty() || uri.substringAfterLast(':') == trackId
                }
                .mapNotNull { entry -> entry.get("canvasUrl")?.takeIf { it.isJsonPrimitive }?.asString }
                .firstOrNull(::isVideoUrl)
            if (url != null) Answer.Found(url) else Answer.None
        }.getOrNull()

        /** Only an HTTPS MP4: some canvases are still images, which the cover stage already has. */
        private fun isVideoUrl(url: String) =
            url.startsWith("https://") && url.substringBefore('?').endsWith(".mp4", ignoreCase = true)
    }
}
