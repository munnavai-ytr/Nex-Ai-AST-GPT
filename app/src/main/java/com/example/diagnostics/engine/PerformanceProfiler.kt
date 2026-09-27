package com.example.diagnostics.engine

import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult

class PerformanceProfiler {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Dalvik / ART Heap Footprint
        val runtime = Runtime.getRuntime()
        val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemoryMb = totalMemoryMb - freeMemoryMb
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)

        val heapPressure = if (maxMemoryMb > 0) (usedMemoryMb.toFloat() / maxMemoryMb.toFloat()) else 0f
        val heapStatus = if (heapPressure > 0.85f) DiagnosticStatus.WARN else DiagnosticStatus.PASS

        items.add(
            DiagnosticItem(
                id = "perf_heap_memory",
                title = "App Heap Footprint & Garbage Collector Pressure",
                category = DiagnosticCategory.PERFORMANCE,
                status = heapStatus,
                summary = "Allocated: ${usedMemoryMb}MB / Max Heap: ${maxMemoryMb}MB (${(heapPressure * 100).toInt()}% load)",
                details = "Used Memory: ${usedMemoryMb}MB\nFree Allocated: ${freeMemoryMb}MB\nTotal VM Heap: ${totalMemoryMb}MB\nMax Allowed VM Heap: ${maxMemoryMb}MB"
            )
        )

        // 2. Active Thread Concurrency
        val threadGroup = Thread.currentThread().threadGroup
        val activeThreads = threadGroup?.activeCount() ?: 1
        items.add(
            DiagnosticItem(
                id = "perf_thread_pool",
                title = "Coroutine & Thread Execution Pool",
                category = DiagnosticCategory.PERFORMANCE,
                status = if (activeThreads < 60) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = "$activeThreads Active Execution Threads (Normal Concurrency)",
                details = "Active Thread Count: $activeThreads\nDispatchers: Main, IO, Default, Unconfined"
            )
        )

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.PERFORMANCE,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
