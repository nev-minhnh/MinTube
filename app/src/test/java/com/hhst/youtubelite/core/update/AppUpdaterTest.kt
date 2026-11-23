package com.hhst.youtubelite.core.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun asset(name: String) = """{"name":"$name","size":1000,"browser_download_url":"https://dl/$name"}"""
    private fun release(tag: String, assets: List<String>, prerelease: Boolean = false, draft: Boolean = false) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":"## New\n- **fast** thing","html_url":"https://page/$tag","assets":[${assets.joinToString(",") { asset(it) }}]}"""
    private fun phone(v: String) = release("v$v", listOf("MinTube-v$v.apk", "MinTube-v$v.apk.sha256"))
    private fun tv(v: String, pre: Boolean = false) =
        release("tv-v$v", listOf("MinTube-TV-v$v.apk", "MinTube-TV-v$v.apk.sha256"), prerelease = pre)

    private fun fake(routes: Map<String, ByteArray>, seen: MutableList<String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString(); seen += url
            val body = routes[url]
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (body == null) 404 else 200).message("x")
                .body((body ?: ByteArray(0)).toResponseBody("application/octet-stream".toMediaType())).build()
        }).build()

    private fun updater(routes: Map<String, ByteArray>, seen: MutableList<String> = mutableListOf()) =
        AppUpdater(fake(routes, seen), tmp.root, "https://api")

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun select(channel: UpdateChannel, tag: String, vararg assets: String) =
        AppUpdater.select(channel, listOf(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<GhRelease>(release(tag, assets.toList()))))

    @Test fun phoneUsesLatestAndMatchesApkAndSha() = runBlocking {
        val seen = mutableListOf<String>()
        val r = updater(mapOf("https://api/releases/latest" to phone("1.0.8").toByteArray()), seen).latest(UpdateChannel.Phone)!!
        assertEquals(listOf("https://api/releases/latest"), seen)
        assertEquals("1.0.8", r.version); assertEquals("MinTube-v1.0.8.apk", r.apkName)
        assertEquals("https://dl/MinTube-v1.0.8.apk.sha256", r.sha256Url); assertEquals("https://page/v1.0.8", r.pageUrl)
    }

    @Test fun tvPicksHighestNonPrereleaseTvTag() = runBlocking {
        val list = "[" + listOf(phone("1.0.9"), tv("1.0.2"), tv("1.0.10", pre = true),
            release("tv-v1.0.4", listOf("MinTube-TV-v1.0.4.apk", "MinTube-TV-v1.0.4.apk.sha256"), draft = true),
            tv("1.0.3")).joinToString(",") + "]"
        val seen = mutableListOf<String>()
        val r = updater(mapOf("https://api/releases?per_page=30" to list.toByteArray()), seen).latest(UpdateChannel.Tv)!!
        assertEquals("1.0.3", r.version); assertEquals("tv-v1.0.3", r.tag); assertEquals("MinTube-TV-v1.0.3.apk", r.apkName)
    }

    @Test fun assetMatchingIsPerChannelAndNeedsApkAndSha() {
        assertNull(select(UpdateChannel.Phone, "v1.0.3", "MinTube-TV-v1.0.3.apk", "MinTube-TV-v1.0.3.apk.sha256"))
        assertNull(select(UpdateChannel.Tv, "tv-v1.0.3", "MinTube-v1.0.3.apk", "MinTube-v1.0.3.apk.sha256"))
        assertNull(select(UpdateChannel.Phone, "v1.0.3", "MinTube-v1.0.3.apk"))
        assertNull(select(UpdateChannel.Phone, "v1.0.3", "MinTube-v1.0.3.apk.sha256"))
        assertNotNull(select(UpdateChannel.Phone, "v1.0.3", "notes.txt", "MinTube-v1.0.3.apk", "MinTube-v1.0.3.apk.sha256"))
    }

    @Test fun phoneIgnoresPrereleaseLatest() = runBlocking {
        val body = release("v1.0.8", listOf("MinTube-v1.0.8.apk", "MinTube-v1.0.8.apk.sha256"), prerelease = true)
        assertNull(updater(mapOf("https://api/releases/latest" to body.toByteArray())).latest(UpdateChannel.Phone))
    }

    @Test fun isNewerUsesStableVersion() {
        val r = AppRelease("tv-v1.0.3", "1.0.3", "", "", 0, "", "", "")
        val u = updater(emptyMap())
        assertTrue(u.isNewer("1.0.2", r)); assertFalse(u.isNewer("1.0.3", r)); assertFalse(u.isNewer("1.0.4", r))
    }

    @Test fun parsesShaFile() {
        assertEquals("a".repeat(64), AppUpdater.parseSha256("${"A".repeat(64)}  MinTube-v1.apk\n"))
        assertNull(AppUpdater.parseSha256("deadbeef  x"))
        assertNull(AppUpdater.parseSha256(""))
    }

    @Test fun channelDetection() {
        assertEquals(UpdateChannel.Phone, UpdateChannel.forPackage("com.nevminhnh.mintube"))
        assertEquals(UpdateChannel.Phone, UpdateChannel.forPackage("com.nevminhnh.mintube.debug"))
        assertEquals(UpdateChannel.Tv, UpdateChannel.forPackage("com.nevminhnh.mintube.tv"))
        assertEquals(UpdateChannel.Tv, UpdateChannel.forPackage("com.nevminhnh.mintube.tv.debug"))
    }

    @Test fun plainNotesStripsMarkdown() {
        assertEquals("New\n• fast thing and link", AppUpdater.plainNotes("## New\n- **fast** thing and [link](http://x)"))
    }

    private fun rel() = AppRelease("v1.0.8", "1.0.8", "https://dl/a.apk", "a.apk", 4, "https://dl/a.sha256", "", "")

    @Test fun downloadStreamsVerifiesAndReportsProgress() = runBlocking {
        val apk = ByteArray(300_000) { it.toByte() }
        val stale = File(tmp.root, "updates/old.apk").apply { parentFile.mkdirs(); writeText("old") }
        val progress = mutableListOf<Float>()
        val file = updater(mapOf("https://dl/a.apk" to apk, "https://dl/a.sha256" to "${sha(apk)}  a.apk\n".toByteArray()))
            .download(rel()) { progress += it }
        assertEquals(File(tmp.root, "updates/a.apk"), file); assertArrayEquals(apk, file.readBytes())
        assertFalse(stale.exists()); assertEquals(1f, progress.last(), 0f)
        assertTrue(progress.zipWithNext().all { it.first <= it.second })
    }

    @Test fun mismatchDeletesFileAndThrows() {
        val apk = ByteArray(1000) { 1 }
        val u = updater(mapOf("https://dl/a.apk" to apk, "https://dl/a.sha256" to "${"0".repeat(64)}  a.apk".toByteArray()))
        val e = assertThrows(UpdateException::class.java) { runBlocking { u.download(rel()) {} } }
        assertEquals(UpdateException.Kind.ChecksumMismatch, e.kind)
        assertFalse(File(tmp.root, "updates/a.apk").exists())
    }

    @Test fun missingShaFailsWithoutDownloading() {
        val seen = mutableListOf<String>()
        val u = updater(mapOf("https://dl/a.apk" to ByteArray(10)), seen)
        assertThrows(UpdateException::class.java) { runBlocking { u.download(rel()) {} } }
        assertFalse(seen.contains("https://dl/a.apk")); assertFalse(File(tmp.root, "updates/a.apk").exists())
        val bad = updater(mapOf("https://dl/a.apk" to ByteArray(10), "https://dl/a.sha256" to "garbage".toByteArray()))
        assertEquals(UpdateException.Kind.MissingChecksum,
            assertThrows(UpdateException::class.java) { runBlocking { bad.download(rel()) {} } }.kind)
    }
}
