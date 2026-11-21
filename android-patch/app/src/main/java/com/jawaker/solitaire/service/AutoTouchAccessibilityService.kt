package com.jawaker.solitaire.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jawaker.solitaire.model.BotMove
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Real Android accessibility service used to issue taps and drags over the game. */
class AutoTouchAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    suspend fun performMove(move: BotMove, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val sx = (move.startX.coerceIn(0f, 1f) * screenWidth).coerceIn(1f, (screenWidth - 2).toFloat())
        val sy = (move.startY.coerceIn(0f, 1f) * screenHeight).coerceIn(1f, (screenHeight - 2).toFloat())
        val ex = (move.endX.coerceIn(0f, 1f) * screenWidth).coerceIn(1f, (screenWidth - 2).toFloat())
        val ey = (move.endY.coerceIn(0f, 1f) * screenHeight).coerceIn(1f, (screenHeight - 2).toFloat())

        val path = Path().apply {
            moveTo(sx, sy)
            if (move.type == BotMove.Type.STOCK_TAP) {
                lineTo(sx + 1f, sy + 1f)
            } else {
                lineTo(ex, ey)
            }
        }
        val duration = if (move.type == BotMove.Type.STOCK_TAP) 90L else 420L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
            if (!accepted && continuation.isActive) continuation.resume(false)
        }
    }

    companion object {
        private const val TAG = "SolitaireAutoTouch"
        @Volatile var active: AutoTouchAccessibilityService? = null
            private set
    }
}
