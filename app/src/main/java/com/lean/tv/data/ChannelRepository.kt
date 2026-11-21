package com.lean.tv.data

import androidx.room.withTransaction
import com.lean.tv.data.entity.ChannelEntity
import com.lean.tv.data.entity.SourceEntity
import com.lean.tv.parser.M3uParser
import com.lean.tv.parser.ParsedChannel
import com.lean.tv.parser.PlaylistParser
import com.lean.tv.parser.TxtParser
import com.lean.tv.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicInteger
import android.util.Log

data class SourceInput(
    val id: Long,
    val name: String,
    val url: String,
    val type: String
)

class ChannelRepository(private val db: AppDatabase) {

    private val refreshingCount = AtomicInteger(0)

    /** 正在刷新的订阅源数量，供配置页展示进度。 */
    val refreshing: Int
        get() = refreshingCount.get()

    suspend fun sources(): List<SourceEntity> = withContext(Dispatchers.IO) {
        db.sourceDao().all()
    }

    suspend fun source(id: Long): SourceEntity? = withContext(Dispatchers.IO) {
        db.sourceDao().find(id)
    }

    suspend fun channels(): List<ChannelEntity> = withContext(Dispatchers.IO) {
        db.channelDao().all()
    }

    /** 频道列表变化时自动推送，供电视端 UI 实时刷新。 */
    fun observeChannels(): Flow<List<ChannelEntity>> = db.channelDao().observeAll()

    fun observeChannelCount(): Flow<Int> = db.channelDao().observeCount()

    suspend fun channelCount(): Int = withContext(Dispatchers.IO) {
        db.channelDao().count()
    }

    suspend fun channelCount(sourceId: Long): Int = withContext(Dispatchers.IO) {
        db.channelDao().countBySource(sourceId)
    }

    suspend fun sourceCount(): Int = withContext(Dispatchers.IO) {
        db.sourceDao().count()
    }

    suspend fun alternativeUrls(name: String, url: String): List<String> =
        withContext(Dispatchers.IO) {
            db.channelDao().alternativeUrls(name, url)
        }

    /** 配置页预览：只返回前 limit 条，避免把整库频道序列化到手机。 */
    suspend fun previewChannels(query: String, limit: Int): List<ChannelEntity> =
        withContext(Dispatchers.IO) {
            db.channelDao().preview(query.trim(), limit.coerceIn(1, 500))
        }

    /**
     * 用前端提交的完整订阅源列表覆盖本地配置，返回需要刷新频道的源 id
     * （新增或 URL/类型有变化的源）。刷新由调用方放入后台，避免阻塞 HTTP 请求。
     */
    suspend fun syncSources(inputs: List<SourceInput>): List<Long> = withContext(Dispatchers.IO) {
        val current = db.sourceDao().all().associateBy { it.id }
        val submittedIds = inputs.map { it.id }.filter { it > 0L }.toSet()
        val refreshIds = LinkedHashSet<Long>()

        db.withTransaction {
            current.values
                .filter { it.id !in submittedIds }
                .forEach { stale ->
                    db.channelDao().deleteBySource(stale.id)
                    db.sourceDao().delete(stale.id)
                }

            inputs.forEach { input ->
                val existing = current[input.id]
                if (existing != null) {
                    if (existing.name != input.name ||
                        existing.url != input.url ||
                        existing.type != input.type
                    ) {
                        db.sourceDao().update(
                            existing.copy(name = input.name, url = input.url, type = input.type)
                        )
                        refreshIds.add(existing.id)
                    } else if (existing.lastUpdate == 0L) {
                        // 曾经保存但从未成功解析过，保存配置时顺带重试
                        refreshIds.add(existing.id)
                    }
                } else {
                    refreshIds.add(
                        db.sourceDao().insert(
                            SourceEntity(name = input.name, url = input.url, type = input.type)
                        )
                    )
                }
            }
        }
        refreshIds.toList()
    }

    /**
     * 最近一次刷新结果，供配置页展示具体失败原因。
     */
    data class RefreshState(val at: Long, val count: Int, val error: String?)

    private val lastStates = java.util.concurrent.ConcurrentHashMap<Long, RefreshState>()

    fun refreshState(sourceId: Long): RefreshState? = lastStates[sourceId]

    /**
     * 拉取并解析订阅源，整体替换该源的频道。
     * @return 解析到的频道数；-1 表示下载/解析失败，0 表示内容为空。
     */
    suspend fun refreshSource(source: SourceEntity): Int = withContext(Dispatchers.IO) {
        refreshingCount.incrementAndGet()
        try {
            val outcome = downloadAndParse(source)
            val parsed = outcome.channels
            if (parsed == null) {
                val reason = outcome.error ?: "下载失败"
                lastStates[source.id] = RefreshState(System.currentTimeMillis(), -1, reason)
                Log.w(TAG, "刷新失败 ${source.url}: $reason")
                return@withContext -1
            }
            if (parsed.isEmpty()) {
                lastStates[source.id] =
                    RefreshState(System.currentTimeMillis(), 0, "未解析到频道，请检查订阅格式")
                Log.w(TAG, "未解析到频道: ${source.url}")
                return@withContext 0
            }

            val entities = parsed.map {
                ChannelEntity(
                    name = it.name,
                    group = it.group,
                    logoUrl = it.logoUrl,
                    streamUrl = it.url,
                    sourceId = source.id
                )
            }
            val now = System.currentTimeMillis()

            db.withTransaction {
                db.channelDao().deleteBySource(source.id)
                db.channelDao().insertAll(entities)
                db.sourceDao().markUpdated(source.id, now)
            }
            lastStates[source.id] = RefreshState(now, entities.size, null)
            entities.size.also {
                Log.i(TAG, "订阅源 ${source.name} 刷新完成: $it 个频道")
            }
        } catch (e: Exception) {
            lastStates[source.id] = RefreshState(
                System.currentTimeMillis(),
                -1,
                "刷新异常：${e.javaClass.simpleName}"
            )
            Log.w(TAG, "刷新异常: ${source.url}", e)
            -1
        } finally {
            refreshingCount.decrementAndGet()
        }
    }

