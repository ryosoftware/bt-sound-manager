package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

interface InterruptModeRepository {
    fun canAccessPolicy(): Boolean

    fun currentMode(): InterruptMode?

    fun setMode(mode: InterruptMode): Boolean

    /**
     * Whether the platform only lets the app contribute its own Do Not Disturb rule
     * (Android 15 and later) instead of setting the global mode.
     */
    val usesOwnRule: Boolean get() = false

    /**
     * Activates the app's rule for [mode], or deactivates the app's rules for
     * [InterruptMode.NORMAL]. Restrictions from the user or other apps remain.
     */
    fun applyOwnRule(mode: InterruptMode): OwnRuleResult =
        OwnRuleResult(OwnRuleStatus.FAILED)
}

enum class OwnRuleStatus {
    CHANGED,
    UNCHANGED,
    /** The user has disabled the rule in the system settings. */
    DISABLED,
    FAILED,
}

data class OwnRuleResult(
    val status: OwnRuleStatus,
    val ruleId: String? = null,
)
