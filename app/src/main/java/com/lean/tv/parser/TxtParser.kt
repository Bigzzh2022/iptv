package com.lean.tv.parser

import java.io.BufferedReader

/**
 * TXT 直播源解析：
 *
 * 央视频道,#genre#
 * CCTV1,http://a.m3u8
 * CCTV2,http://b.m3u8#http://b2.m3u8
 */
object TxtParser : PlaylistParser {

    private const val GENRE = "#genre#"

    override fun parse(reader: BufferedReader): List<ParsedChannel> {
        val channels = ArrayList<ParsedChannel>(256)
        var group = DEFAULT_GROUP

        fun parseLine(raw: String) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return

            // 分组行格式为「分组名,#genre#」，分组名在前，不能按 # 开头判断
            if (line.contains(GENRE)) {
                val name = line.substringBefore(',').removeSuffix(GENRE).trim()
                group = name.ifBlank { DEFAULT_GROUP }
                return
            }
            if (line.startsWith("#")) return

            val comma = line.indexOf(',')
            if (comma <= 0) return

            val name = line.substring(0, comma).trim()
            val urls = line.substring(comma + 1).trim()
            if (name.isEmpty() || urls.isEmpty()) return

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

        var raw = reader.readLine()
        while (raw != null) {
            parseLine(raw)
            raw = reader.readLine()
        }
        return channels
    }
}
