package com.lean.tv.util

import android.os.SystemClock
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 全局唯一的 OkHttp 客户端：
 * - 长连接复用，减少直播换台时的 TCP 握手
 * - 自带带过期时间的 DNS 缓存，避免同一域名反复解析，也防止源站换 IP 后一直连旧地址
 */
object Http {

    private const val DNS_TTL_MS = 10 * 60 * 1000L

    private class DnsEntry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val dnsCache = object : Dns {

        private val cache = HashMap<String, DnsEntry>()

        override fun lookup(hostname: String): List<InetAddress> {
            val now = SystemClock.elapsedRealtime()
            synchronized(cache) {
                val entry = cache[hostname]
                if (entry != null && entry.expiresAt > now) return entry.addresses
            }
            val addresses = Dns.SYSTEM.lookup(hostname)
            synchronized(cache) {
                if (cache.size >= 64) cache.clear()
                cache[hostname] = DnsEntry(addresses, now + DNS_TTL_MS)
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
