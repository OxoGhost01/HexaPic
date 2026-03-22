package com.oxoghost.hexapic.ui.library

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oxoghost.hexapic.R

/**
 * Draws a sticky copy of the current section's MonthHeader at the top of the RecyclerView.
 * When the next header scrolls up and hits the sticky one, it pushes it off the top.
 */
class StickyHeaderDecoration(private val adapter: SectionedGridAdapter) :
        RecyclerView.ItemDecoration() {

    private var cachedView: View? = null
    private var cachedLabel: String? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val lm = parent.layoutManager as? GridLayoutManager ?: return
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_ID.toInt()) return

        val headerPos = adapter.findHeaderPositionFor(firstPos)
        if (headerPos < 0) return
        val label = (adapter.currentList.getOrNull(headerPos) as? GridItem.MonthHeader)
                ?.label ?: return

        val sticky = getView(parent, label)

        // Push sticky upward when the next header approaches
        var translateY = 0f
        val nextHeaderPos = adapter.findNextHeaderAfter(headerPos)
        if (nextHeaderPos >= 0) {
            val nextView = parent.findViewHolderForAdapterPosition(nextHeaderPos)?.itemView
            if (nextView != null && nextView.top < sticky.height) {
                translateY = (nextView.top - sticky.height).toFloat()
            }
        }

        c.save()
        c.translate(0f, translateY)
        sticky.draw(c)
        c.restore()
    }

    private fun getView(parent: RecyclerView, label: String): View {
        if (cachedLabel != label || cachedView == null) {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section_header, parent, false)
            view.findViewById<TextView>(R.id.tvSectionLabel).text = label
            val wSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(wSpec, hSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            cachedView  = view
            cachedLabel = label
        }
        return cachedView!!
    }
}
