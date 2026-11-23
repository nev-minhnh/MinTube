package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.BrowseTab
import java.net.URLDecoder
import java.util.Base64

/**
 * Channel tabs identified by their browse params, which do not depend on `hl`; tab titles
 * are localized ("Videos" is "Video" in Vietnamese). WEB params carry the tab's URL slug
 * (field 2); TV params select the tab by a field number under 110.2. The English title is
 * the fallback for params this decoder does not recognize.
 */
internal enum class ChannelTabKind(private val slugs: Set<String>, private val tvField: Int?, private val titles: Set<String>) {
    Videos(setOf("videos"), 13, setOf("Videos")),
    Shorts(setOf("shorts"), 14, setOf("Shorts")),
    Live(setOf("streams"), 9, setOf("Live")),
    Playlists(setOf("playlists"), 12, setOf("Playlists")),
    Posts(setOf("posts", "community"), null, setOf("Posts", "Community"));

    fun matches(tab: BrowseTab): Boolean =
        of(tab.params)?.let { it == this } ?: titles.any { it.equals(tab.title, ignoreCase = true) }

    companion object {
        /** The tab kind encoded in [params]; null when absent or not a recognized shape. */
        fun of(params: String?): ChannelTabKind? {
            val bytes = decode(params ?: return null) ?: return null
            val root = fields(bytes) ?: return null
            (root[2] as? ByteArray)?.let { slug ->
                val text = String(slug, Charsets.UTF_8)
                return entries.firstOrNull { text in it.slugs }
            }
            val tv = (root[110] as? ByteArray)?.let(::fields)?.get(2) as? ByteArray ?: return null
            val selector = fields(tv)?.keys ?: return null
            return entries.firstOrNull { it.tvField != null && it.tvField in selector }
        }

        internal fun decode(params: String): ByteArray? = runCatching {
            // Only percent-escapes are decoded: a raw '+' is base64, not a space.
            val raw = (if ('%' in params) URLDecoder.decode(params, "UTF-8") else params).trimEnd('=')
            Base64.getUrlDecoder().decode(raw.replace('+', '-').replace('/', '_'))
        }.getOrNull()

        /** Top-level protobuf fields: length-delimited values as bytes, varints as Long. */
        internal fun fields(bytes: ByteArray): Map<Int, Any>? {
            val out = LinkedHashMap<Int, Any>()
            var index = 0
            fun varint(): Long? {
                var shift = 0
                var value = 0L
                while (index < bytes.size && shift < 64) {
                    val b = bytes[index++].toInt() and 0xff
                    value = value or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return value
                    shift += 7
                }
                return null
            }
            while (index < bytes.size) {
                val tag = varint() ?: return null
                val field = (tag ushr 3).toInt()
                if (field <= 0) return null
                when ((tag and 7).toInt()) {
                    0 -> out.putIfAbsent(field, varint() ?: return null)
                    2 -> {
                        val length = varint()?.takeIf { it >= 0 && index + it <= bytes.size }?.toInt() ?: return null
                        out.putIfAbsent(field, bytes.copyOfRange(index, index + length))
                        index += length
                    }
                    else -> return null
                }
            }
            return out
        }
    }
}

/**
 * Search filter options keep their navigation params across languages while their labels
 * are localized. An option's params hold the filter set it navigates to: field 2 holds
 * upload date (1) and result type (2) codes.
 */
internal object SearchFilterParams {
    const val UPLOAD_DATE = 1
    const val TYPE = 2

    fun value(params: String?, field: Int): Long? {
        val bytes = ChannelTabKind.decode(params ?: return null) ?: return null
        val filters = ChannelTabKind.fields(bytes)?.get(2) as? ByteArray ?: return null
        return ChannelTabKind.fields(filters)?.get(field) as? Long
    }

    /**
     * Search params for an upload-date and/or type code, in YouTube's own layout (field 2 holds the
     * filters: 1 = upload date, 2 = type), e.g. This week alone is "EgIIAw==". Null when both are absent.
     */
    fun build(uploadDate: Long?, type: Long?): String? {
        val inner = buildList {
            uploadDate?.let { add(0x08); add(it.toInt()) }
            type?.let { add(0x10); add(it.toInt()) }
        }
        if (inner.isEmpty()) return null
        val bytes = (listOf(0x12, inner.size) + inner).map(Int::toByte).toByteArray()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}
