package com.lean.tv.parser

const val DEFAULT_GROUP = "未分组"

data class ParsedChannel(
    val name: String,
    val group: String,
    val logoUrl: String,
    val url: String
)

interface PlaylistParser {
    fun parse(content: String): List<ParsedChannel>
}
