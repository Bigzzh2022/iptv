package com.lean.tv.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class IptvHttpServer(port: Int, context: Context) : NanoHTTPD(port) {

    private val router = ApiRouter(context.applicationContext)

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            val result = router.handle(session)
            newFixedLengthResponse(result.status, result.mime, result.body)
        } catch (t: Throwable) {
            val body = JSONObject().apply {
                put("ok", false)
                put("error", t.message ?: "服务器内部错误")
            }.toString()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json; charset=utf-8",
                body
            )
        }

        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        response.addHeader("Cache-Control", "no-store")
        return response
    }
}
