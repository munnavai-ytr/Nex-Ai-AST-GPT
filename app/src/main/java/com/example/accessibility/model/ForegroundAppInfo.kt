package com.example.accessibility.model

data class ForegroundAppInfo(
    val packageName: String,
    val displayName: String?,
    val timestamp: Long = System.currentTimeMillis()
)
