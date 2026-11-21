package com.lean.tv.data

import androidx.room.withTransaction
import com.lean.tv.data.entity.ChannelEntity
import com.lean.tv.data.entity.SourceEntity
import com.lean.tv.parser.M3uParser
import com.lean.tv.parser.PlaylistParser
import com.lean.tv.parser.TxtParser
import com.lean.tv.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import android.util.Log

data class SourceInput(
    val id: Long,
    val name: String,
    val url: String,
    val type: String
)

class ChannelRepository(private val db: AppDatabase) {

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

    suspend fun sourceCount(): Int = withContext(Dispatchers.IO) {
        db.sourceDao().count()
    }

    suspend fun alternativeUrls(name: String, url: String): List<String> =
        withContext(Dispatchers.IO) {
            db.channelDao().alternativeUrls(name, url)
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
     * 拉取并解析订阅源，整体替换该源的频道。
     * @return 解析到的频道数；-1 表示下载失败，0 表示内容为空。
     */
    suspend fun refreshSource(source: SourceEntity): Int = withContext(Dispatchers.IO) {
        val text = try {
            fetchText(source.url)
        } catch (e: Exception) {
            Log.w(TAG, "下载订阅源失败: ${source.url}", e)
            null
        } ?: return@withContext -1

        val parser: PlaylistParser = when {
            text.contains("#EXTM3U", ignoreCase = true) -> M3uParser
            source.type.equals("M3U", ignoreCase = true) -> M3uParser
            else -> TxtParser
        }

        val parsed = parser.parse(text)
        if (parsed.isEmpty()) {
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
        entities.size.also {
            Log.i(TAG, "订阅源 ${source.name} 刷新完成: $it 个频道")
        }
    }

    private fun fetchText(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LeanIPTV/1.0")
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_SOURCE_BYTES) return null
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) return null
            return decode(bytes)
        }
    }

    /**
     * 中文直播源常见 GBK 编码：先按严格 UTF-8 解码，失败再回退 GBK。
     */
    private fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("GBK"))
        }
    }

    private companion object {
        const val TAG = "ChannelRepository"
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
    }
}
