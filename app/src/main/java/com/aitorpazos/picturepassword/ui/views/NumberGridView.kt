package com.aitorpazos.picturepassword.ui.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.aitorpazos.picturepassword.model.NumberGrid
import com.aitorpazos.picturepassword.model.NumberGridFactory

/**
 * Custom view that renders the draggable rectangular number grid overlay.
 *
 * The grid is a large matrix of digits 0-9 (with repeats). Only the portion
 * that falls within the visible screen is drawn, but the grid extends beyond
 * the screen in all directions so that panning always reveals more numbers.
 *
 * Each cell is a square whose side = screenWidth / VISIBLE_COLS.
 * The grid is initially centred so that roughly the middle columns/rows are visible.
 */
class NumberGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var numberGrid: NumberGrid? = null
        set(value) {
            field = value
            totalDragX = 0f
            totalDragY = 0f
            invalidate()
        }

    /** How many columns are visible on screen. Controls digit size/density. */
    var visibleCols: Int = NumberGridFactory.VISIBLE_COLS
        set(value) {
            field = value
            invalidate()
        }

    var onGridMoved: ((offsetX: Float, offsetY: Float) -> Unit)? = null
    var onGridReleased: ((offsetX: Float, offsetY: Float) -> Unit)? = null

    /** Highlight a specific digit (used during setup to show selected number) */
    var highlightedDigit: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    /** Show a target point indicator (used during setup confirmation step) */
    var showTargetPoint: Boolean = false
    var targetPointX: Float = 0f
    var targetPointY: Float = 0f

    /** When true, digits are always visible (used during setup). */
    var alwaysShowDigits: Boolean = false
        set(value) {
            field = value
            if (value) {
                gridAlpha = 1f
                fadeAnimator?.cancel()
            }
            invalidate()
        }

    private var totalDragX = 0f
    private var totalDragY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    /** Current alpha for the digit grid (0 = invisible, 1 = fully visible) */
    private var gridAlpha = 0f

    /** Animator for smooth fade in/out */
    private var fadeAnimator: ValueAnimator? = null

    /** Delay before fading out after touch release (ms) */
    private val fadeOutDelayMs = 400L

    /** Runnable that triggers fade-out after delay */
    private val fadeOutRunnable = Runnable { animateGridAlpha(0f) }

    private fun animateGridAlpha(target: Float) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(gridAlpha, target).apply {
            duration = if (target > 0f) 150L else 300L  // fade in fast, fade out slow
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                gridAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---- Paints ----

    /** Digit text — white, thin/light weight, centred, with dark stroke outline for contrast */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    /** Dark stroke outline drawn behind the white text for visibility on any background */
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        style = Paint.Style.STROKE
    }

    /** Highlighted digit text (blue tint) */
    private val highlightTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 80, 160, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(6f, 0f, 0f, Color.argb(200, 0, 80, 200))
    }

    /** Stroke outline for highlighted digit */
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        style = Paint.Style.STROKE
    }

    /** Target point ring (setup confirmation step) */
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 60, 60)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Target point fill (setup confirmation step) */
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 60, 60)
        style = Paint.Style.FILL
    }

    /** Tolerance radius in width-fraction units for drawing the target circle.
     *  This should match config.toleranceRadius; set from the activity. */
    var toleranceRadius: Float = DEFAULT_TOLERANCE

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Draw target point if enabled (setup confirmation only)
        if (showTargetPoint) {
            val tx = targetPointX * w
            // targetPointY is in width-fraction units, so multiply by width not height
            val ty = targetPointY * w
            val targetRadius = w * toleranceRadius

            canvas.drawCircle(tx, ty, targetRadius, targetFillPaint)
            canvas.drawCircle(tx, ty, targetRadius, targetPaint)
            canvas.drawCircle(tx, ty, 6f, targetPaint)
        }

        val grid = numberGrid ?: return

        // Determine effective alpha for digits
        val effectiveAlpha = if (alwaysShowDigits) 1f else gridAlpha
        if (effectiveAlpha <= 0f) return  // nothing to draw

        // Cell size in pixels: screen width divided by visible columns
        val cellPx = w / visibleCols

        // Text size scales with cell size — no circles, just text
        val fontSize = cellPx * 0.55f
        textPaint.textSize = fontSize
        textPaint.alpha = (255 * effectiveAlpha).toInt()
        textStrokePaint.textSize = fontSize
        textStrokePaint.strokeWidth = fontSize * 0.12f
        textStrokePaint.alpha = (220 * effectiveAlpha).toInt()
        highlightTextPaint.textSize = fontSize
        highlightTextPaint.alpha = (255 * effectiveAlpha).toInt()
        highlightStrokePaint.textSize = fontSize
        highlightStrokePaint.strokeWidth = fontSize * 0.12f
        highlightStrokePaint.alpha = (220 * effectiveAlpha).toInt()

        // Origin: the grid column/row that maps to the top-left of the screen
        // before any drag. We centre the grid so there's room to pan in all directions.
        val visibleRows = (h / cellPx).toInt()
        val originCol = (grid.cols - visibleCols) / 2f
        val originRow = (grid.rows - visibleRows) / 2f

        // Pixel offset from drag
        val dragPxX = totalDragX
        val dragPxY = totalDragY

        // Half-cell margin for clipping check
        val margin = cellPx * 0.5f

        // Determine visible cell range (with 1-cell margin for partial visibility)
        val firstVisibleCol = ((-dragPxX / cellPx) + originCol - 1).toInt().coerceAtLeast(0)
        val lastVisibleCol = (firstVisibleCol + visibleCols + 3).coerceAtMost(grid.cols - 1)
        val firstVisibleRow = ((-dragPxY / cellPx) + originRow - 1).toInt().coerceAtLeast(0)
        val lastVisibleRow = (firstVisibleRow + (h / cellPx).toInt() + 3).coerceAtMost(grid.rows - 1)

        for (row in firstVisibleRow..lastVisibleRow) {
            for (col in firstVisibleCol..lastVisibleCol) {
                if (col < 0 || col >= grid.cols || row < 0 || row >= grid.rows) continue
                val digit = grid.digitAt(col, row)

                // Centre of this cell in screen pixels
                val cx = (col - originCol) * cellPx + cellPx / 2f + dragPxX
                val cy = (row - originRow) * cellPx + cellPx / 2f + dragPxY

                // Skip if fully offscreen
                if (cx < -margin || cx > w + margin || cy < -margin || cy > h + margin) continue

                // Vertical centre for text baseline
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f

                val isHighlighted = digit == highlightedDigit

                // Draw dark stroke outline first (border), then fill on top
                canvas.drawText(
                    digit.toString(), cx, textY,
                    if (isHighlighted) highlightStrokePaint else textStrokePaint
                )
                canvas.drawText(
                    digit.toString(), cx, textY,
                    if (isHighlighted) highlightTextPaint else textPaint
                )
            }
        }
    }

    // ---- Touch handling ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (numberGrid == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                // Fade in the grid when touch starts
                if (!alwaysShowDigits) {
                    removeCallbacks(fadeOutRunnable)
                    animateGridAlpha(1f)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    totalDragX += dx
                    totalDragY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y

                    onGridMoved?.invoke(totalDragX / width, totalDragY / width)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onGridReleased?.invoke(totalDragX / width, totalDragY / width)
                    // Schedule fade out after a short delay
                    if (!alwaysShowDigits) {
                        postDelayed(fadeOutRunnable, fadeOutDelayMs)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun resetDrag() {
        totalDragX = 0f
        totalDragY = 0f
        if (!alwaysShowDigits) {
            gridAlpha = 0f
            fadeAnimator?.cancel()
            removeCallbacks(fadeOutRunnable)
        }
        invalidate()
    }

    /**
     * Compute the origin row used for vertical centering of the grid.
     * This must match the value used in onDraw so the unlock verifier
     * uses the same coordinate space.
     */
    fun computeOriginRow(): Float {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return 0f
        val grid = numberGrid ?: return 0f
        val cellPx = w / visibleCols
        val visibleRows = (h / cellPx).toInt()
        return (grid.rows - visibleRows) / 2f
    }

    companion object {
        const val DEFAULT_TOLERANCE = 0.06f
    }
}
