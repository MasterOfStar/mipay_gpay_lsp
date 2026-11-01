package com.mipay.gpay.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class MiuixSwitch(context: Context) : View(context) {

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
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val accentColor = Color.parseColor("#3478F6")
    private val offTrackColor: Int
    private val thumbColor = Color.WHITE

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "off"
        offTrackColor = if (isDarkMode()) Color.parseColor("#48484C") else Color.parseColor("#E6E6EA")
        shadowPaint.color = Color.parseColor("#1F000000")
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        checkedChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension((52 * density).toInt(), (32 * density).toInt())
    }

    override fun performClick(): Boolean {
        isChecked = !isChecked
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val trackHeight = height.toFloat()
        val trackRadius = trackHeight / 2f
        val thumbRadius = 13 * density
        val padding = 4 * density

        trackPaint.color = if (isChecked) accentColor else offTrackColor
        canvas.drawRoundRect(0f, 0f, width.toFloat(), trackHeight, trackRadius, trackRadius, trackPaint)

        val centerX = if (isChecked) width - padding - thumbRadius else padding + thumbRadius
        val centerY = height / 2f
        if (!isChecked) {
            canvas.drawCircle(centerX, centerY + density, thumbRadius, shadowPaint)
        }
        thumbPaint.color = thumbColor
        canvas.drawCircle(centerX, centerY, thumbRadius, thumbPaint)
    }

    private fun isDarkMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
