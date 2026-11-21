package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.search.domain.SearchFilters
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Trending in Vietnam" sent `gl=VN` with the player request too. Over a foreign VPN, a
 * geo-restricted video then failed with googlevideo 403. Only discovery reads may carry it.
 */
class ContentRegionScopeTest {
    private val bodies = mutableListOf<String>()
    private val defaultRegion = RequestContext().region

    private val client = MediaClient(OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        val body = request.body?.let { okio.Buffer().also(it::writeTo).readUtf8() }.orEmpty()
        synchronized(bodies) { bodies += "${request.url} $body" }
        Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
            .body("{}".toResponseBody("application/json".toMediaType())).build()
    }).build(), MediaSession::freshContext)

    @Before fun setUp() {
        MediaSession.initialize(MemoryAuthStore())
        MediaSession.setContentLocale(ContentLocale("vi", "VN", originalText = true))
    }

    @After fun tearDown() = MediaSession.initialize(MemoryAuthStore())

    private var lastFailure: Throwable? = null

    private fun sent(block: suspend () -> Unit): List<String> {
        synchronized(bodies) { bodies.clear() }
        // The canned `{}` replies do not parse into pages; only the outgoing requests matter.
        lastFailure = runBlocking { runCatching { block() }.exceptionOrNull() }
        return synchronized(bodies) { bodies.toList() }
    }

    @Test fun sessionContextsKeepTheLanguageButNeverTheRegionOverride() {
        assertEquals("VN", ContentLocale.resolve(true, true, "", java.util.Locale.US).region)
        for (context in listOf(MediaSession.snapshot(), MediaSession.freshContext(), MediaSession.snapshot().anonymous())) {
            assertEquals("vi", context.language)
            assertEquals(defaultRegion, context.region)
        }
        assertEquals("VN", MediaSession.withContentRegion(MediaSession.snapshot()).region)
    }

    @Test fun onlyDiscoveryAndSearchRequestsCarryTheRegionOverride() {
        val unused = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
        ) { _, method, _ -> error("unexpected preference access: ${method.name}") } as android.content.SharedPreferences
        val preferences = com.hhst.youtubelite.core.PreferenceStore.of(object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int) = unused
        }, "unused-library")
        val repositories = ContentRepositories(client, kv = preferences)
        val search = sent { repositories.search.search("open movie", SearchFilters()) }
        assertTrue("search must send the region: $search", search.isNotEmpty() && search.all { "\"gl\":\"VN\"" in it })

        val watch = sent { repositories.watch.watch("Cks_r_3fkcY") }
        assertTrue("a watch read was expected: $lastFailure", watch.isNotEmpty())
        val shorts = sent { repositories.shorts("Cks_r_3fkcY", null).hydrate("Cks_r_3fkcY") }
        assertTrue("a Shorts read was expected: $lastFailure", shorts.isNotEmpty())
        for (playback in listOf(watch, shorts)) {
            playback.forEach {
                assertFalse("playback reads must not send the region override: $it", "\"gl\":\"VN\"" in it)
                assertTrue("playback reads keep the default region: $it", "\"gl\":\"$defaultRegion\"" in it)
            }
        }
    }
}
