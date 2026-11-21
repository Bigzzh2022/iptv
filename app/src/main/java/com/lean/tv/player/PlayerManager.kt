package com.lean.tv.player

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lean.tv.App
import com.lean.tv.data.BufferProfile
import com.lean.tv.data.ConfigStore
import com.lean.tv.data.DecoderMode
import com.lean.tv.util.Http
import kotlinx.coroutines.launch

/**
 * 单例播放器：面向 Android 5.1.1 低性能设备。
 *
 * - 缓冲：15s 起播水位 / 50s 上限
 * - 网络：OkHttp + DNS 缓存 + 长连接
 * - 容错：同频道多线路自动切换；线路全挂后自动降级软解
 */
object PlayerManager {

    private const val TAG = "PlayerManager"
    private const val LOW_MEMORY_MB = 200L
    private const val MAX_SOFTWARE_RETRY = 1

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var exoPlayer: ExoPlayer? = null
    private var softwareDecode = false
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var softwareRetry = 0

    var onPlayerChanged: ((ExoPlayer) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init(context: Context, decoderMode: String) {
        val ctx = context.applicationContext
        appContext = ctx
        softwareDecode = when (decoderMode) {
            DecoderMode.SW -> true
            DecoderMode.HW -> false
            else -> isLowMemory(ctx)
        }
        ensurePlayer()
    }

    fun player(): ExoPlayer? = exoPlayer

    fun play(urlList: List<String>) {
        val list = urlList.filter { it.isNotBlank() }.distinct()
        if (list.isEmpty()) return

        urls = list
        urlIndex = 0
        softwareRetry = 0
        App.appScope.launch { Http.prewarm(list) }
        prepareCurrent()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun release() {
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }
        exoPlayer = null
        urls = emptyList()
        urlIndex = 0
        softwareRetry = 0
        onPlayerChanged = null
        onError = null
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val ctx = appContext ?: error("PlayerManager 尚未初始化")
        val player = buildPlayer(ctx, softwareDecode)
        exoPlayer = player
        onPlayerChanged?.invoke(player)
        return player
    }

    private fun buildPlayer(context: Context, forceSoftware: Boolean): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .apply {
                if (forceSoftware) {
                    setMediaCodecSelector(MediaCodecSelector { mimeType, secure, tunneling ->
                        val decoders = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                        decoders.filter { it.softwareOnly }.ifEmpty { decoders }
                    })
                }
            }

        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(
                if (ConfigStore.insecureTls) Http.insecureClient else Http.client
            )
                .setUserAgent(ConfigStore.userAgent)
        )

        val durations = bufferDurations(ConfigStore.bufferProfile)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(durations[0], durations[1], durations[2], durations[3])
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .also { it.addListener(playerListener) }
    }

    private fun prepareCurrent() {
        if (urls.isEmpty()) return
        val player = ensurePlayer()
        player.setMediaItem(MediaItem.fromUri(urls[urlIndex]))
        player.prepare()
        player.playWhenReady = true
    }

    private fun rebuildPlayer() {
        val ctx = appContext ?: return
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }
        exoPlayer = null
        val player = buildPlayer(ctx, softwareDecode)
        exoPlayer = player
        onPlayerChanged?.invoke(player)
        prepareCurrent()
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val url = urls.getOrNull(urlIndex).orEmpty()
        Log.e(TAG, "播放失败 ${error.errorCodeName} url=$url", error)

        if (urlIndex + 1 < urls.size) {
            Log.w(TAG, "切换备用线路 ${urlIndex + 2}/${urls.size}")
            urlIndex++
            prepareCurrent()
            return
        }
        // 网络/HTTP 错误换软解没有意义，只对解码类错误做降级
        if (!softwareDecode && softwareRetry < MAX_SOFTWARE_RETRY && isDecoderError(error)) {
            softwareRetry++
            softwareDecode = true
            rebuildPlayer()
            return
        }
        onError?.invoke(describe(error))
    }

    private fun isDecoderError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

    /** 优先把 HTTP 状态码暴露给用户，403 通常是 UA / 防盗链问题。 */
    private fun describe(error: PlaybackException): String {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return "播放失败：HTTP ${cause.responseCode}"
            }
            cause = cause.cause
        }
        return "播放失败：${error.errorCodeName}"
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            mainHandler.post { handlePlaybackError(error) }
        }
    }

    /**
     * 缓冲档位 -> [最小缓冲, 最大缓冲, 起播缓冲, 卡顿恢复缓冲]（毫秒）
     */
    private fun bufferDurations(profile: String): IntArray = when (profile) {
        BufferProfile.FAST -> intArrayOf(8_000, 25_000, 1_000, 2_000)
        BufferProfile.STABLE -> intArrayOf(25_000, 80_000, 2_000, 5_000)
        else -> intArrayOf(15_000, 50_000, 1_500, 3_000)
    }

    private fun isLowMemory(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem / (1024 * 1024) < LOW_MEMORY_MB
    }
}
