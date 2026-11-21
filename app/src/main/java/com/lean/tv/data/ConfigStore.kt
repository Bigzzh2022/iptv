package com.lean.tv.data

import com.tencent.mmkv.MMKV

/**
 * 轻量配置（MMKV），只存标量，订阅源仍放在 Room。
 */
object ConfigStore {

    const val DEFAULT_PORT = 10481
    const val DEFAULT_USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18"

    private const val KEY_EPG = "epg_url"
    private const val KEY_DECODER = "decoder_mode"
    private const val KEY_PORT = "http_port"
    private const val KEY_UA = "user_agent"
    private const val KEY_BUFFER = "buffer_profile"
    private const val KEY_ASPECT = "aspect_mode"

    private val kv: MMKV by lazy { MMKV.defaultMMKV() }

    fun init() {
        // 触发 MMKV 初始化，必须在 Application.onCreate 中调用
        kv
    }

    var epgUrl: String
        get() = kv.decodeString(KEY_EPG, "").orEmpty()
        set(value) {
            kv.encode(KEY_EPG, value)
        }

    var decoderMode: String
        get() = kv.decodeString(KEY_DECODER, DecoderMode.AUTO).orEmpty()
        set(value) {
            kv.encode(KEY_DECODER, value)
        }

    /**
     * 拉流时使用的 User-Agent。部分源站会拒绝 ExoPlayer 默认 UA 并返回 403，
     * 默认使用 VLC UA，可在 Web 配置页自定义。
     */
    var userAgent: String
        get() = kv.decodeString(KEY_UA, DEFAULT_USER_AGENT).orEmpty()
            .ifBlank { DEFAULT_USER_AGENT }
        set(value) {
            kv.encode(KEY_UA, value.replace("\r", "").replace("\n", "").trim())
        }

    var httpPort: Int
        get() = kv.decodeInt(KEY_PORT, DEFAULT_PORT)
        set(value) {
            kv.encode(KEY_PORT, value)
        }

    /** 缓冲档位：流畅 / 均衡 / 稳定。 */
    var bufferProfile: String
        get() = kv.decodeString(KEY_BUFFER, BufferProfile.BALANCED).orEmpty()
            .takeIf { BufferProfile.isValid(it) } ?: BufferProfile.BALANCED
        set(value) {
            kv.encode(KEY_BUFFER, value)
        }

    /** 画面比例：自适应 / 拉伸 / 裁剪。 */
    var aspectMode: String
        get() = kv.decodeString(KEY_ASPECT, AspectMode.FIT).orEmpty()
            .takeIf { AspectMode.isValid(it) } ?: AspectMode.FIT
        set(value) {
            kv.encode(KEY_ASPECT, value)
        }
}

object BufferProfile {
    const val FAST = "fast"
    const val BALANCED = "balanced"
    const val STABLE = "stable"

    fun isValid(value: String): Boolean =
        value == FAST || value == BALANCED || value == STABLE
}

object AspectMode {
    const val FIT = "fit"
    const val FILL = "fill"
    const val ZOOM = "zoom"

    fun isValid(value: String): Boolean =
        value == FIT || value == FILL || value == ZOOM
}

object DecoderMode {
    const val AUTO = "auto"
    const val HW = "hw"
    const val SW = "sw"

    fun isValid(value: String): Boolean = value == AUTO || value == HW || value == SW
}
