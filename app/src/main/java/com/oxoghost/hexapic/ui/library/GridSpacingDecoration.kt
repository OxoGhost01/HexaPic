package com.oxoghost.hexapic.ui.library

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Adds uniform gaps between photo cells only; skips headers, separators, and footer. */
class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int,           // pixels
    private val adapter: SectionedGridAdapter,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < 0) return

        if (adapter.currentList.getOrNull(position) !is GridItem.Photo) {
            outRect.set(0, 0, 0, 0)
            return
        }

        val lm = parent.layoutManager as? GridLayoutManager ?: return
        val spanIndex = lm.spanSizeLookup.getSpanIndex(position, spanCount)
        val groupIndex = lm.spanSizeLookup.getSpanGroupIndex(position, spanCount)

        outRect.left  = spanIndex * spacing / spanCount
        outRect.right = spacing - (spanIndex + 1) * spacing / spanCount
        if (groupIndex > 0) outRect.top = spacing
    }
}
