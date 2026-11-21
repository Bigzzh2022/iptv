package com.lean.tv.server

import android.app.ActivityManager
import android.content.Context
import com.lean.tv.App
import com.lean.tv.BuildConfig
import com.lean.tv.data.AspectMode
import com.lean.tv.data.BufferProfile
import com.lean.tv.data.ConfigStore
import com.lean.tv.data.DecoderMode
import com.lean.tv.data.SourceInput
import com.lean.tv.util.NetUtils
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 极简 REST 路由。运行在 NanoHTTPD 的工作线程上，
 * 因此允许用 runBlocking 等待 Room 查询。
 */
class ApiRouter(private val context: Context) {

    fun handle(session: IHTTPSession): HttpResult {
        val uri = session.uri ?: "/"
        val method = session.method

        if (method == Method.OPTIONS) {
            return HttpResult(Response.Status.OK, MIME_TEXT, "")
        }

        return when {
            method == Method.GET && (uri == "/" || uri == "/index.html") -> serveIndex()
            method == Method.GET && uri == "/api/config" -> getConfig()
            method == Method.GET && uri == "/api/status" -> getStatus()
            method == Method.POST && uri == "/api/config" -> postConfig(session)
            method == Method.POST && uri == "/api/source/refresh" -> postRefresh(session)
            method == Method.POST && uri == "/api/source/refresh-all" -> postRefreshAll()
            else -> error(Response.Status.NOT_FOUND, "接口不存在")
        }
    }

    private fun serveIndex(): HttpResult {
        return try {
            val html = context.assets.open("index.html")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            HttpResult(Response.Status.OK, MIME_HTML, html)
        } catch (e: Exception) {
            error(Response.Status.NOT_FOUND, "index.html 未找到")
        }
    }

    private fun getConfig(): HttpResult {
        val config = runBlocking {
            val sources = App.repository.sources()
            JSONObject().apply {
                put("port", ConfigStore.httpPort)
                put("epgUrl", ConfigStore.epgUrl)
                put("decoder", ConfigStore.decoderMode)
                put("userAgent", ConfigStore.userAgent)
                put("bufferProfile", ConfigStore.bufferProfile)
                put("aspectMode", ConfigStore.aspectMode)
                put("sources", JSONArray().apply {
                    sources.forEach { source ->
                        put(JSONObject().apply {
                            put("id", source.id)
                            put("name", source.name)
                            put("url", source.url)
                            put("type", source.type)
                            put("lastUpdate", source.lastUpdate)
                        })
                    }
                })
            }
        }
        return ok(config)
    }

    private fun getStatus(): HttpResult {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / MB

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val counts = runBlocking {
            App.repository.channelCount() to App.repository.sourceCount()
        }

        val data = JSONObject().apply {
            put("ip", NetUtils.localIpv4())
            put("port", ConfigStore.httpPort)
            put("usedMb", usedMb)
            put("availMb", memoryInfo.availMem / MB)
            put("totalMb", memoryInfo.totalMem / MB)
            put("channelCount", counts.first)
            put("sourceCount", counts.second)
            put("version", BuildConfig.VERSION_NAME)
        }
        return ok(data)
    }

    private fun postConfig(session: IHTTPSession): HttpResult {
        val raw = readBody(session)
        if (raw.isBlank()) return error(Response.Status.BAD_REQUEST, "请求体为空")

        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return error(Response.Status.BAD_REQUEST, "JSON 格式错误")
        }

        if (json.has("epgUrl")) {
            ConfigStore.epgUrl = json.optString("epgUrl").trim()
        }
        if (json.has("decoder")) {
            val decoder = json.optString("decoder").trim()
            if (DecoderMode.isValid(decoder)) {
                ConfigStore.decoderMode = decoder
            }
        }
        if (json.has("userAgent")) {
            ConfigStore.userAgent = json.optString("userAgent")
        }
        if (json.has("bufferProfile")) {
            val profile = json.optString("bufferProfile").trim()
            if (BufferProfile.isValid(profile)) ConfigStore.bufferProfile = profile
        }
        if (json.has("aspectMode")) {
            val aspect = json.optString("aspectMode").trim()
            if (AspectMode.isValid(aspect)) ConfigStore.aspectMode = aspect
        }
        if (json.has("sources")) {
            val inputs = parseSources(json.optJSONArray("sources") ?: JSONArray())
            val refreshIds = runBlocking { App.repository.syncSources(inputs) }
            refreshIds.forEach { id ->
                App.appScope.launch {
                    App.repository.source(id)?.let { App.repository.refreshSource(it) }
                }
            }
        }

        return ok(JSONObject().apply { put("saved", true) })
    }

    private fun postRefresh(session: IHTTPSession): HttpResult {
        val raw = readBody(session)
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return error(Response.Status.BAD_REQUEST, "JSON 格式错误")
        }

        val id = json.optLong("id", 0L)
        if (id <= 0L) return error(Response.Status.BAD_REQUEST, "缺少订阅源 id")

        val source = runBlocking { App.repository.source(id) }
            ?: return error(Response.Status.NOT_FOUND, "订阅源不存在")

        App.appScope.launch {
            App.repository.refreshSource(source)
        }
        return ok(JSONObject().apply { put("started", true) })
    }

    /** 依次刷新所有订阅源，避免同时下载拖垮老电视。 */
    private fun postRefreshAll(): HttpResult {
        val count = runBlocking { App.repository.sourceCount() }
        if (count > 0) {
            App.appScope.launch {
                App.repository.sources().forEach { App.repository.refreshSource(it) }
            }
        }
        return ok(JSONObject().apply {
            put("started", true)
            put("count", count)
        })
    }

    private fun parseSources(array: JSONArray): List<SourceInput> {
        val inputs = ArrayList<SourceInput>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue

            val type = item.optString("type", TYPE_AUTO).trim().uppercase()
            inputs.add(
                SourceInput(
                    id = item.optLong("id", 0L),
                    name = item.optString("name").trim()
                        .ifBlank { "订阅源 ${inputs.size + 1}" },
                    url = url,
                    type = if (type == TYPE_M3U || type == TYPE_TXT) type else TYPE_AUTO
                )
            )
        }
        return inputs
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private fun ok(data: JSONObject): HttpResult =
        HttpResult(
            Response.Status.OK,
            MIME_JSON,
            JSONObject().apply {
                put("ok", true)
                put("data", data)
            }.toString()
        )

    private fun error(status: Response.Status, message: String): HttpResult =
        HttpResult(
            status,
            MIME_JSON,
            JSONObject().apply {
                put("ok", false)
                put("error", message)
            }.toString()
        )

    private companion object {
        const val MIME_JSON = "application/json; charset=utf-8"
        const val MIME_HTML = "text/html; charset=utf-8"
        const val MIME_TEXT = "text/plain; charset=utf-8"
        const val MB = 1024L * 1024L
        const val TYPE_AUTO = "AUTO"
        const val TYPE_M3U = "M3U"
        const val TYPE_TXT = "TXT"
    }
}
