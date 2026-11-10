package com.familygames.data.updater

import com.familygames.domain.repository.SettingsRepository
import com.familygames.domain.update.UpdateDecision
import com.familygames.domain.update.UpdateInfo
import com.familygames.domain.update.UpdateRules
import com.familygames.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File

enum class UpdateError { Network, NoUpdateFile, BadUpdateFile, Verification, DeviceTooOld, Install }

/** docs/07 §6. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Downloading(val info: UpdateInfo, val bytes: Long, val total: Long) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Error(val reason: UpdateError, val detail: String? = null) : UpdateState
}

/**
 * Checks GitHub Releases, downloads and verifies the APK, and installs it only when [UpdateRules.canInstallNow]
 * allows (docs/07). One operation at a time.
 */
class UpdateManager(
    private val api: GitHubReleaseApi,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val updatesDir: File,
    private val currentVersionCode: Long,
    private val deviceSdk: Int,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** Removes leftovers of older downloads (at start, docs/02 §3). */
    fun cleanCache() {
        if (mutableState.value !is UpdateState.ReadyToInstall) updatesDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun checkOnStartIfDue() {
        val s = settings.settings.first()
        if (UpdateRules.shouldCheckOnStart(s.updateCheckOnStart, s.updateLastCheckAt, clock.now())) check()
    }

    /** Check, and download + verify a newer version. Returns the resulting state. */
    suspend fun check(): UpdateState = mutex.withLock {
        val current = mutableState.value
        if (current is UpdateState.ReadyToInstall && current.file.exists()) return current
        mutableState.value = UpdateState.Checking
        val result = runCatching { checkAndDownload() }.getOrElse { e ->
            Timber.w(e, "Update check failed")
            UpdateState.Error(UpdateError.Network, e.message)
        }
        mutableState.value = result
        result
    }

    private suspend fun checkAndDownload(): UpdateState {
        val prefs = settings.settings.first()
        val now = clock.now()
        val release = when (val r = api.latestRelease(prefs.updateEtag)) {
            ReleaseResult.NotModified, ReleaseResult.NoRelease -> {
                settings.update { it.copy(updateLastCheckAt = now) }
                return UpdateState.UpToDate(now)
            }
            is ReleaseResult.Found -> r
        }
        val jsonAsset = release.release.assets.firstOrNull { it.name == UPDATE_JSON } ?: return UpdateState.Error(UpdateError.NoUpdateFile)
        val info = runCatching { UpdateInfo.parse(api.downloadText(jsonAsset.downloadUrl)) }.getOrElse {
            return UpdateState.Error(UpdateError.BadUpdateFile, it.message)
        }
        when (UpdateRules.decide(currentVersionCode, info, deviceSdk)) {
            UpdateDecision.UpToDate -> {
                // Only an "up to date" answer may be cached: a newer release must be fetched in full next time.
                settings.update { it.copy(updateLastCheckAt = now, updateEtag = release.etag) }
                return UpdateState.UpToDate(now)
            }
            UpdateDecision.DeviceTooOld -> return UpdateState.Error(UpdateError.DeviceTooOld)
            UpdateDecision.Update -> Unit
        }
        settings.update { it.copy(updateLastCheckAt = now, updateEtag = null) }
        val apkAsset = release.release.assets.firstOrNull { it.name == info.apkAsset } ?: return UpdateState.Error(UpdateError.NoUpdateFile)
        updatesDir.listFiles()?.forEach { it.delete() }
        val apk = File(updatesDir, info.apkAsset)
        mutableState.value = UpdateState.Downloading(info, 0, info.apkSize)
        api.download(apkAsset.downloadUrl, apk) { bytes -> mutableState.value = UpdateState.Downloading(info, bytes, info.apkSize) }
        val error = verifier.verify(apk, info)
        if (error != null) {
            apk.delete()
            return UpdateState.Error(UpdateError.Verification, error.name)
        }
        return UpdateState.ReadyToInstall(info, apk)
    }

    /** Starts the install when allowed (docs/07 §5). Returns true if it started. */
    fun installIfReady(onMenu: Boolean, idleMs: Long, parentRequested: Boolean, hourOfDay: Int, appInBackground: Boolean): Boolean {
        val ready = mutableState.value as? UpdateState.ReadyToInstall ?: return false
        if (!UpdateRules.canInstallNow(onMenu, idleMs, parentRequested, hourOfDay, appInBackground)) return false
        mutableState.value = UpdateState.Installing(ready.info)
        return runCatching { installer.install(ready.file); true }.getOrElse {
            Timber.e(it, "Install failed to start")
            mutableState.value = UpdateState.Error(UpdateError.Install, it.message)
            false
        }
    }

    fun onInstallFailed() {
        (mutableState.value as? UpdateState.Installing)?.let { mutableState.value = UpdateState.Error(UpdateError.Install) }
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val UPDATE_JSON = "update.json"
    }
}
