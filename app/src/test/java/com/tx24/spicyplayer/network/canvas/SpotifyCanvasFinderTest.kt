package com.tx24.spicyplayer.network.canvas

import com.tx24.spicyplayer.network.canvas.SpotifyCanvasFinder.Answer
import com.tx24.spicyplayer.network.canvas.SpotifyCanvasFinder.Companion.parse
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotifyCanvasFinderTest {
    private val id = "4Dvkj6JhhA12EX05fT7y2e"
    private val mp4 = "https://canvaz.scdn.co/upload/licensor/x/video/abc.cnvs.mp4"
    private val found = """{"canvasesList":[{"id":"abc","canvasUrl":"$mp4","trackUri":"spotify:track:$id",
        "artist":{"artistUri":"spotify:artist:1","artistName":"Harry Styles"},"canvasUri":"spotify:canvas:1"}]}"""

    @Test
    fun `a list with a canvas gives its MP4`() {
        assertEquals(Answer.Found(mp4), parse(found, id))
    }

    @Test
    fun `an empty list is no canvas`() {
        assertEquals(Answer.None, parse("""{"canvasesList":[]}""", id))
    }

    @Test
    fun `entries for another track or without a video are skipped`() {
        val json = """{"canvasesList":[
            {"canvasUrl":"https://canvaz.scdn.co/other.mp4","trackUri":"spotify:track:0VjIjW4GlUZAMYd2vXMi3b"},
            {"canvasUrl":"https://canvaz.scdn.co/still.jpg","trackUri":"spotify:track:$id"},
            {"canvasUrl":"http://canvaz.scdn.co/plain.mp4","trackUri":"spotify:track:$id"},
            {"canvasUrl":"$mp4?x=1","trackUri":"spotify:track:$id"}]}"""
        assertEquals(Answer.Found("$mp4?x=1"), parse(json, id))
        assertEquals(Answer.None, parse("""{"canvasesList":[{"canvasUrl":"https://canvaz.scdn.co/still.jpg"}]}""", id))
    }

    @Test
    fun `a malformed body is a failure, not a miss`() {
        assertNull(parse("not json", id))
        assertNull(parse("""{"error":"Missing trackId parameter"}""", id))
        assertNull(parse("""["canvasesList"]""", id))
    }

    @Test
    fun `only real track IDs are asked about`() {
        var calls = 0
        val finder = newFinder(fake { calls++; 200 to found })
        assertNull(runBlocking { finder.find("not-an-id") })
        assertNull(runBlocking { finder.find("") })
        assertEquals(0, calls)
    }

    @Test
    fun `answers are remembered per track, misses included`() {
        var calls = 0
        var body = found
        val finder = newFinder(fake { calls++; 200 to body })
        assertEquals(mp4, runBlocking { finder.find(id) })
        assertEquals(mp4, runBlocking { finder.find(id) })
        assertEquals(1, calls)

        body = """{"canvasesList":[]}"""
        val other = "0VjIjW4GlUZAMYd2vXMi3b"
        assertNull(runBlocking { finder.find(other) })
        assertNull(runBlocking { finder.find(other) })
        assertEquals(2, calls)
    }

    @Test
    fun `a miss is asked again once it is old`() {
        var calls = 0
        var clock = 0L
        val finder = newFinder(fake { calls++; 200 to """{"canvasesList":[]}""" }, now = { clock })
        runBlocking { finder.find(id) }
        clock += SpotifyCanvasFinder.MISS_TTL_MS - 1
        runBlocking { finder.find(id) }
        assertEquals(1, calls)
        clock += 1
        runBlocking { finder.find(id) }
        assertEquals(2, calls)
    }

    @Test
    fun `a bad request is no canvas and is remembered`() {
        var calls = 0
        val finder = newFinder(fake { calls++; 400 to """{"error":"Missing trackId parameter"}""" })
        assertNull(runBlocking { finder.find(id) })
        assertNull(runBlocking { finder.find(id) })
        assertEquals(1, calls)
    }

    @Test
    fun `server errors and network failures are retried once, not remembered, and pause the lookups a while`() {
        for (failure in listOf<() -> Pair<Int, String>>({ 500 to "oops" }, { 429 to "" }, { throw IOException("offline") }, { 200 to "<html>" })) {
            var calls = 0
            var clock = 0L
            var fail = true
            val finder = newFinder(fake { calls++; if (fail) failure() else 200 to found }, now = { clock })
            assertNull(runBlocking { finder.find(id) })
            assertEquals(2, calls)
            // Paused: not asked again straight away, even for another track.
            assertNull(runBlocking { finder.find("0VjIjW4GlUZAMYd2vXMi3b") })
            assertEquals(2, calls)
            clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
            fail = false
            assertEquals(mp4, runBlocking { finder.find(id) })
            assertEquals(3, calls)
        }
    }

    @Test
    fun `a blip is ridden out by the retry`() {
        var calls = 0
        val finder = newFinder(fake { calls++; if (calls == 1) 500 to "oops" else 200 to found })
        assertEquals(mp4, runBlocking { finder.find(id) })
        assertEquals(2, calls)
    }

    @Test
    fun `the pause doubles with each failure in a row and resets on success`() {
        var clock = 0L
        var fail = true
        var calls = 0
        val finder = newFinder(fake { calls++; if (fail) 500 to "oops" else 200 to """{"canvasesList":[]}""" }, now = { clock })
        val other = "0VjIjW4GlUZAMYd2vXMi3b"
        runBlocking { finder.find(id) }
        clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
        runBlocking { finder.find(id) }
        assertEquals(4, calls)
        // Second failure in a row: twice the pause.
        clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
        runBlocking { finder.find(id) }
        assertEquals(4, calls)
        clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
        fail = false
        runBlocking { finder.find(id) }
        assertEquals(5, calls)
        // Back to the short pause after a success.
        fail = true
        runBlocking { finder.find(other) }
        clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
        fail = false
        runBlocking { finder.find(other) }
        assertEquals(8, calls)
    }

    @Test
    fun `the pause stops growing at its cap`() {
        var clock = 0L
        var calls = 0
        val finder = newFinder(fake { calls++; 500 to "oops" }, now = { clock })
        repeat(12) {
            runBlocking { finder.find(id) }
            clock += SpotifyCanvasFinder.MAX_FAILURE_PAUSE_MS
        }
        assertEquals(24, calls)
    }

    @Test
    fun `the own service is asked first and the fallback only when it fails`() {
        val asked = mutableListOf<String>()
        var ownFails = false
        val client = fakeUrl { url ->
            asked += url.substringBefore('?')
            if (ownFails && url.startsWith("https://own.example")) 502 to "{}" else 200 to found
        }
        val finder = newFinder(client, urls = listOf("https://own.example/api/canvas", SpotifyCanvasFinder.FALLBACK_URL))
        assertEquals(mp4, runBlocking { finder.find(id) })
        assertEquals(listOf("https://own.example/api/canvas"), asked)
        ownFails = true
        runBlocking { finder.forget(id) }
        assertEquals(mp4, runBlocking { finder.find(id) })
        assertEquals(listOf("https://own.example/api/canvas", "https://own.example/api/canvas", SpotifyCanvasFinder.FALLBACK_URL), asked)
    }

    @Test
    fun `the memo survives on disk and a forgotten canvas is asked again`() {
        val dir = Files.createTempDirectory("canvas").toFile()
        try {
            val file = File(dir, "canvas/known.json")
            var calls = 0
            val client = fake { calls++; 200 to found }
            assertEquals(mp4, runBlocking { newFinder(client, memoFile = file).find(id) })
            val reopened = newFinder(client, memoFile = file)
            assertEquals(mp4, runBlocking { reopened.find(id) })
            assertEquals(1, calls)
            runBlocking { reopened.forget(id) }
            assertEquals(mp4, runBlocking { reopened.find(id) })
            assertEquals(2, calls)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the track ID goes in the query`() {
        var asked: String? = null
        val finder = newFinder(fakeUrl { asked = it; 200 to found })
        runBlocking { finder.find(id) }
        assertEquals("${SpotifyCanvasFinder.FALLBACK_URL}?trackId=$id", asked)
    }

    @Test
    fun `live lookup finds a known canvas`() {
        assumeTrue(System.getenv("RUN_SPOTIFY_CANVAS_TEST") == "1")
        val finder = SpotifyCanvasFinder(OkHttpClient(), memoFile = null)
        // Harry Styles, "As It Was".
        val url = runBlocking { finder.find(id) }
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://") && url.endsWith(".mp4"))
    }

    /** A finder asking only [urls], with no wait before its retry. */
    private fun newFinder(
        client: OkHttpClient,
        memoFile: File? = null,
        now: () -> Long = System::currentTimeMillis,
        urls: List<String> = listOf(SpotifyCanvasFinder.FALLBACK_URL),
    ) = SpotifyCanvasFinder(client, memoFile, now, urls, retryDelayMs = 0)

    /** A client whose every call is answered by [answer] (status, body), given the URL asked. */
    private fun fakeUrl(answer: (String) -> Pair<Int, String>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val (code, body) = answer(chain.request().url.toString())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun fake(answer: () -> Pair<Int, String>): OkHttpClient = fakeUrl { answer() }
}
