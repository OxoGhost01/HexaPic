package com.oxoghost.hexapic.ui.deleted

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.data.db.DeletedItem
import com.oxoghost.hexapic.databinding.ItemDeletedPhotoBinding
import android.net.Uri
import java.util.concurrent.TimeUnit

class DeletedGridAdapter(
    private val onItemClick:     (id: Long) -> Unit,
    private val onItemLongClick: (id: Long) -> Unit,
) : ListAdapter<DeletedItem, DeletedGridAdapter.VH>(DIFF) {

    var cellSizePx: Int = 0
    var selectionMode: Boolean = false
        private set
    var selectedIds: Set<Long> = emptySet()
        private set

    companion object {
        private const val PAYLOAD_SEL = "sel"

        private val DIFF = object : DiffUtil.ItemCallback<DeletedItem>() {
            override fun areItemsTheSame(a: DeletedItem, b: DeletedItem) = a.mediaId == b.mediaId
            override fun areContentsTheSame(a: DeletedItem, b: DeletedItem) = a == b
        }
    }

    fun setSelectionMode(mode: Boolean) {
        if (selectionMode == mode) return
        selectionMode = mode
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SEL)
    }

    fun setSelectedIds(newIds: Set<Long>) {
        val old = selectedIds
        selectedIds = newIds
        for (i in 0 until itemCount) {
            val id = getItem(i).mediaId
            if ((id in old) != (id in newIds)) notifyItemChanged(i, PAYLOAD_SEL)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDeletedPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SEL }) holder.bindSelection()
        else super.onBindViewHolder(holder, position, payloads)
    }

    inner class VH(private val b: ItemDeletedPhotoBinding) : RecyclerView.ViewHolder(b.root) {

        private var currentId = -1L

        fun bind(item: DeletedItem) {
            currentId = item.mediaId

            b.root.setOnClickListener {
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                if (selectionMode) onItemClick(currentId)
                else { onItemLongClick(currentId) /* tap enters selection */ }
            }
            b.root.setOnLongClickListener {
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                onItemLongClick(currentId)
                true
            }

            b.thumbnail.load(Uri.parse(item.uri)) {
                placeholder(ColorDrawable(Color.parseColor("#FF2C2C2E")))
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.DISABLED)
                if (cellSizePx > 0) size(cellSizePx, cellSizePx)
            }

            b.tvDaysAgo.text = formatAge(item.deletedAt)
            bindSelection()
        }

        fun bindSelection() {
            val selected = currentId in selectedIds
            b.selectionIndicator.visibility = if (selectionMode) View.VISIBLE else View.GONE
            b.selectionDim.visibility = if (selectionMode && selected) View.VISIBLE else View.GONE
            b.selectionIndicator.setImageResource(
                if (selected) R.drawable.ic_selection_checked else R.drawable.ic_selection_empty
            )
        }

        private fun formatAge(deletedAtMillis: Long): String {
            val days = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - deletedAtMillis
            ).toInt()
            return when (days) {
                0    -> itemView.context.getString(R.string.deleted_today)
                1    -> itemView.context.getString(R.string.deleted_yesterday)
                else -> itemView.context.getString(R.string.deleted_n_days_ago, days)
            }
        }
    }
}
