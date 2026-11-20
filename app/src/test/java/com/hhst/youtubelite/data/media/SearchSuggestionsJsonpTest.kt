package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.HttpClient
import com.liskovsoft.youtubeapi.videoinfo.V2.HttpReply
import com.liskovsoft.youtubeapi.videoinfo.V2.HttpRequest
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext
import com.liskovsoft.youtubeapi.videoinfo.V2.loadSearchSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA D1: the suggestion endpoint answers JSONP; remote rows must survive the library parser. */
class SearchSuggestionsJsonpTest {
    private val jsonp = "window.google.ac.h([\"how to\",[[\"how to tie a tie\",0,[512,433]]," +
        "[\"how to draw\",0,[512,433]]],{\"k\":1,\"q\":\"x\"}])"

    private fun replying(body: String) = object : HttpClient {
        override fun execute(request: HttpRequest) = HttpReply(200, body, emptyMap())
    }

    @Test fun libraryAloneRejectsTheJsonpBody() {
        // Documents the root cause: without the unwrap the read fails and degrades to no rows.
        assertTrue(loadSearchSuggestions("how to", RequestContext(), replying(jsonp)).value.isNullOrEmpty())
    }

    @Test fun unwrappedJsonpYieldsServerSuggestions() {
        val rows = loadSearchSuggestions("how to", RequestContext(), JsonpUnwrappingClient(replying(jsonp))).value
        assertEquals(listOf("how to tie a tie", "how to draw"), rows)
    }

    @Test fun plainJsonPassesThroughUnchanged() {
        val json = "[\"how to\",[\"how to tie a tie\"],[],{}]"
        assertEquals(json, unwrapJsonp(json))
        assertEquals(listOf("how to tie a tie"),
            loadSearchSuggestions("how to", RequestContext(), JsonpUnwrappingClient(replying(json))).value)
    }

    @Test fun requestAsksForUtf8OutputOnce() {
        val urls = mutableListOf<String>()
        val client = JsonpUnwrappingClient(object : HttpClient {
            override fun execute(request: HttpRequest): HttpReply { urls += request.url; return HttpReply(200, jsonp, emptyMap()) }
        })
        loadSearchSuggestions("tây du", RequestContext(), client)
        assertTrue(urls.single(), urls.single().startsWith("https://suggestqueries.google.com/complete/search?"))
        assertTrue(urls.single(), urls.single().endsWith("&oe=utf-8"))
        assertEquals("https://x/s?a=1&oe=latin1", withUtf8Output("https://x/s?a=1&oe=latin1"))
        assertEquals("https://x/s?oe=utf-8", withUtf8Output("https://x/s"))
    }

    @Test fun nonJsonpTextIsLeftAlone() {
        assertEquals("<html>error</html>", unwrapJsonp("<html>error</html>"))
        assertEquals("(oops)", unwrapJsonp("(oops)"))
    }
}
