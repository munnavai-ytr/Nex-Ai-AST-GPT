package com.example.accessibility.model

data class ScreenSnapshot(
    val packageName: String,
    val displayName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val elements: List<ScreenElement> = emptyList(),
    val isRedacted: Boolean = true
) {
    val elementCount: Int get() = elements.size
    val interactiveElementCount: Int get() = elements.count { it.isInteractive }
}

sealed class ScreenSnapshotResult {
    data class Success(val snapshot: ScreenSnapshot) : ScreenSnapshotResult()
    data class Error(val errorCode: String, val message: String) : ScreenSnapshotResult()
}
