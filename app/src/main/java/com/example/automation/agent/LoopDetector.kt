package com.example.automation.agent

import com.example.automation.Action
import com.example.automation.ActionType

data class ActionSignatureEntry(
    val actionType: ActionType,
    val targetKey: String,
    val screenSignature: ScreenSignature,
    val timestamp: Long = System.currentTimeMillis()
)

class LoopDetector(
    private val loopThreshold: Int = 2
) {
    private val history = mutableListOf<ActionSignatureEntry>()

    fun recordAction(action: Action, signature: ScreenSignature) {
        val target = action.parameters.targetText
            ?: action.parameters.viewId
            ?: action.parameters.packageName
            ?: action.parameters.appName
            ?: action.description

        history.add(
            ActionSignatureEntry(
                actionType = action.type,
                targetKey = target,
                screenSignature = signature
            )
        )

        // Keep last 10 actions
        if (history.size > 10) {
            history.removeAt(0)
        }
    }

    /**
     * Checks if the recent actions and screen states form an infinite loop.
     * Returns true if repeated identical actions failed to change screen state.
     */
    fun isLoopDetected(): Boolean {
        if (history.size < loopThreshold + 1) return false

        val latest = history.last()
        var repeatedCount = 0

        for (i in (history.size - 2) downTo 0) {
            val entry = history[i]
            if (entry.actionType == latest.actionType &&
                entry.targetKey == latest.targetKey &&
                !entry.screenSignature.hasChangedFrom(latest.screenSignature)
            ) {
                repeatedCount++
                if (repeatedCount >= loopThreshold) {
                    return true
                }
            } else {
                break
            }
        }

        return false
    }

    fun clear() {
        history.clear()
    }
}
