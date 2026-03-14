package com.aitorpazos.picturepassword.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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

    var onGridMoved: ((offsetX: Float, offsetY: Float) -> Unit)? = null
    var onGridReleased: ((offsetX: Float, offsetY: Float) -> Unit)? = null

    /** Highlight a specific digit (used during setup to show selected number) */
    var highlightedDigit: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    /** Show a target point indicator (used during setup) */
    var showTargetPoint: Boolean = false
    var targetPointX: Float = 0f
    var targetPointY: Float = 0f

    private var totalDragX = 0f
    private var totalDragY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // ---- Paints ----

    /** Semi-transparent dark circle behind each digit */
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 20, 20, 20)
        style = Paint.Style.FILL
    }

    /** White stroke around each circle for contrast */
    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    /** Highlighted digit circle (blue) */
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 120, 255)
        style = Paint.Style.FILL
    }

    /** Digit text — white, bold, centred */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    /** Target point ring (setup mode) */
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 60, 60)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Target point fill (setup mode) */
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 60, 60)
        style = Paint.Style.FILL
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Draw target point if in setup mode
        if (showTargetPoint) {
            val tx = targetPointX * w
            val ty = targetPointY * h
            val targetRadius = minOf(w, h) * DEFAULT_TOLERANCE
            canvas.drawCircle(tx, ty, targetRadius, targetFillPaint)
            canvas.drawCircle(tx, ty, targetRadius, targetPaint)
            canvas.drawCircle(tx, ty, 6f, targetPaint)
        }

        val grid = numberGrid ?: return

        // Cell size in pixels: screen width divided by visible columns
        val cellPx = w / NumberGridFactory.VISIBLE_COLS

        // Circle radius inside each cell
        val radius = cellPx * 0.36f
        textPaint.textSize = radius * 1.3f

        // Origin: the grid column/row that maps to the top-left of the screen
        // before any drag. We centre the grid so there's room to pan in all directions.
        val originCol = (grid.cols - NumberGridFactory.VISIBLE_COLS) / 2f
        val originRow = 0f  // start from top; grid is tall enough to extend below screen

        // Pixel offset from drag
        val dragPxX = totalDragX
        val dragPxY = totalDragY

        // Determine visible cell range (with 1-cell margin for partial visibility)
        val firstVisibleCol = ((- dragPxX / cellPx) + originCol - 1).toInt().coerceAtLeast(0)
        val lastVisibleCol = (firstVisibleCol + NumberGridFactory.VISIBLE_COLS + 3).coerceAtMost(grid.cols - 1)
        val firstVisibleRow = ((- dragPxY / cellPx) + originRow - 1).toInt().coerceAtLeast(0)
        val lastVisibleRow = (firstVisibleRow + (h / cellPx).toInt() + 3).coerceAtMost(grid.rows - 1)

        for (row in firstVisibleRow..lastVisibleRow) {
            for (col in firstVisibleCol..lastVisibleCol) {
                val digit = grid.digitAt(col, row)

                // Centre of this cell in screen pixels
                val cx = (col - originCol) * cellPx + cellPx / 2f + dragPxX
                val cy = (row - originRow) * cellPx + cellPx / 2f + dragPxY

                // Skip if fully offscreen
                if (cx < -radius || cx > w + radius || cy < -radius || cy > h + radius) continue

                val bgPaint = if (digit == highlightedDigit) highlightPaint else circlePaint
                canvas.drawCircle(cx, cy, radius, bgPaint)
                canvas.drawCircle(cx, cy, radius, circleStrokePaint)

                // Draw digit centred
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(digit.toString(), cx, textY, textPaint)
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

                    // Report normalised offset (fraction of screen)
                    onGridMoved?.invoke(totalDragX / width, totalDragY / height)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onGridReleased?.invoke(totalDragX / width, totalDragY / height)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun resetDrag() {
        totalDragX = 0f
        totalDragY = 0f
        invalidate()
    }

    companion object {
        const val DEFAULT_TOLERANCE = 0.06f
    }
}
