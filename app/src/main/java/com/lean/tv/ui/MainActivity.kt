package com.lean.tv.ui

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.status_view)

        startService(Intent(this, WebServerService::class.java))

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
                }
            }
        }
    }
}
