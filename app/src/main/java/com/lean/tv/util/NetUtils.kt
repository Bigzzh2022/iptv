package com.lean.tv.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtils {

    /**
     * 取局域网 IPv4，优先站点本地地址（192.168 / 10 / 172.16）。
     */
    fun localIpv4(): String {
        return try {
            val candidates = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
            candidates.firstOrNull { it.isSiteLocalAddress }?.hostAddress
                ?: candidates.firstOrNull()?.hostAddress
                ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
