package com.lean.tv.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.lean.tv.data.ConfigStore
import com.lean.tv.util.NetUtils
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class WebServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return
        val port = ConfigStore.httpPort
        val instance = IptvHttpServer(port, applicationContext)
        try {
            instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = instance
            Log.i(TAG, "局域网配置服务已启动: http://${NetUtils.localIpv4()}:$port")
        } catch (e: IOException) {
            Log.e(TAG, "配置服务启动失败，端口 $port 可能被占用", e)
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebServerService"

        @Volatile
        var server: IptvHttpServer? = null
            private set
    }
}
