package com.oxoghost.hexapic.ui.library

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** Adds uniform gaps between grid cells, no outer padding. */
class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int,       // pixels
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        outRect.left = column * spacing / spanCount
        outRect.right = spacing - (column + 1) * spacing / spanCount
        if (position >= spanCount) {
            outRect.top = spacing
        }
    }
}
