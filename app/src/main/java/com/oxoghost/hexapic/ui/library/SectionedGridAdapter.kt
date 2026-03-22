package com.oxoghost.hexapic.ui.library

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.databinding.ItemPhotoBinding
import java.util.concurrent.TimeUnit

class SectionedGridAdapter(
    private val onPhotoClick: (photoIndex: Int, thumbnail: android.view.View) -> Unit,
) : ListAdapter<GridItem, RecyclerView.ViewHolder>(DIFF) {

    /** Cell size in pixels; set by the fragment after layout so Coil can size correctly. */
    var cellSizePx: Int = 0

    /** Flat index (photos-only) → adapter position, rebuilt after each diff completes. */
    private val photoFlatIndex = mutableListOf<Int>()

    // ── View types ────────────────────────────────────────────────────────────

    companion object {
        const val TYPE_MONTH_HEADER = 0
        const val TYPE_YEAR_SEP    = 1
        const val TYPE_PHOTO       = 2
        const val TYPE_FOOTER      = 3

        private val DIFF = object : DiffUtil.ItemCallback<GridItem>() {
            override fun areItemsTheSame(a: GridItem, b: GridItem) = when {
                a is GridItem.MonthHeader  && b is GridItem.MonthHeader  -> a.label == b.label
                a is GridItem.YearSeparator && b is GridItem.YearSeparator -> a.year == b.year
                a is GridItem.Photo        && b is GridItem.Photo        -> a.media.id == b.media.id
                a is GridItem.Footer       && b is GridItem.Footer       -> true
                else -> false
            }
            override fun areContentsTheSame(a: GridItem, b: GridItem) = a == b
        }
    }

    override fun onCurrentListChanged(previousList: List<GridItem>, currentList: List<GridItem>) {
        super.onCurrentListChanged(previousList, currentList)
        photoFlatIndex.clear()
        currentList.forEachIndexed { adapterPos, item ->
            if (item is GridItem.Photo) photoFlatIndex.add(adapterPos)
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is GridItem.MonthHeader   -> TYPE_MONTH_HEADER
        is GridItem.YearSeparator -> TYPE_YEAR_SEP
        is GridItem.Photo         -> TYPE_PHOTO
        is GridItem.Footer        -> TYPE_FOOTER
    }

    // ── ViewHolder creation ───────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MONTH_HEADER -> MonthHeaderVH(inflater.inflate(R.layout.item_section_header, parent, false))
            TYPE_YEAR_SEP     -> YearSepVH(inflater.inflate(R.layout.item_year_separator, parent, false))
            TYPE_FOOTER       -> FooterVH(inflater.inflate(R.layout.item_footer, parent, false))
            else              -> PhotoVH(ItemPhotoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GridItem.MonthHeader   -> (holder as MonthHeaderVH).bind(item)
            is GridItem.YearSeparator -> (holder as YearSepVH).bind(item)
            is GridItem.Photo         -> (holder as PhotoVH).bind(item)
            is GridItem.Footer        -> (holder as FooterVH).bind(item)
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    class MonthHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val label = view.findViewById<TextView>(R.id.tvSectionLabel)
        fun bind(item: GridItem.MonthHeader) { label.text = item.label }
    }

    class YearSepVH(view: View) : RecyclerView.ViewHolder(view) {
        private val label = view.findViewById<TextView>(R.id.tvYear)
        fun bind(item: GridItem.YearSeparator) { label.text = item.year.toString() }
    }

    class FooterVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text = view.findViewById<TextView>(R.id.tvFooterCount)
        fun bind(item: GridItem.Footer) {
            text.text = buildString {
                append(item.photoCount)
                append(if (item.photoCount == 1) " Photo" else " Photos")
                if (item.videoCount > 0) {
                    append(", ")
                    append(item.videoCount)
                    append(if (item.videoCount == 1) " Video" else " Videos")
                }
            }
        }
    }

    inner class PhotoVH(private val binding: ItemPhotoBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GridItem.Photo) {
            val media = item.media

            // Shared-element transition name
            binding.thumbnail.transitionName = "photo_${media.id}"

            // Click → open detail
            binding.root.setOnClickListener {
                val flatIdx = photoFlatIndex.indexOf(bindingAdapterPosition)
                if (flatIdx >= 0) onPhotoClick(flatIdx, binding.thumbnail)
            }

            // Thumbnail — use MediaStore thumbnail API (system-cached, no full JPEG decode)
            binding.thumbnail.load(MediaThumb(media.uri, media.isVideo)) {
                placeholder(ColorDrawable(Color.parseColor("#FF2C2C2E")))
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.DISABLED)  // system already caches thumbnails
                if (cellSizePx > 0) size(cellSizePx, cellSizePx)
            }

            // Video duration badge
            if (media.isVideo && media.duration > 0) {
                binding.videoDuration.text = formatDuration(media.duration)
                binding.videoDuration.visibility = View.VISIBLE
                binding.videoIcon.visibility = View.VISIBLE
                binding.videoBadgeContainer.visibility = View.VISIBLE
            } else {
                binding.videoBadgeContainer.visibility = View.GONE
            }

            // Favorite heart
            binding.ivFavorite.visibility = if (media.isFavorite) View.VISIBLE else View.GONE
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes >= 60) {
                val hours = minutes / 60
                val mins  = minutes % 60
                "%d:%02d:%02d".format(hours, mins, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }

    // ── Sticky-header helpers (called by StickyHeaderDecoration) ─────────────

    /** Returns the position of the MonthHeader that governs [position], or -1. */
    fun findHeaderPositionFor(position: Int): Int {
        for (i in position downTo 0) {
            if (currentList.getOrNull(i) is GridItem.MonthHeader) return i
        }
        return -1
    }

    /** Returns the position of the next MonthHeader after [headerPos], or -1. */
    fun findNextHeaderAfter(headerPos: Int): Int {
        for (i in headerPos + 1 until itemCount) {
            if (currentList.getOrNull(i) is GridItem.MonthHeader) return i
        }
        return -1
    }
}
