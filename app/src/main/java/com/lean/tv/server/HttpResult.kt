package com.lean.tv.server

import fi.iki.elonen.NanoHTTPD.Response

data class HttpResult(
    val status: Response.Status,
    val mime: String,
    val body: String
)
