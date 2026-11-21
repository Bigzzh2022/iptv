package com.lean.tv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.Presenter
import com.lean.tv.R
import com.lean.tv.data.entity.ChannelEntity

class ChannelAdapter(
    private val onClick: (ChannelEntity) -> Unit
) : ArrayObjectAdapter(ChannelPresenter(onClick)) {

    fun submit(items: List<ChannelEntity>) {
        clear()
        addAll(0, items)
    }
}

private class ChannelPresenter(
    private val onClick: (ChannelEntity) -> Unit
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
        )

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val channel = item as? ChannelEntity ?: return
        val text = viewHolder.view as TextView
        text.text = channel.name
        text.setOnClickListener { onClick(channel) }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as TextView).setOnClickListener(null)
    }
}
