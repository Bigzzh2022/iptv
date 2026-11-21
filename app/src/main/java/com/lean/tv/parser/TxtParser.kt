package com.lean.tv.parser

/**
 * TXT 直播源解析：
 *
 * 央视频道,#genre#
 * CCTV1,http://a.m3u8
 * CCTV2,http://b.m3u8#http://b2.m3u8
 */
object TxtParser : PlaylistParser {

    private const val GENRE = "#genre#"

    override fun parse(content: String): List<ParsedChannel> {
        val channels = ArrayList<ParsedChannel>(256)
        var group = DEFAULT_GROUP

        content.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#")) {
                if (line.contains(GENRE)) {
                    group = line.substringBefore(',').trim().ifBlank { DEFAULT_GROUP }
                }
                return@forEach
            }

            val comma = line.indexOf(',')
            if (comma <= 0) return@forEach

            val name = line.substring(0, comma).trim()
            val urls = line.substring(comma + 1).trim()
            if (name.isEmpty() || urls.isEmpty()) return@forEach

            urls.split('#').forEach { part ->
                val url = part.trim()
                if (url.startsWith("http", ignoreCase = true)) {
                    channels.add(
                        ParsedChannel(
                            name = name,
                            group = group,
                            logoUrl = "",
                            url = url
                        )
                    )
                }
            }
        }
        return channels
    }
}
