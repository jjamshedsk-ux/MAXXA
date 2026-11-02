package com.maxxa.ai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility bridge for a deliberately limited set of global navigation actions.
 * No window-content access, gestures, text entry, message reading, or background automation.
 */
class MaxxaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally do not inspect or retain events or screen contents.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeService: MaxxaAccessibilityService? = null

        fun isEnabled(): Boolean = activeService != null

        /** Executes only Android global actions explicitly requested by the foreground UI. */
        fun perform(action: PhoneAction): Boolean {
            val service = activeService ?: return false
            val globalAction = when (action) {
                PhoneAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
                PhoneAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
                PhoneAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
                PhoneAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                PhoneAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            }
            return runCatching { service.performGlobalAction(globalAction) }.getOrDefault(false)
        }
    }
}

enum class PhoneAction { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }
