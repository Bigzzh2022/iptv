package com.lean.tv.ui

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
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
        reload()
    }

    fun reload() {
        if (!isAdded || view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val channels = App.repository.channels()
            channelAdapter.submit(channels)
        }
    }

    private companion object {
        const val COLUMNS = 4
    }
}