    private class ParseOutcome(val channels: List<ParsedChannel>?, val error: String?) {
        companion object {
            fun success(channels: List<ParsedChannel>) = ParseOutcome(channels, null)
            fun failure(reason: String) = ParseOutcome(null, reason)
        }
    }

    /**
     * 下载并逐行解析。全程流式读取，不把整个订阅源解码成字符串。
     */
    private fun downloadAndParse(source: SourceEntity): ParseOutcome {
        return try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", ConfigStore.userAgent)
                .build()

            Http.clientFor(source.url).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "订阅源 HTTP ${response.code}: ${source.url}")
                    return ParseOutcome.failure("HTTP ${response.code}")
                }
                val body = response.body ?: return ParseOutcome.failure("响应为空")
                if (body.contentLength() > MAX_SOURCE_BYTES) {
                    Log.w(TAG, "订阅源超过 32 MB: ${source.url}")
                    return ParseOutcome.failure("订阅源超过 32 MB")
                }

                val channels = body.byteStream().use { raw ->
                    val bounded = BoundedInputStream(raw, MAX_SOURCE_BYTES)
                    val buffered = BufferedInputStream(bounded, SNIFF_BYTES)
                    val sniff = sniff(buffered, source)
                    BufferedReader(
                        InputStreamReader(buffered, sniff.charset),
                        LINE_BUFFER
                    ).use { reader ->
                        sniff.parser.parse(reader)
                    }
                }
                ParseOutcome.success(channels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "下载订阅源失败: ${source.url}", e)
            ParseOutcome.failure(describeError(e))
        }
    }

    private fun describeError(e: Exception): String = when (e) {
        is javax.net.ssl.SSLException -> "TLS 握手失败（可开启兼容旧电视 TLS）"
        is java.net.UnknownHostException -> "域名解析失败"
        is java.net.SocketTimeoutException -> "连接超时"
        is IllegalArgumentException -> "订阅地址格式不正确"
        else -> e.message
            ?.take(80)
            ?.let { "${e.javaClass.simpleName}: $it" }
            ?: e.javaClass.simpleName
    }

    private class SniffResult(val charset: Charset, val parser: PlaylistParser)

    /**
     * 读取文件头部用于判断编码和格式，随后 reset 回起点，交给解析器重新读取。
     */
    private fun sniff(input: BufferedInputStream, source: SourceEntity): SniffResult {
        input.mark(SNIFF_BYTES + 8)
        val sample = ByteArray(SNIFF_BYTES)
        var length = 0
        while (length < sample.size) {
            val read = input.read(sample, length, sample.size - length)
            if (read < 0) break
            length += read
        }
        input.reset()

        val charset = detectCharset(sample, length)
        val bomLength = when {
            charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE -> 2
            length >= 3 &&
                sample[0] == 0xEF.toByte() &&
                sample[1] == 0xBB.toByte() &&
                sample[2] == 0xBF.toByte() -> 3
            else -> 0
        }
        if (bomLength > 0) input.skip(bomLength.toLong())

        val head = if (length > 0) String(sample, 0, length, charset) else ""
        val parser = when {
            head.contains("#EXTM3U", ignoreCase = true) -> M3uParser
            source.type.equals("M3U", ignoreCase = true) ||
                source.type.equals("M3U8", ignoreCase = true) -> M3uParser
            else -> TxtParser
        }
        return SniffResult(charset, parser)
    }

    /**
     * 中文直播源常见 GBK 编码：先按严格 UTF-8 解码样本，失败再回退 GBK。
     * 样本末尾可能截断多字节字符，因此不要求 endOfInput。
     */
    private fun detectCharset(sample: ByteArray, length: Int): Charset {
        if (length >= 2) {
            val b0 = sample[0].toInt() and 0xFF
            val b1 = sample[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return Charsets.UTF_16LE
            if (b0 == 0xFE && b1 == 0xFF) return Charsets.UTF_16BE
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(
                ByteBuffer.wrap(sample, 0, length),
                CharBuffer.allocate(length + 1),
                false
            )
            Charsets.UTF_8
        } catch (e: CharacterCodingException) {
            Charset.forName("GBK")
        }
    }

    /** 限制读取字节数，防止超大或无限流把电视内存吃满。 */
    private class BoundedInputStream(
        input: InputStream,
        private val limit: Long
    ) : FilterInputStream(input) {

        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) checkLimit(1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read > 0) checkLimit(read.toLong())
            return read
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) checkLimit(skipped)
            return skipped
        }

        private fun checkLimit(delta: Long) {
            count += delta
            if (count > limit) {
                throw IOException("订阅源超过 ${limit / 1024 / 1024} MB")
            }
        }
    }

    private companion object {
        const val TAG = "ChannelRepository"
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
        const val SNIFF_BYTES = 16 * 1024
        const val LINE_BUFFER = 16 * 1024
    }
}
