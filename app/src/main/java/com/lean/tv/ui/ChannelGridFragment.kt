package com.lean.tv.ui

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lean.tv.App
import kotlinx.coroutines.launch

class ChannelGridFragment : VerticalGridSupportFragment() {

    private val channelAdapter = ChannelAdapter { channel ->
        startActivity(PlaybackActivity.intent(requireContext(), channel))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presenter = VerticalGridPresenter()
        presenter.numberOfColumns = COLUMNS
        gridPresenter = presenter
        setAdapter(channelAdapter)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Room Flow：订阅源在 Web 端保存并刷新后，频道列表会自动推送到电视端
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                App.repository.observeChannels().collect { channels ->
                    channelAdapter.submit(channels)
                }
            }
        }
    }

    private companion object {
        const val COLUMNS = 4
    }
}
