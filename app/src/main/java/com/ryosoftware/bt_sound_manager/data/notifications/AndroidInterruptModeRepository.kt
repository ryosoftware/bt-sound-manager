package com.ryosoftware.bt_sound_manager.data.notifications

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import com.ryosoftware.bt_sound_manager.MainActivity
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.labelRes
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.OwnRuleResult
import com.ryosoftware.bt_sound_manager.domain.repository.OwnRuleStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidInterruptModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InterruptModeRepository {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    override fun canAccessPolicy(): Boolean = try {
        manager?.isNotificationPolicyAccessGranted == true
    } catch (_: SecurityException) {
        false
    }

    override fun currentMode(): InterruptMode? {
        if (!canAccessPolicy()) return null
        return try {
            when (manager?.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> InterruptMode.NORMAL
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> InterruptMode.DO_NOT_DISTURB
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> InterruptMode.ALARMS_ONLY
                NotificationManager.INTERRUPTION_FILTER_NONE -> InterruptMode.SILENT_ALL
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    override fun setMode(mode: InterruptMode): Boolean {
        if (!canAccessPolicy()) return false
        return try {
            manager?.setInterruptionFilter(mode.interruptionFilter())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override val usesOwnRule: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    override fun applyOwnRule(mode: InterruptMode): OwnRuleResult {
        val notificationManager = manager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            notificationManager == null ||
            !canAccessPolicy()
        ) {
            return OwnRuleResult(OwnRuleStatus.FAILED)
        }
        return try {
            applyRule(notificationManager, mode)
        } catch (_: SecurityException) {
            OwnRuleResult(OwnRuleStatus.FAILED)
        } catch (_: IllegalArgumentException) {
            OwnRuleResult(OwnRuleStatus.FAILED)
        }
    }

    /**
     * There is one rule per restrictive mode, created when first needed, so that a
     * rule never has to be modified (the user may have customised it).
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun applyRule(
        manager: NotificationManager,
        mode: InterruptMode,
    ): OwnRuleResult {
        // Releases the implicit rule that setInterruptionFilter() created in
        // earlier versions of the app. It does not affect any other rule.
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        val rules = manager.automaticZenRules
        val conditionId = conditionId(mode)
        var changed = false
        rules.forEach { (id, rule) ->
            if (rule.conditionId != conditionId &&
                manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
            ) {
                manager.setRuleState(id, rule.conditionId, Condition.STATE_FALSE)
                changed = true
            }
        }
        if (mode == InterruptMode.NORMAL) return result(changed)

        val existing = rules.entries.firstOrNull { it.value.conditionId == conditionId }
        if (existing != null && !existing.value.isEnabled) {
            return OwnRuleResult(OwnRuleStatus.DISABLED, existing.key)
        }
        val id = existing?.key
            ?: manager.addAutomaticZenRule(newRule(mode, conditionId))
            ?: return OwnRuleResult(OwnRuleStatus.FAILED)

        // A rule that reports itself active while the system is less restrictive
        // has been turned off by the user.
        val effective = currentMode()
        val isActive = manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE &&
            effective != null &&
            effective.ordinal >= mode.ordinal
        if (isActive) return result(changed, id)

        // After the user turns a rule off, Android ignores it until it is reported
        // as inactive and then active again.
        manager.setRuleState(id, conditionId, Condition.STATE_FALSE)
        manager.setRuleState(id, conditionId, Condition.STATE_TRUE)
        return if (manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE) {
            result(changed = true, id)
        } else {
            OwnRuleResult(OwnRuleStatus.FAILED, id)
        }
    }

    private fun result(changed: Boolean, ruleId: String? = null) = OwnRuleResult(
        status = if (changed) OwnRuleStatus.CHANGED else OwnRuleStatus.UNCHANGED,
        ruleId = ruleId,
    )

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun NotificationManager.setRuleState(id: String, conditionId: Uri, state: Int) {
        setAutomaticZenRuleState(
            id,
            Condition(conditionId, context.getString(R.string.app_name), state),
        )
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun newRule(mode: InterruptMode, conditionId: Uri) = AutomaticZenRule(
        "${context.getString(R.string.app_name)}: ${context.getString(mode.labelRes())}",
        null,
        ComponentName(context, MainActivity::class.java),
        conditionId,
        null,
        mode.interruptionFilter(),
        true,
    )

    private fun conditionId(mode: InterruptMode): Uri = Uri.Builder()
        .scheme(Condition.SCHEME)
        .authority(context.packageName)
        .appendPath(mode.storageValue)
        .build()

    private fun InterruptMode.interruptionFilter(): Int = when (this) {
        InterruptMode.NORMAL -> NotificationManager.INTERRUPTION_FILTER_ALL
        InterruptMode.DO_NOT_DISTURB -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        InterruptMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        InterruptMode.SILENT_ALL -> NotificationManager.INTERRUPTION_FILTER_NONE
    }
}
