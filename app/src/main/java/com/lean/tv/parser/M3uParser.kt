package com.lean.tv.parser

import java.io.BufferedReader

/**
 * M3U / M3U8 直播源解析。
 * 逐行状态机读取，不把整个文件载入内存。
 */
object M3uParser : PlaylistParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9-]+)="([^"]*)"""")

    override fun parse(reader: BufferedReader): List<ParsedChannel> {
        val channels = ArrayList<ParsedChannel>(256)
        var pending: ParsedChannel? = null

        var raw = reader.readLine()
        while (raw != null) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }

                line.startsWith("#") -> Unit

                pending != null -> {
                    channels.add(pending.copy(url = line))
                    pending = null
                }

                line.startsWith("http", ignoreCase = true) -> {
                    channels.add(
                        ParsedChannel(
                            name = nameFromUrl(line),
                            group = DEFAULT_GROUP,
                            logoUrl = "",
                            url = line
                        )
                    )
                }
            }
            raw = reader.readLine()
        }
        return channels
    }

    private fun parseExtInf(line: String): ParsedChannel {
        val attrs = HashMap<String, String>(4)
        ATTR_REGEX.findAll(line).forEach { match ->
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }

        val comma = line.lastIndexOf(',')
        val displayName = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val name: String = attrs["tvg-name"].takeUnless { it.isNullOrBlank() } ?: displayName

        return ParsedChannel(
            name = name.ifBlank { "未命名频道" },
            group = attrs["group-title"].orEmpty().ifBlank { DEFAULT_GROUP },
            logoUrl = attrs["tvg-logo"].orEmpty(),
            url = ""
        )
    }

    private fun nameFromUrl(url: String): String {
        val path = url.substringBefore('?')
        return path.substringAfterLast('/').ifBlank { "未命名频道" }
    }
}
