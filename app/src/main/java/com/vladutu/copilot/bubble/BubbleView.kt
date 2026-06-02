package com.vladutu.copilot.bubble

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.vladutu.copilot.R
import com.vladutu.copilot.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class BubbleView(
    private val context: Context,
    private val onPositionChanged: (x: Int, y: Int) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view: ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_bubble)
    }
    private val sizePx = (64 * context.resources.displayMetrics.density).toInt()
    private val slopPx = (12 * context.resources.displayMetrics.density).toInt()
    private val tapTimeoutMs = 300L

    private val layoutParams = WindowManager.LayoutParams(
        sizePx, sizePx,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 200
    }

    private var downX = 0f
    private var downY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var downTime = 0L
    private var dragging = false

    init {
        view.setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun show(initialX: Int? = null, initialY: Int? = null) {
        initialX?.let { layoutParams.x = it }
        initialY?.let { layoutParams.y = it }
        try { windowManager.addView(view, layoutParams) } catch (_: Exception) { /* already added */ }
    }

    fun hide() {
        try { windowManager.removeView(view) } catch (_: Exception) {}
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY
                startWindowX = layoutParams.x; startWindowY = layoutParams.y
                downTime = System.currentTimeMillis()
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && (abs(dx) > slopPx || abs(dy) > slopPx)) dragging = true
                if (dragging) {
                    layoutParams.x = (startWindowX + dx).roundToInt()
                    layoutParams.y = (startWindowY + dy).roundToInt()
                    try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - downTime
                if (!dragging && dt <= tapTimeoutMs) {
                    openMainActivity()
                } else if (dragging) {
                    onPositionChanged(layoutParams.x, layoutParams.y)
                }
                return true
            }
        }
        return false
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_SHOW_HOME, true)
        }
        context.startActivity(intent)
    }
}
