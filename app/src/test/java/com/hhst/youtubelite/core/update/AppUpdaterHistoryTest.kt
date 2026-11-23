package com.hhst.youtubelite.core.update

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdaterHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun asset(name: String) = """{"name":"$name","size":10,"browser_download_url":"https://dl/$name"}"""
    private fun release(tag: String, published: String?, pre: Boolean = false, draft: Boolean = false, apk: String? = null): String {
        val assets = apk?.let { listOf(asset(it), asset("$it.sha256")) }.orEmpty().joinToString(",")
        val at = published?.let { ""","published_at":"$it"""" }.orEmpty()
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":"notes $tag","html_url":"https://p/$tag"$at,"assets":[$assets]}"""
    }

    private fun updater(json: String) = AppUpdater(OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }).build(), tmp.root, "https://api")

    private val feed = "[" + listOf(
        release("v1.0.8", "2026-11-18T10:00:00Z", apk = "MinTube-v1.0.8.apk"),
        release("tv-v1.0.6", "2026-11-21T10:00:00Z", apk = "MinTube-TV-v1.0.6.apk"),
        release("v1.0.9", "2026-11-22T10:00:00Z", apk = "MinTube-v1.0.9.apk"),
        release("v1.0.10-beta", "2026-11-23T10:00:00Z", pre = true, apk = "MinTube-v1.0.10-beta.apk"),
        release("v1.0.7", null, draft = true),
        release("v1.0.6", "2026-11-10T10:00:00Z"),
        release("tv-v1.0.5", "2026-11-12T10:00:00Z", apk = "MinTube-TV-v1.0.5.apk"),
    ).joinToString(",") + "]"

    @Test fun phoneHistoryIsNewestFirstStableOnlyWithPublishedAt() = runBlocking {
        val list = updater(feed).history(UpdateChannel.Phone, 10)
        assertEquals(listOf("1.0.9", "1.0.8", "1.0.6"), list.map { it.version })
        assertEquals("2026-11-22T10:00:00Z", list[0].publishedAt)
        assertEquals("https://dl/MinTube-v1.0.9.apk", list[0].apkUrl)
        assertEquals("", list[2].apkUrl)
    }

    @Test fun tvHistoryOnlyHasTvTags() = runBlocking {
        val list = updater(feed).history(UpdateChannel.Tv, 10)
        assertEquals(listOf("1.0.6", "1.0.5"), list.map { it.version })
        assertEquals("2026-11-21T10:00:00Z", list[0].publishedAt)
    }

    @Test fun historyHonoursTheLimit() = runBlocking {
        assertEquals(listOf("1.0.9"), updater(feed).history(UpdateChannel.Phone, 1).map { it.version })
    }

    @Test fun latestCarriesPublishedAt() {
        val releases = Json { ignoreUnknownKeys = true }.decodeFromString<List<GhRelease>>(feed)
        assertEquals("2026-11-22T10:00:00Z", AppUpdater.select(UpdateChannel.Phone, releases)?.publishedAt)
    }
}
