package com.lean.tv.parser

import java.io.BufferedReader

const val DEFAULT_GROUP = "未分组"

data class ParsedChannel(
    val name: String,
    val group: String,
    val logoUrl: String,
    val url: String
)

interface PlaylistParser {
    /** 逐行读取，调用方负责关闭 reader。 */
    fun parse(reader: BufferedReader): List<ParsedChannel>
}
