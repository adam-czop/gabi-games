package com.familygames.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import timber.log.Timber

/** Device Owner admin component (docs/06 §7). */
class FamilyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        KioskManager(context).applyPolicies()
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Timber.d("Lock task entering")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Timber.d("Lock task exiting")
    }
}

data class KioskStatus(val deviceOwner: Boolean, val lockTaskActive: Boolean, val isHomeApp: Boolean)

/**
 * Kiosk mode (docs/06 §3, §5). Everything is a no-op without Device Owner, so a development device or
 * a tablet in "degraded" mode keeps working normally.
 */
class KioskManager(private val context: Context) {
    private val dpm: DevicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val activityManager: ActivityManager = context.getSystemService(ActivityManager::class.java)

    val admin = ComponentName(context, FamilyDeviceAdminReceiver::class.java)

    /** The HOME entry point (activity-alias, docs/06 §3); enabled only in kiosk builds. */
    private val homeComponent = ComponentName(context.packageName, "com.familygames.HomeAlias")

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /** The adb command from docs/06 §2 for this build (debug has the `.debug` suffix). */
    val provisioningCommand: String
        get() = "adb shell dpm set-device-owner ${context.packageName}/${FamilyDeviceAdminReceiver::class.java.name}"

    fun status(): KioskStatus = KioskStatus(
        deviceOwner = isDeviceOwner,
        lockTaskActive = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE,
        isHomeApp = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName,
    )

    fun applyPolicies() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE) // Q4: no power menu
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, homeComponent)
            RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
            dpm.setStatusBarDisabled(admin, true)
            dpm.setKeyguardDisabled(admin, true) // Q3: tablet used only by the child at home
            dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createWindowedInstallPolicy(120, 300)) // OS updates 2:00–5:00
        }.onFailure { Timber.e(it, "Applying kiosk policies failed") }
    }

    fun enterLockTask(activity: Activity) {
        if (!isDeviceOwner) return
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { activity.startLockTask() }.onFailure { Timber.e(it, "startLockTask failed") }
        }
    }

    /** "Wyjdź z kiosku" after the PIN: leave lock task and open the system settings (docs/06 §3). */
    fun exitKiosk(activity: Activity) {
        if (isDeviceOwner) {
            runCatching {
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
                dpm.setStatusBarDisabled(admin, false)
                dpm.setKeyguardDisabled(admin, false)
            }
        }
        runCatching { activity.stopLockTask() }
        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "Usuń uprawnienia Device Owner" (PIN + confirmation). */
    fun decommission(activity: Activity) {
        if (!isDeviceOwner) return
        runCatching { activity.stopLockTask() }
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
        }.onFailure { Timber.e(it, "Decommission failed") }
    }

    /**
     * Photo picker and camera live in other packages, which lock task blocks; they are allowed only while
     * the parent zone is open (docs/06 §5).
     */
    fun beginParentSession() {
        if (!isDeviceOwner) return
        val pm = context.packageManager
        val extra = listOf(
            Intent(ACTION_PICK_IMAGES), // literal: the constant needs SDK extensions on older Android versions
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*"),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        ).mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" } // "android" = the chooser, not a real handler
        runCatching { dpm.setLockTaskPackages(admin, (listOf(context.packageName) + extra).distinct().toTypedArray()) }
    }

    fun endParentSession() {
        if (!isDeviceOwner) return
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }
    }

    private companion object {
        const val ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES"
        val RESTRICTIONS = listOf(UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_ADD_USER)
    }
}
