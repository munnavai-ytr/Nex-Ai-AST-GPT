package com.example.accessibility.tree

import com.example.accessibility.model.ScreenElement
import kotlin.math.hypot

data class ElementSearchQuery(
    val query: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val mustBeClickable: Boolean? = null,
    val mustBeEditable: Boolean? = null,
    val mustBeScrollable: Boolean? = null,
    val targetX: Int? = null,
    val targetY: Int? = null
)

object ElementFinder {

    fun findByExactText(elements: List<ScreenElement>, text: String): List<ScreenElement> {
        val target = text.trim()
        return elements.filter {
            !it.isPassword && (it.text?.equals(target, ignoreCase = false) == true)
        }
    }

    fun findByPartialText(elements: List<ScreenElement>, text: String): List<ScreenElement> {
        val target = text.trim().lowercase()
        return elements.filter {
            !it.isPassword && (it.text?.lowercase()?.contains(target) == true)
        }
    }

    fun findByContentDescription(elements: List<ScreenElement>, desc: String): List<ScreenElement> {
        val target = desc.trim().lowercase()
        return elements.filter {
            !it.isPassword && (it.contentDescription?.lowercase()?.contains(target) == true)
        }
    }

    fun findByResourceId(elements: List<ScreenElement>, resId: String): List<ScreenElement> {
        val target = resId.trim().lowercase()
        return elements.filter {
            it.resourceId?.lowercase()?.contains(target) == true
        }
    }

    fun findByClassName(elements: List<ScreenElement>, className: String): List<ScreenElement> {
        val target = className.trim().lowercase()
        return elements.filter {
            it.className.lowercase().contains(target)
        }
    }

    fun findClickable(elements: List<ScreenElement>): List<ScreenElement> {
        return elements.filter { it.clickable && it.enabled }
    }

    fun findEditable(elements: List<ScreenElement>): List<ScreenElement> {
        return elements.filter { it.editable && it.enabled }
    }

    fun findScrollable(elements: List<ScreenElement>): List<ScreenElement> {
        return elements.filter { it.scrollable && it.enabled }
    }

    fun findNearest(elements: List<ScreenElement>, x: Int, y: Int): ScreenElement? {
        return elements
            .filter { it.enabled && (it.clickable || it.editable) }
            .minByOrNull { elem ->
                hypot((elem.bounds.centerX - x).toDouble(), (elem.bounds.centerY - y).toDouble())
            }
    }

    /**
     * Multi-strategy best-effort matcher.
     * Evaluates text, contentDescription, resourceId, and interactive state.
     */
    fun findBestMatch(elements: List<ScreenElement>, query: String): ScreenElement? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null

        // 1. Exact text match on interactive element
        val exactInteractive = elements.firstOrNull {
            !it.isPassword && it.clickable && it.text?.equals(query.trim(), ignoreCase = true) == true
        }
        if (exactInteractive != null) return exactInteractive

        // 2. Exact text match on any element
        val exactAny = elements.firstOrNull {
            !it.isPassword && it.text?.equals(query.trim(), ignoreCase = true) == true
        }
        if (exactAny != null) return exactAny

        // 3. Exact content description match on interactive element
        val exactDescInteractive = elements.firstOrNull {
            !it.isPassword && it.clickable && it.contentDescription?.equals(query.trim(), ignoreCase = true) == true
        }
        if (exactDescInteractive != null) return exactDescInteractive

        // 4. Partial text match on interactive element
        val partialInteractive = elements.firstOrNull {
            !it.isPassword && it.clickable && it.text?.lowercase()?.contains(q) == true
        }
        if (partialInteractive != null) return partialInteractive

        // 5. Partial content description on interactive element
        val partialDesc = elements.firstOrNull {
            !it.isPassword && it.clickable && it.contentDescription?.lowercase()?.contains(q) == true
        }
        if (partialDesc != null) return partialDesc

        // 6. Resource ID match (e.g., "search_button" matching "search")
        val resIdMatch = elements.firstOrNull {
            it.clickable && it.resourceId?.lowercase()?.contains(q) == true
        }
        if (resIdMatch != null) return resIdMatch

        // 7. Fallback to any element containing text
        val anyText = elements.firstOrNull {
            !it.isPassword && it.text?.lowercase()?.contains(q) == true
        }
        if (anyText != null) return anyText

        // 8. Fallback to any element containing content description
        return elements.firstOrNull {
            !it.isPassword && it.contentDescription?.lowercase()?.contains(q) == true
        }
    }
}
