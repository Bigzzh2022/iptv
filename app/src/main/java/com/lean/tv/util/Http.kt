package com.lean.tv.util

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 全局唯一的 OkHttp 客户端：
 * - 长连接复用，减少直播换台时的 TCP 握手
 * - 自带 DNS 缓存，避免同一域名反复解析
 */
object Http {

    private val dnsCache = object : Dns {

        private val cache = HashMap<String, List<InetAddress>>()

        override fun lookup(hostname: String): List<InetAddress> {
            synchronized(cache) {
                cache[hostname]?.let { return it }
            }
            val addresses = Dns.SYSTEM.lookup(hostname)
            synchronized(cache) {
                if (cache.size >= 64) cache.clear()
                cache[hostname] = addresses
            }
            return addresses
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(dnsCache)
            .build()
    }

    /**
     * 播放前预解析域名（阻塞调用，请在 IO 线程执行）。
     */
    fun prewarm(urls: List<String>) {
        urls.asSequence()
            .mapNotNull { it.toHttpUrlOrNull()?.host }
            .distinct()
            .take(8)
            .forEach { host ->
                runCatching { dnsCache.lookup(host) }
            }
    }
}
