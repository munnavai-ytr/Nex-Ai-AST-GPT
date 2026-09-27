package com.example.accessibility.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GestureResult(
    val success: Boolean,
    val message: String,
    val durationMs: Long = 0L
)

class GestureExecutor {

    suspend fun executeTap(
        service: AccessibilityService?,
        x: Float,
        y: Float
    ): GestureResult {
        if (service == null) {
            return GestureResult(false, "Accessibility Service is not connected")
        }

        if (x < 0 || y < 0) {
            return GestureResult(false, "Invalid tap coordinates: ($x, $y)")
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSuspending(service, gesture, "Tap at ($x, $y)")
    }

    suspend fun executeLongPress(
        service: AccessibilityService?,
        x: Float,
        y: Float,
        durationMs: Long = 700L
    ): GestureResult {
        if (service == null) {
            return GestureResult(false, "Accessibility Service is not connected")
        }

        if (x < 0 || y < 0) {
            return GestureResult(false, "Invalid coordinates for long press: ($x, $y)")
        }

        val clampedDuration = durationMs.coerceIn(300L, 3000L)
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSuspending(service, gesture, "Long press at ($x, $y)")
    }

    suspend fun executeSwipe(
        service: AccessibilityService?,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): GestureResult {
        if (service == null) {
            return GestureResult(false, "Accessibility Service is not connected")
        }

        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return GestureResult(false, "Invalid swipe coordinates: from ($startX, $startY) to ($endX, $endY)")
        }

        val clampedDuration = durationMs.coerceIn(100L, 2000L)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSuspending(
            service,
            gesture,
            "Swipe from ($startX, $startY) to ($endX, $endY)"
        )
    }

    private suspend fun dispatchGestureSuspending(
        service: AccessibilityService,
        gesture: GestureDescription,
        description: String
    ): GestureResult {
        val startTime = System.currentTimeMillis()

        val result = withTimeoutOrNull(2500L) {
            suspendCancellableCoroutine<GestureResult> { continuation ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                GestureResult(
                                    success = true,
                                    message = "$description completed successfully",
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            )
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                GestureResult(
                                    success = false,
                                    message = "$description was cancelled by the system",
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            )
                        }
                    }
                }

                val handler = Handler(Looper.getMainLooper())
                try {
                    val dispatched = service.dispatchGesture(gesture, callback, handler)
                    if (!dispatched) {
                        if (continuation.isActive) {
                            continuation.resume(
                                GestureResult(
                                    success = false,
                                    message = "Failed to dispatch gesture: system rejected gesture description",
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(
                            GestureResult(
                                success = false,
                                message = "Gesture dispatch exception: ${e.message}",
                                durationMs = System.currentTimeMillis() - startTime
                            )
                        )
                    }
                }
            }
        }

        return result ?: GestureResult(
            success = false,
            message = "$description timed out waiting for system confirmation",
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
