package com.familygames.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `update.json` attached to every GitHub Release by CI (docs/07 §1). Unknown fields are ignored. */
@Serializable
data class UpdateInfo(
    val schema: Int = 1,
    val versionCode: Long,
    val versionName: String,
    val apkAsset: String,
    val apkSize: Long,
    val sha256: String,
    val minSdk: Int = 28,
    val mandatory: Boolean = false,
    val changelog: List<String> = emptyList(),
    @SerialName("publishedAt") val publishedAt: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): UpdateInfo = json.decodeFromString(serializer(), text).also {
            require(it.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "sha256 must be 64 hex characters" }
            require(it.apkSize > 0) { "apkSize must be positive" }
        }
    }
}

enum class UpdateDecision { Update, UpToDate, DeviceTooOld }

/** docs/07 §4: only strictly newer builds, never downgrades; the device must meet minSdk. */
object UpdateRules {
    fun decide(currentVersionCode: Long, info: UpdateInfo, deviceSdk: Int): UpdateDecision = when {
        info.versionCode <= currentVersionCode -> UpdateDecision.UpToDate
        deviceSdk < info.minSdk -> UpdateDecision.DeviceTooOld
        else -> UpdateDecision.Update
    }

    /** Start-up check at most every 6 h (docs/07 §3). */
    fun shouldCheckOnStart(enabled: Boolean, lastCheckAt: Long, now: Long): Boolean =
        enabled && now - lastCheckAt >= 6 * 60 * 60 * 1000L

    /**
     * Never interrupt a game (docs/07 §5): install on the menu after 10 s without a touch, when the parent
     * asked for it, or at night (01:00–05:00) while the app is in the background.
     */
    fun canInstallNow(onMenu: Boolean, idleMs: Long, parentRequested: Boolean, hourOfDay: Int, appInBackground: Boolean): Boolean =
        parentRequested || (onMenu && idleMs >= 10_000) || (appInBackground && hourOfDay in 1..4)
}
