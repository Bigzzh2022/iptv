package com.lean.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lean.tv.App
import com.lean.tv.R
import com.lean.tv.data.ConfigStore
import com.lean.tv.server.WebServerService
import com.lean.tv.util.NetUtils
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var statusView: TextView
    private var gridFragment: ChannelGridFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.status_view)

        startService(Intent(this, WebServerService::class.java))

        if (savedInstanceState == null) {
            val fragment = ChannelGridFragment()
            gridFragment = fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.grid_container, fragment)
                .commit()
        } else {
            gridFragment =
                supportFragmentManager.findFragmentById(R.id.grid_container) as? ChannelGridFragment
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        gridFragment?.reload()
        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val count = App.repository.channelCount()
            statusView.text = getString(
                R.string.status_format,
                NetUtils.localIpv4(),
                ConfigStore.httpPort,
                count
            )
        }
    }
}
