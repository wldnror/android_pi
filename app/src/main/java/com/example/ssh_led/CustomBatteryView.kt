package com.example.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CustomBatteryView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var batteryLevel: Int = 100
    private val paint = Paint()

    fun setBatteryLevel(level: Int) {
        batteryLevel = level
        invalidate()  // View를 다시 그리도록 함
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 10f
        val top = 10f
        val right = width - 30f
        val bottom = height - 10f

        // 배터리 테두리 그리기
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRect(left, top, right, bottom, paint)

        // 배터리 안쪽 그리기
        paint.style = Paint.Style.FILL
        val batteryWidth = (right - left - 10) * (batteryLevel / 100f)
        paint.color = when {
            batteryLevel > 75 -> Color.GREEN
            batteryLevel > 50 -> Color.YELLOW
            batteryLevel > 25 -> Color.rgb(255, 165, 0) // 주황색
            else -> Color.RED
        }
        canvas.drawRect(left + 5, top + 5, left + 5 + batteryWidth, bottom - 5, paint)

        // 배터리 끝부분(포트) 그리기
        val portWidth = 20f
        val portHeight = (bottom - top) / 2
        val portLeft = right
        val portTop = (height / 2) - (portHeight / 2)
        val portRight = right + portWidth
        val portBottom = portTop + portHeight

        paint.color = Color.BLACK
        canvas.drawRect(portLeft, portTop, portRight, portBottom, paint)

        // 배터리 잔량 텍스트 그리기
        paint.color = Color.BLACK
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        val text = "$batteryLevel%"
        val textWidth = paint.measureText(text)
        val textX = (left + right - textWidth) / 2
        val textY = (top + bottom - (paint.descent() + paint.ascent())) / 2
        canvas.drawText(text, textX, textY, paint)
    }
}
