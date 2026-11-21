package com.lean.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lean.tv.App
import com.lean.tv.R
import com.lean.tv.data.AspectMode
import com.lean.tv.data.ConfigStore
import com.lean.tv.data.entity.ChannelEntity
import com.lean.tv.player.PlayerManager
import kotlinx.coroutines.launch

class PlaybackActivity : FragmentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var hintView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)

        playerView = findViewById(R.id.player_view)
        titleView = findViewById(R.id.title_view)
        hintView = findViewById(R.id.hint_view)

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isEmpty()) {
            finish()
            return
        }
        titleView.text = name

        PlayerManager.init(this, ConfigStore.decoderMode)
        PlayerManager.onPlayerChanged = { player -> playerView.player = player }
        PlayerManager.onError = { message ->
            hintView.text = message
            hintView.visibility = View.VISIBLE
        }
        playerView.player = PlayerManager.player()
        playerView.resizeMode = when (ConfigStore.aspectMode) {
            AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.controllerShowTimeoutMs = 3000

        lifecycleScope.launch {
            // 同名频道的其它线路，用于播放失败时自动换源
            val alternatives = App.repository.alternativeUrls(name, url)
            PlayerManager.play(listOf(url) + alternatives)
        }
    }

    override fun onPause() {
        super.onPause()
        PlayerManager.pause()
    }

    override fun onDestroy() {
        PlayerManager.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_URL = "extra_url"

        fun intent(context: Context, channel: ChannelEntity): Intent =
            Intent(context, PlaybackActivity::class.java)
                .putExtra(EXTRA_NAME, channel.name)
                .putExtra(EXTRA_URL, channel.streamUrl)
    }
}
