package com.lean.tv.util

import android.os.SystemClock
import com.lean.tv.data.ConfigStore
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.Inet6Address
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
            // 老电视的 IPv6 路由通常不可用，优先尝试 IPv4 地址
            val addresses = Dns.SYSTEM.lookup(hostname)
                .sortedBy { if (it is Inet6Address) 1 else 0 }
            synchronized(cache) {
                if (cache.size >= 64) cache.clear()
                cache[hostname] = DnsEntry(addresses, now + DNS_TTL_MS)
            }
            return addresses
        }
    }

    private fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(dnsCache)

    val client: OkHttpClient by lazy { newBuilder().build() }

    /**
     * 兼容模式客户端：不校验证书链与域名。
     * 仅当用户在配置页显式开启「兼容旧电视 TLS」时使用，用于 Android 5.x 缺少新根证书的源。
     */
    val insecureClient: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun clientFor(url: String): OkHttpClient =
        if (ConfigStore.insecureTls && url.startsWith("https://", ignoreCase = true)) {
            insecureClient
        } else {
            client
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
