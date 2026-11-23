package com.hhst.youtubelite.core.update

import com.hhst.youtubelite.ui.about.StableVersion
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Which GitHub release stream an install follows. */
enum class UpdateChannel {
    Phone, Tv;

    companion object {
        /** The only place that decides the channel: the TV build differs by an applicationId segment `tv`. */
        fun forPackage(applicationId: String): UpdateChannel =
            if (applicationId.split('.').contains("tv")) Tv else Phone

        fun current(): UpdateChannel = forPackage(com.hhst.youtubelite.BuildConfig.APPLICATION_ID)
    }
}

data class AppRelease(
    val tag: String,
    /** Version name without tag prefixes, e.g. `1.0.8`. */
    val version: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sha256Url: String,
    val notes: String,
    val pageUrl: String,
    /** GitHub `published_at` (ISO-8601), for the changelog list; blank when unknown. */
    val publishedAt: String = "",
)

/** Typed update failures; the UI maps [kind] to a message. */
class UpdateException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { Http, MissingChecksum, ChecksumMismatch, Empty }
}

@Serializable
internal data class GhAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = "",
)

@Serializable
internal data class GhRelease(
    @SerialName("tag_name") val tag: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    @SerialName("html_url") val pageUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/** Finds, downloads and verifies the newest MinTube APK on GitHub Releases. Install is [ApkInstaller]'s job. */
class AppUpdater(
    private val http: OkHttpClient,
    cacheDir: File,
    private val apiBase: String = "https://api.github.com/repos/nev-minhnh/MinTube",
) {
    private val updatesDir = File(cacheDir, "updates")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(channel: UpdateChannel): AppRelease? = withContext(Dispatchers.IO) {
        val releases = when (channel) {
            UpdateChannel.Phone -> listOf(json.decodeFromString<GhRelease>(get("$apiBase/releases/latest")))
            UpdateChannel.Tv -> json.decodeFromString<List<GhRelease>>(get("$apiBase/releases?per_page=30"))
        }
        select(channel, releases)
    }

    /** Newest-first stable releases of [channel], for the changelog list. Releases without an APK are kept (blank apk fields). */
    suspend fun history(channel: UpdateChannel, limit: Int): List<AppRelease> = withContext(Dispatchers.IO) {
        val releases = json.decodeFromString<List<GhRelease>>(get("$apiBase/releases?per_page=30"))
        historyOf(channel, releases, limit)
    }

    fun isNewer(current: String, release: AppRelease): Boolean = StableVersion.isNewer(current, release.version)

    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val expected = parseSha256(get(release.sha256Url, "text/plain"))
            ?: throw UpdateException(UpdateException.Kind.MissingChecksum, "Checksum file is unreadable")
        updatesDir.deleteRecursively()
        check(updatesDir.mkdirs() || updatesDir.isDirectory) { "Cannot create ${updatesDir.path}" }
        val target = File(updatesDir, release.apkName)
        val call = http.newCall(Request.Builder().url(release.apkUrl).build())
        val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            call.execute().use { response ->
                if (!response.isSuccessful) throw UpdateException(UpdateException.Kind.Http, "HTTP ${response.code}")
                val body = response.body ?: throw UpdateException(UpdateException.Kind.Empty, "Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                var read = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw UpdateException(UpdateException.Kind.ChecksumMismatch, "SHA-256 mismatch")
            onProgress(1f)
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            handle.dispose()
        }
    }

    private fun get(url: String, accept: String = "application/vnd.github+json"): String {
        val request = Request.Builder().url(url).header("Accept", accept).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException(UpdateException.Kind.Http, "HTTP ${response.code}")
            return response.body?.string() ?: throw UpdateException(UpdateException.Kind.Empty, "Empty response")
        }
    }

    companion object {
        private val phoneApk = Regex("^MinTube-v[0-9][^/]*\\.apk$")
        private val tvApk = Regex("^MinTube-TV-v[0-9][^/]*\\.apk$")

        /** First 64-hex token of a `.sha256` file (`<hex>  <name>`), lower-cased, or null. */
        fun parseSha256(text: String): String? =
            Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])").find(text)?.value?.lowercase()

        /** Version name from a tag: `v1.0.8` and `tv-v1.0.3` give `1.0.8` and `1.0.3`. */
        fun versionOf(tag: String): String = tag.removePrefix("tv-").removePrefix("v")

        private fun versionKey(tag: String): List<Int> =
            Regex("\\d+").findAll(versionOf(tag)).map { it.value.toIntOrNull() ?: 0 }.take(3).toList()

        private fun compareKeys(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }

        private fun candidates(channel: UpdateChannel, releases: List<GhRelease>) =
            releases.filter { !it.draft && !it.prerelease }.filter {
                when (channel) {
                    UpdateChannel.Phone -> it.tag.startsWith("v")
                    UpdateChannel.Tv -> it.tag.startsWith("tv-v")
                }
            }.sortedWith { a, b -> compareKeys(versionKey(b.tag), versionKey(a.tag)) }

        private fun toRelease(channel: UpdateChannel, r: GhRelease): AppRelease? {
            val pattern = if (channel == UpdateChannel.Tv) tvApk else phoneApk
            val apk = r.assets.firstOrNull { pattern.matches(it.name) } ?: return null
            val sha = r.assets.firstOrNull { it.name == apk.name + ".sha256" } ?: return null
            return AppRelease(r.tag, versionOf(r.tag), apk.url, apk.name, apk.size, sha.url, r.body.orEmpty(), r.pageUrl,
                r.publishedAt.orEmpty())
        }

        internal fun historyOf(channel: UpdateChannel, releases: List<GhRelease>, limit: Int): List<AppRelease> =
            candidates(channel, releases).take(limit.coerceAtLeast(0)).map { r ->
                toRelease(channel, r) ?: AppRelease(r.tag, versionOf(r.tag), "", "", 0, "", r.body.orEmpty(), r.pageUrl,
                    r.publishedAt.orEmpty())
            }

        internal fun select(channel: UpdateChannel, releases: List<GhRelease>): AppRelease? =
            candidates(channel, releases).firstNotNullOfOrNull { toRelease(channel, it) }

        /** Release-note markdown reduced to readable plain text. */
        fun plainNotes(markdown: String): String = markdown.lines().joinToString("\n") { line ->
            line.replace(Regex("^\\s{0,3}#{1,6}\\s*"), "")
                .replace(Regex("^(\\s*)[*+-]\\s+"), "$1• ")
                .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("(\\*\\*|__|`)"), "")
        }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
