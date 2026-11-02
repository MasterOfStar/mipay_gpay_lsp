package com.mipay.gpay.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class MiuixSwitch(context: Context, private val miuix: Boolean) : View(context) {

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            contentDescription = if (value) "on" else "off"
            invalidate()
            checkedChangeListener?.invoke(value)
        }

    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density = resources.displayMetrics.density
    private val trackRect = RectF()

    private val accentColor = when {
        miuix && isDarkMode() -> Color.parseColor("#277AF7")
        miuix -> Color.parseColor("#3482FF")
        else -> Color.parseColor("#6750A4")
    }
    private val offTrackColor = when {
        miuix && isDarkMode() -> Color.parseColor("#505050")
        miuix -> Color.parseColor("#E6E6E6")
        isDarkMode() -> Color.parseColor("#4A4458")
        else -> Color.parseColor("#E6E0E9")
    }
    private val offOutlineColor = Color.parseColor("#79747E")
    private val thumbColor = Color.WHITE

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "off"
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        checkedChangeListener = listener
    }

    fun toggle() {
        isChecked = !isChecked
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthDp = if (miuix) 49f else 52f
        setMeasuredDimension((widthDp * density).toInt(), (48 * density).toInt())
    }

    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        val trackHeight = (if (miuix) 28f else 32f) * density
        val trackTop = (height - trackHeight) / 2f
        val trackRadius = trackHeight / 2f
        trackRect.set(0f, trackTop, width.toFloat(), trackTop + trackHeight)

        trackPaint.color = if (isChecked) accentColor else offTrackColor
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)

        if (!miuix && !isChecked) {
            outlinePaint.color = offOutlineColor
            outlinePaint.strokeWidth = 2 * density
            val inset = density
            canvas.drawRoundRect(
                trackRect.left + inset,
                trackRect.top + inset,
                trackRect.right - inset,
                trackRect.bottom - inset,
                trackRadius - inset,
                trackRadius - inset,
                outlinePaint
            )
        }

        val thumbRadius = (if (miuix) 10f else if (isChecked) 12f else 8f) * density
        val padding = (if (miuix) 4f else if (isChecked) 4f else 8f) * density
        val centerX = if (isChecked) {
            width - padding - thumbRadius
        } else {
            padding + thumbRadius
        }
        val centerY = height / 2f

        thumbPaint.color = if (!miuix && !isChecked) offOutlineColor else thumbColor
        canvas.drawCircle(centerX, centerY, thumbRadius, thumbPaint)
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
