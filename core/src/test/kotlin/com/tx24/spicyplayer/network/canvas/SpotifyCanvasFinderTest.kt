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
        val finder = SpotifyCanvasFinder(fake { calls++; 200 to found }, memoFile = null)
        assertNull(runBlocking { finder.find("not-an-id") })
        assertNull(runBlocking { finder.find("") })
        assertEquals(0, calls)
    }

    @Test
    fun `answers are remembered per track, misses included`() {
        var calls = 0
        var body = found
        val finder = SpotifyCanvasFinder(fake { calls++; 200 to body }, memoFile = null)
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
        val finder = SpotifyCanvasFinder(fake { calls++; 200 to """{"canvasesList":[]}""" }, memoFile = null, now = { clock })
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
        val finder = SpotifyCanvasFinder(fake { calls++; 400 to """{"error":"Missing trackId parameter"}""" }, memoFile = null)
        assertNull(runBlocking { finder.find(id) })
        assertNull(runBlocking { finder.find(id) })
        assertEquals(1, calls)
    }

    @Test
    fun `server errors and network failures are not remembered, and pause the lookups a while`() {
        for (failure in listOf<() -> Pair<Int, String>>({ 500 to "oops" }, { 429 to "" }, { throw IOException("offline") }, { 200 to "<html>" })) {
            var calls = 0
            var clock = 0L
            var fail = true
            val finder = SpotifyCanvasFinder(
                fake { calls++; if (fail) failure() else 200 to found },
                memoFile = null,
                now = { clock },
            )
            assertNull(runBlocking { finder.find(id) })
            // Paused: not asked again straight away, even for another track.
            assertNull(runBlocking { finder.find("0VjIjW4GlUZAMYd2vXMi3b") })
            assertEquals(1, calls)
            clock += SpotifyCanvasFinder.FAILURE_PAUSE_MS
            fail = false
            assertEquals(mp4, runBlocking { finder.find(id) })
            assertEquals(2, calls)
        }
    }

    @Test
    fun `the memo survives on disk and a forgotten canvas is asked again`() {
        val dir = Files.createTempDirectory("canvas").toFile()
        try {
            val file = File(dir, "canvas/known.json")
            var calls = 0
            val client = fake { calls++; 200 to found }
            assertEquals(mp4, runBlocking { SpotifyCanvasFinder(client, file).find(id) })
            val reopened = SpotifyCanvasFinder(client, file)
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
        val finder = SpotifyCanvasFinder(fakeUrl { asked = it; 200 to found }, memoFile = null)
        runBlocking { finder.find(id) }
        assertEquals("${SpotifyCanvasFinder.BASE_URL}?trackId=$id", asked)
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
