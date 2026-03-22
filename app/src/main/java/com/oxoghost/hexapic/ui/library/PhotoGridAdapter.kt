package com.oxoghost.hexapic.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexapic.data.MediaItem
import com.oxoghost.hexapic.databinding.ItemPhotoBinding
import java.util.concurrent.TimeUnit

class PhotoGridAdapter : ListAdapter<MediaItem, PhotoGridAdapter.MediaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(private val binding: ItemPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            binding.thumbnail.load(item.uri) {
                crossfade(true)
                error(android.R.color.darker_gray)
            }

            if (item.isVideo && item.duration > 0) {
                binding.videoDuration.text = formatDuration(item.duration)
                binding.videoDuration.visibility = android.view.View.VISIBLE
                binding.videoIcon.visibility = android.view.View.VISIBLE
            } else {
                binding.videoDuration.visibility = android.view.View.GONE
                binding.videoIcon.visibility = android.view.View.GONE
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes >= 60) {
                val hours = minutes / 60
                val mins = minutes % 60
                "%d:%02d:%02d".format(hours, mins, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.id == b.id
            override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
        }
    }
}
