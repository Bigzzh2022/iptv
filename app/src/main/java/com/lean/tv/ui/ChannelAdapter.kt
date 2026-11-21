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

    private var keys: List<ChannelKey> = emptyList()

    /**
     * 增量更新：先比较首尾未变化区间，只对中间差异做增删，
     * 避免每次订阅源刷新都 clear + 全量重绑几千个频道。
     */
    fun submit(items: List<ChannelEntity>) {
        val newKeys = items.map {
            ChannelKey(it.sourceId, it.name, it.group, it.logoUrl, it.streamUrl)
        }
        if (newKeys == keys) return

        var prefix = 0
        val maxPrefix = minOf(keys.size, newKeys.size)
        while (prefix < maxPrefix && keys[prefix] == newKeys[prefix]) prefix++

        var suffix = 0
        val maxSuffix = minOf(keys.size - prefix, newKeys.size - prefix)
        while (suffix < maxSuffix &&
            keys[keys.size - 1 - suffix] == newKeys[newKeys.size - 1 - suffix]
        ) {
            suffix++
        }

        val oldMiddle = keys.size - prefix - suffix
        val newMiddle = newKeys.size - prefix - suffix

        when {
            oldMiddle == 0 -> addAll(prefix, items.subList(prefix, prefix + newMiddle))
            newMiddle == 0 -> removeItems(prefix, oldMiddle)
            oldMiddle == 1 && newMiddle == 1 -> replace(prefix, items[prefix])
            else -> {
                removeItems(prefix, oldMiddle)
                addAll(prefix, items.subList(prefix, prefix + newMiddle))
            }
        }
        keys = newKeys
    }

    private data class ChannelKey(
        val sourceId: Long,
        val name: String,
        val group: String,
        val logoUrl: String,
        val streamUrl: String
    )
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
