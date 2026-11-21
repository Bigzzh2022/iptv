package com.lean.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lean.tv.App
import com.lean.tv.R
import com.lean.tv.data.ConfigStore
import com.lean.tv.server.WebServerService
import com.lean.tv.util.NetUtils
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var statusView: TextView
    private var onboardingChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.status_view)

        startService(Intent(this, WebServerService::class.java))

        findViewById<Button>(R.id.qr_button).setOnClickListener { showQrCode() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.grid_container, ChannelGridFragment())
                .commit()
        }

        // 频道数随订阅源刷新自动更新
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                App.repository.observeChannelCount().collect { count ->
                    statusView.text = getString(
                        R.string.status_format,
                        NetUtils.localIpv4(),
                        ConfigStore.httpPort,
                        count
                    )
                    maybeShowOnboarding(count)
                }
            }
        }
    }

    /** 遥控器菜单键也能唤出二维码，避免网格焦点吃掉了按钮焦点。 */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showQrCode()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showQrCode() {
        if (isFinishing || isDestroyed) return
        val url = "http://${NetUtils.localIpv4()}:${ConfigStore.httpPort}"
        QrCodeDialog(this, url).show()
    }

    /** 首次启动且没有任何订阅源时，自动弹出配置二维码引导。 */
    private fun maybeShowOnboarding(channelCount: Int) {
        if (onboardingChecked) return
        onboardingChecked = true
        if (channelCount > 0) return

        lifecycleScope.launch {
            if (App.repository.sourceCount() == 0) {
                statusView.postDelayed({
                    if (!isFinishing && !isDestroyed) showQrCode()
                }, 600)
            }
        }
    }
}
