package com.example.accessibility.model

data class ScreenBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun contains(x: Int, y: Int): Boolean {
        return x in left..right && y in top..bottom
    }

    override fun toString(): String = "[$left, $top, $right, $bottom]"
}
