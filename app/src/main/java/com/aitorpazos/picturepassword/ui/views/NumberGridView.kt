package com.aitorpazos.picturepassword.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aitorpazos.picturepassword.model.NumberGrid

/**
 * Custom view that renders the draggable number grid overlay.
 * Digits 0-9 are displayed as circles with numbers that the user can drag as a group.
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

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 30, 30, 30)
        style = Paint.Style.FILL
    }

    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 120, 255)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 60, 60)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 60, 60)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        val radius = minOf(w, h) * 0.04f
        textPaint.textSize = radius * 1.2f

        // Draw target point if in setup mode
        if (showTargetPoint) {
            val tx = targetPointX * w
            val ty = targetPointY * h
            val targetRadius = minOf(w, h) * PicturePasswordConfigCompanion.DEFAULT_TOLERANCE
            canvas.drawCircle(tx, ty, targetRadius, targetFillPaint)
            canvas.drawCircle(tx, ty, targetRadius, targetPaint)
            canvas.drawCircle(tx, ty, 6f, targetPaint)
        }

        val grid = numberGrid ?: return

        // Normalized offset from drag
        val offsetX = totalDragX / w
        val offsetY = totalDragY / h

        for (cell in grid.cellPositions) {
            val cx = (cell.normalizedX + offsetX) * w
            val cy = (cell.normalizedY + offsetY) * h

            // Skip if offscreen
            if (cx < -radius || cx > w + radius || cy < -radius || cy > h + radius) continue

            val paint = if (cell.digit == highlightedDigit) highlightPaint else circlePaint
            canvas.drawCircle(cx, cy, radius, paint)
            canvas.drawCircle(cx, cy, radius, circleStrokePaint)

            // Draw digit centered in circle
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(cell.digit.toString(), cx, textY, textPaint)
        }
    }

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

    private object PicturePasswordConfigCompanion {
        const val DEFAULT_TOLERANCE = 0.06f
    }
}
