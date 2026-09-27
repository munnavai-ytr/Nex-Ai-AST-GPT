package com.example.automation.state

import android.content.Context
import com.example.accessibility.NexAccessibilityService
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.accessibility.inspector.UIHierarchySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScreenTransitionType {
    WINDOW_CHANGED,
    APP_FOREGROUNDED,
    CONTENT_UPDATED,
    SCROLLED,
    NONE
}

data class ScreenTransitionEvent(
    val type: ScreenTransitionType,
    val packageName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val totalElements: Int = 0
)

data class ScreenState(
    val activePackage: String? = null,
    val isAccessibilityActive: Boolean = false,
    val elementCount: Int = 0,
    val interactiveCount: Int = 0,
    val editableCount: Int = 0,
    val scrollableCount: Int = 0,
    val lastUpdatedTimestamp: Long = 0L,
    val snapshot: UIHierarchySnapshot? = null
)

class ScreenStateManager(
    private val context: Context,
    private val uiInspector: UIHierarchyInspector,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _transitionEvents = MutableSharedFlow<ScreenTransitionEvent>(replay = 5)
    val transitionEvents: SharedFlow<ScreenTransitionEvent> = _transitionEvents.asSharedFlow()

    fun updateFromAccessibilityEvent(eventType: Int, packageName: String?) {
        scope.launch {
            val isServiceActive = NexAccessibilityService.isServiceConnected()
            val previousPackage = _screenState.value.activePackage

            val snapshot = if (isServiceActive) {
                uiInspector.inspectCurrentScreen()
            } else {
                null
            }

            val currentPkg = packageName ?: snapshot?.packageName ?: previousPackage

            val transitionType = when {
                previousPackage != currentPkg -> ScreenTransitionType.APP_FOREGROUNDED
                eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ScreenTransitionType.WINDOW_CHANGED
                eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED -> ScreenTransitionType.SCROLLED
                else -> ScreenTransitionType.CONTENT_UPDATED
            }

            val newState = ScreenState(
                activePackage = currentPkg,
                isAccessibilityActive = isServiceActive,
                elementCount = snapshot?.totalElements ?: 0,
                interactiveCount = snapshot?.interactiveCount ?: 0,
                editableCount = snapshot?.editableCount ?: 0,
                scrollableCount = snapshot?.scrollableCount ?: 0,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                snapshot = snapshot
            )

            _screenState.value = newState

            _transitionEvents.emit(
                ScreenTransitionEvent(
                    type = transitionType,
                    packageName = currentPkg,
                    totalElements = newState.elementCount
                )
            )
        }
    }

    fun captureFreshSnapshot(): UIHierarchySnapshot {
        val snapshot = uiInspector.inspectCurrentScreen()
        val isServiceActive = NexAccessibilityService.isServiceConnected()
        _screenState.value = _screenState.value.copy(
            activePackage = snapshot.packageName ?: _screenState.value.activePackage,
            isAccessibilityActive = isServiceActive,
            elementCount = snapshot.totalElements,
            interactiveCount = snapshot.interactiveCount,
            editableCount = snapshot.editableCount,
            scrollableCount = snapshot.scrollableCount,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            snapshot = snapshot
        )
        return snapshot
    }
}
