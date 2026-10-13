package com.tx24.spicyplayer.network.data.providers

import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusSongPagesTest {
    /** A client answering with [codes] in turn (the last one repeats), counting the requests. */
    private class Genius(vararg val codes: Int) {
        var requests = 0
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val code = codes[minOf(requests++, codes.lastIndex)]
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body("""{"response":{"song":{"id":1,"translation_songs":[]}}}""".toResponseBody()).build()
        }.build()
    }

    @Test fun `the romanization and translation lookups share a song's requests`() = runBlocking {
        val genius = Genius(200)
        val pages = GeniusSongPages(genius.client, Gson())
        // Both lookups ask for the same record at once: one request answers both.
        val records = List(2) { async { pages.record(1) } }.awaitAll()
        assertEquals(1, genius.requests)
        assertEquals(records[0], records[1])
        pages.record(1)
        assertEquals(1, genius.requests)
    }

    @Test fun `a failed request isn't remembered and an old answer is asked again`() = runBlocking {
        var now = 0L
        val genius = Genius(429, 200)
        val pages = GeniusSongPages(genius.client, Gson()) { now }
        assertTrue(runCatching { pages.record(1) }.isFailure)
        assertEquals(1L, pages.record(1)?.get("id")?.asLong)
        assertEquals(2, genius.requests)
        now += 11 * 60_000L
        pages.record(1)
        assertEquals(3, genius.requests)
    }

    @Test fun `a Genius outage isn't taken for a song without a romanization`() = runBlocking {
        val limited = GeniusRomanizationSource(GeniusSongPages(Genius(429).client, Gson()))
        assertTrue(runCatching { limited.find("夜に駆ける", "YOASOBI") }.isFailure)
        val empty = GeniusRomanizationSource(GeniusSongPages(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
                .body("""{"response":{"sections":[]}}""".toResponseBody()).build()
        }.build(), Gson()))
        assertNull(empty.find("夜に駆ける", "YOASOBI"))
    }
}
