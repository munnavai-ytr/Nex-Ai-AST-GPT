package com.example.automation.agent

import com.example.accessibility.model.ScreenSnapshot

/**
 * ScreenSignature provides a lightweight, privacy-preserving signature of the UI.
 * It uses hashes and structural counts instead of storing sensitive raw text or values.
 */
data class ScreenSignature(
    val packageName: String,
    val elementCount: Int,
    val interactiveCount: Int,
    val structureHash: Int,
    val textHashSample: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun from(snapshot: ScreenSnapshot?): ScreenSignature {
            if (snapshot == null) {
                return ScreenSignature(
                    packageName = "unknown",
                    elementCount = 0,
                    interactiveCount = 0,
                    structureHash = 0,
                    textHashSample = 0
                )
            }

            var structHash = snapshot.packageName.hashCode()
            var textHash = 0
            var interactive = 0

            for (elem in snapshot.elements) {
                if (elem.isInteractive) interactive++
                structHash = 31 * structHash + elem.className.hashCode()
                elem.resourceId?.let { structHash = 31 * structHash + it.hashCode() }

                // Safe hash of visible text without storing raw text
                if (!elem.isPassword && !elem.text.isNullOrBlank()) {
                    textHash = 31 * textHash + elem.text.hashCode()
                }
            }

            return ScreenSignature(
                packageName = snapshot.packageName,
                elementCount = snapshot.elementCount,
                interactiveCount = interactive,
                structureHash = structHash,
                textHashSample = textHash,
                timestamp = snapshot.timestamp
            )
        }
    }

    /**
     * Determines whether the screen has genuinely changed between observations.
     */
    fun hasChangedFrom(previous: ScreenSignature?): Boolean {
        if (previous == null) return true
        if (this.packageName != previous.packageName) return true
        if (this.structureHash != previous.structureHash) return true
        if (this.textHashSample != previous.textHashSample) return true
        if (this.elementCount != previous.elementCount) return true
        return false
    }

    /**
     * Computes similarity between two signatures [0.0 - 1.0].
     */
    fun similarity(other: ScreenSignature): Float {
        if (packageName != other.packageName) return 0.0f
        var matches = 1.0f // package matched
        val total = 4.0f

        if (elementCount == other.elementCount) matches += 1.0f
        if (interactiveCount == other.interactiveCount) matches += 1.0f
        if (structureHash == other.structureHash) matches += 1.0f

        return matches / total
    }
}
