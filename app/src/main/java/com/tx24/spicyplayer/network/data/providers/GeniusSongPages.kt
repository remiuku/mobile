package com.tx24.spicyplayer.network.data.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tx24.spicyplayer.lyrics.spicy.romanization.HumanRomanization
import com.tx24.spicyplayer.network.data.awaitResponse
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackMatcher
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared public song search, record lookup and lyric page parsing. One instance serves both the
 * romanization and the translation lookups, which ask about the same song at the same moment:
 * what Genius answered is kept for a while and a request already out is waited for, so the two
 * share their searches, records and pages instead of doubling the burst Genius limits.
 */
internal class GeniusSongPages(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Answer(val at: Long, val value: Any?)
    private val answers = object : LinkedHashMap<String, Answer>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Answer>) = size > MAX_ANSWERS
    }
    private val asking = object : LinkedHashMap<String, Mutex>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Mutex>) = size > MAX_ANSWERS
    }

    /** [fetch]'s answer for [key], from a recent answer or a request already out. Failures aren't kept. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> remembered(key: String, fetch: suspend () -> T): T {
        val lock = synchronized(asking) { asking.getOrPut(key) { Mutex() } }
        return lock.withLock {
            val kept = synchronized(answers) { answers[key]?.takeIf { now() - it.at < ANSWER_MS } }
            if (kept != null) kept.value as T
            else fetch().also { value -> synchronized(answers) { answers[key] = Answer(now(), value) } }
        }
    }

    /**
     * Search the song, with extra reading-specific queries when looking for romanizations. With
     * [strict], a search that found nothing because every query failed throws instead of
     * answering "no hits", so a caller can tell an outage from a miss.
     */
    suspend fun search(title: String, artist: String, romanizations: Boolean = true, strict: Boolean = false): List<JsonObject> {
        val cleanTitle = title.replace(BRACKETED, "").trim()
        val cleanArtist = artist.replace(BRACKETED, "").trim()
        // Several credited artists ("ROSÉ, Bruno Mars") can bury the song under its translation
        // pages; the first artist alone finds it.
        val firstArtist = artist.split(ARTIST_SEPARATOR).first().trim().takeIf { it.isNotEmpty() && it != artist.trim() }
        val queries = if (romanizations) listOfNotNull(
            "$title $artist",
            firstArtist?.let { "$cleanTitle $it" },
            "$cleanTitle $cleanArtist Romanized",
            "$cleanTitle Romanized",
            "$title Genius Romanizations",
        ) else listOfNotNull("$title $artist", "$cleanTitle $cleanArtist", firstArtist?.let { "$cleanTitle $it" })
        val hits = mutableListOf<JsonObject>()
        val seen = mutableSetOf<Long>()
        var failure: Exception? = null
        for (query in queries) {
            if (query.isBlank()) continue
            try {
                val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim()).build()
                val sections = getJson(url.toString())?.getAsJsonObject("response")?.getAsJsonArray("sections")
                sections?.forEach { section ->
                    val s = section.asJsonObject
                    if (s.str("type") !in setOf("top_hit", "song")) return@forEach
                    s.getAsJsonArray("hits")?.forEach { h ->
                        val res = h.asJsonObject.getAsJsonObject("result") ?: return@forEach
                        val id = res.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
                        if (isSong(res) && seen.add(id)) hits += res
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                failure = e
            }
            if (romanizations && hits.any(::isRomanization)) break
        }
        if (strict && hits.isEmpty()) failure?.let { throw it }
        return hits.sortedBy { !isRomanization(it) }
    }

    /** A song, not an album: an album's id is a different id space and would fetch another song. */
    private fun isSong(hit: JsonObject): Boolean {
        val type = hit.str("_type").ifEmpty { hit.str("type") }
        return type.isEmpty() || type == "song"
    }

    /** Only entries that claim to be romanizations: the original-script page is no use. */
    fun isRomanization(hit: JsonObject): Boolean {
        if (hit.str("language").lowercase() in ROMAN_LANGUAGES) return true
        val fields = listOf(hit.str("title"), hit.str("full_title"), hit.str("title_with_featured"), artistOf(hit))
        return fields.any { ROMAN_HINT.containsMatchIn(it) }
    }

    /**
     * Whether a romanization page is for [artist]. They're filed under a Genius account, so the
     * page title is where the musician is named: "Ado - 踊 (Odo) (Romanized)". Unknown counts as yes.
     */
    fun byUs(hit: JsonObject, artist: String): Boolean {
        if (artist.isBlank()) return true
        val bare = hit.str("title").replace(ROMAN_HINT, "").trim(' ', '-', '(', ')', '[', ']')
        if (" - " !in bare) return true
        val ours = norm(artist)
        val theirs = norm(bare.substringBefore(" - "))
        if (ours.isEmpty() || theirs.isEmpty()) return true
        return ours in theirs || theirs in ours
    }

    /**
     * Whether a search hit is the song that was asked for. One title holds the other, whole or
     * with their bracketed parts (featured credits, alternate titles) set aside. Any one of our
     * credited artists in Genius's credits, featured ones included, is enough: a producer can be
     * filed under another name (the player's "32ki" is Genius's "サツキ (Satsuki)").
     */
    fun ours(hit: JsonObject, title: String, artist: String): Boolean {
        fun holds(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && (a == b || a in b || b in a)
        val theirTitle = hit.str("title")
        if (!holds(norm(theirTitle), norm(title)) && !holds(norm(theirTitle.replace(BRACKETED, "")), norm(title.replace(BRACKETED, "")))) return false
        val who = norm(artistOf(hit))
        val asked = norm(artist)
        if (asked.isEmpty() || who.isEmpty() || asked in who || who in asked) return true
        val credits = norm(hit.str("artist_names").ifEmpty { artistOf(hit) })
        return artist.split(ARTIST_SEPARATOR).map(::norm).any { it.length >= 2 && it in credits }
    }

    /**
     * The romanization Genius files against a song (`translation_songs`), if any. With [strict],
     * a record that couldn't be read throws instead of reading as none.
     */
    suspend fun linkedRomanization(songId: Long, strict: Boolean = false): JsonObject? = try {
        record(songId)
            ?.getAsJsonArray("translation_songs")
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.str("language").lowercase() in ROMAN_LANGUAGES }
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        if (strict) throw e
        null
    }

    /** A Genius song page's lyric lines, cleaned; short or unavailable pages have no result. */
    suspend fun linesAt(url: String, minimum: Int = 4, strict: Boolean = false): List<String>? =
        textAt(url, strict)?.let(HumanRomanization::cleanLines)?.takeIf { it.size >= minimum }

    /**
     * A page's lyrics as text, section headers ("[Chorus]") kept. With [strict], a page that
     * couldn't be read (limited, server error, no connection) throws; a missing page is still null.
     */
    suspend fun textAt(url: String, strict: Boolean = false): String? {
        if (url.isBlank()) return null
        return try {
            remembered("page:$url") {
                val html = client.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_UA).get().build())
                    .awaitResponse().use {
                        if (!it.isSuccessful && it.code != 404) throw IOException("Genius page unavailable (HTTP ${it.code})")
                        if (it.isSuccessful) it.body?.string().orEmpty() else ""
                    }
                geniusLyricsText(html).takeIf(String::isNotBlank)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (strict) throw e
            null
        }
    }

    suspend fun record(songId: Long): JsonObject? =
        getJson("https://genius.com/api/songs/$songId")?.getAsJsonObject("response")?.getAsJsonObject("song")

    // Genius' front answers OkHttp's own user agent with HTTP 401. A missing page is null; any
    // other refusal (rate limit, server error) throws, so it isn't taken for a missing page.
    private suspend fun getJson(url: String): JsonObject? = remembered("json:$url") {
        client.newCall(Request.Builder().url(url).header("Referer", "https://genius.com/").header("User-Agent", BROWSER_UA).get().build())
            .awaitResponse().use { r ->
                if (r.code == 404) return@use null
                if (!r.isSuccessful) throw IOException("Genius unavailable (HTTP ${r.code})")
                gson.fromJson(r.body?.string(), JsonObject::class.java)
            }
    }

    private fun artistOf(hit: JsonObject) = hit.getAsJsonObject("primary_artist")?.str("name").orEmpty()
    private fun norm(s: String) = SpotifyTrackMatcher.normalize(s).replace(" ", "")
    private fun JsonObject.str(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private companion object {
        const val MAX_ANSWERS = 64
        /** Long enough for one song's lookups to share; short enough that an edited page shows up. */
        const val ANSWER_MS = 10 * 60_000L
        val ROMAN_HINT = Regex("romani[sz]ed|romani[sz]ation|\\bromaji\\b", RegexOption.IGNORE_CASE)
        val ROMAN_LANGUAGES = setOf("romanization", "romanized")
        val BRACKETED = Regex("[(\\[{].*?[)\\]}]")
        /** Between the names in a player's artist credit: "32ki, Hatsune Miku & 重音テト". */
        val ARTIST_SEPARATOR = Regex("""\s*(?:,|&|、|/|×|\bfeat\.?|\bft\.?|\bx\b)\s*""", RegexOption.IGNORE_CASE)
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    }
}
