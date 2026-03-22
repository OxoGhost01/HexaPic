package com.oxoghost.hexapic.ui.detail

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.abs

class GestureImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    interface GestureCallback {
        fun onSingleTap()
        fun onSwipeDownProgress(fraction: Float)
        fun onSwipeDownCommit()
        fun onSwipeDownCancel()
    }

    var gestureCallback: GestureCallback? = null

    // ── Matrix state ──────────────────────────────────────────────────────────
    private val baseMatrix  = Matrix()  // fit-center of the drawable
    private val suppMatrix  = Matrix()  // accumulated user zoom/pan
    private val drawMatrix  = Matrix()  // = suppMatrix ∘ baseMatrix

    private val maxScale = 10f

    // ── Swipe-down state ──────────────────────────────────────────────────────
    private var swipeDownY     = 0f
    private var isSwipingDown  = false

    // ── Gesture direction lock ────────────────────────────────────────────────
    private enum class Dir { NONE, H, V }
    private var dir  = Dir.NONE
    private var downX = 0f
    private var downY = 0f
    private val dirThresholdPx by lazy { 10f * resources.displayMetrics.density }

    // ── Fling ─────────────────────────────────────────────────────────────────
    private val scroller = OverScroller(context)
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                suppMatrix.postTranslate(
                    (scroller.currX - scroller.startX).toFloat() - lastFlingX,
                    (scroller.currY - scroller.startY).toFloat() - lastFlingY,
                )
                lastFlingX = (scroller.currX - scroller.startX).toFloat()
                lastFlingY = (scroller.currY - scroller.startY).toFloat()
                clampTranslation()
                applyMatrix()
                postOnAnimation(this)
            }
        }
    }
    private var lastFlingX = 0f
    private var lastFlingY = 0f

    // ── Detectors ─────────────────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context, object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = currentScale()
            val next = (cur * d.scaleFactor).coerceIn(0.5f, maxScale)
            val factor = next / cur
            suppMatrix.postScale(factor, factor, d.focusX, d.focusY)
            clampTranslation()
            applyMatrix()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object :
            GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            gestureCallback?.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale() > 1.5f) animateTo(Matrix())
            else animateScale(2f, e.x, e.y)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float,
        ): Boolean {
            if (scaleDetector.isInProgress) return false

            // Swipe-down dismiss (at min scale, dragging down)
            if (dir == Dir.V && isAtMinScale() && distY < 0) {
                swipeDownY = (swipeDownY - distY).coerceAtLeast(0f)
                isSwipingDown = true
                gestureCallback?.onSwipeDownProgress(
                    (swipeDownY / height).coerceIn(0f, 1f))
                return true
            }

            // Pan when zoomed
            if (!isAtMinScale()) {
                suppMatrix.postTranslate(-distX, -distY)
                clampTranslation()
                applyMatrix()
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velX: Float, velY: Float,
        ): Boolean {
            if (isSwipingDown && velY > 800f) {
                gestureCallback?.onSwipeDownCommit()
                return true
            }
            if (!isAtMinScale()) {
                val bounds = imageBounds()
                val minX = if (bounds.width() < width) 0 else (width - bounds.width().toInt())
                val maxX = if (bounds.width() < width) 0 else 0
                lastFlingX = 0f; lastFlingY = 0f
                scroller.fling(0, 0,
                    velX.toInt(), velY.toInt(),
                    minX, maxX, minX, 0)
                postOnAnimation(flingRunnable)
            }
            return true
        }
    })

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        scaleType = ScaleType.MATRIX
    }

    // ── Layout / drawable callbacks ───────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (drawable != null) setupBaseMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null && width > 0) setupBaseMatrix()
    }

    private fun setupBaseMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = minOf(vw / dw, vh / dh)
        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
        suppMatrix.reset()
        applyMatrix()
    }

    // ── Matrix helpers ────────────────────────────────────────────────────────
    private fun applyMatrix() {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        imageMatrix = drawMatrix
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        suppMatrix.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun isAtMinScale() = currentScale() <= 1.05f

    private fun imageBounds(): android.graphics.RectF {
        val d = drawable ?: return android.graphics.RectF()
        val r = android.graphics.RectF(
            0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(r)
        return r
    }

    private fun clampTranslation() {
        val b = imageBounds()
        val vw = width.toFloat()
        val vh = height.toFloat()
        var dx = 0f; var dy = 0f
        if (b.width() <= vw) dx = (vw - b.width()) / 2f - b.left
        else { if (b.left > 0) dx = -b.left; if (b.right < vw) dx = vw - b.right }
        if (b.height() <= vh) dy = (vh - b.height()) / 2f - b.top
        else { if (b.top > 0) dy = -b.top; if (b.bottom < vh) dy = vh - b.bottom }
        if (dx != 0f || dy != 0f) {
            suppMatrix.postTranslate(dx, dy)
            // recompute drawMatrix inline (avoid recursive call)
            drawMatrix.set(baseMatrix); drawMatrix.postConcat(suppMatrix)
            imageMatrix = drawMatrix
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────
    private fun animateScale(targetScale: Float, focusX: Float, focusY: Float) {
        val startVals = FloatArray(9).also { suppMatrix.getValues(it) }
        val target = Matrix().apply { postScale(targetScale, targetScale, focusX, focusY) }
        val endVals = FloatArray(9).also { target.getValues(it) }
        animateMatrix(startVals, endVals)
    }

    /** Animate suppMatrix → target (identity = zoom out to fit). */
    private fun animateTo(target: Matrix) {
        val startVals = FloatArray(9).also { suppMatrix.getValues(it) }
        val endVals   = FloatArray(9).also { target.getValues(it) }
        animateMatrix(startVals, endVals)
    }

    private fun animateMatrix(from: FloatArray, to: FloatArray) {
        val interp = FastOutSlowInInterpolator()
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = interp
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val vals = FloatArray(9) { i -> from[i] + (to[i] - from[i]) * t }
                suppMatrix.setValues(vals)
                clampTranslation()
                applyMatrix()
            }
        }
        animator.start()
    }

    fun springBack() {
        swipeDownY = 0f
        isSwipingDown = false
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x; downY = event.y
                dir = Dir.NONE; swipeDownY = 0f
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dir == Dir.NONE && !scaleDetector.isInProgress) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > dirThresholdPx || dy > dirThresholdPx) {
                        dir = if (dx > dy) Dir.H else Dir.V
                        if (dir == Dir.H && isAtMinScale()) {
                            // Let ViewPager2 handle horizontal swipes
                            parent.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipingDown) {
                    isSwipingDown = false
                    val progress = swipeDownY / height
                    if (progress > 0.3f) gestureCallback?.onSwipeDownCommit()
                    else gestureCallback?.onSwipeDownCancel()
                    swipeDownY = 0f
                }
            }
        }

        if (!scaleDetector.isInProgress && dir != Dir.H) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }
}
