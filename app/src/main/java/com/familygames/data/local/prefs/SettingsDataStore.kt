package com.familygames.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.familygames.domain.model.AgeBand
import com.familygames.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Preferences DataStore `settings` with the keys from docs/02 §2. */
class SettingsDataStore(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            if (next != current) prefs.write(next)
        }
    }

    /** Version of the built-in content already seeded (internal, not part of AppSettings). */
    suspend fun seedVersion(): Int = store.data.first()[SEED_VERSION] ?: 0

    suspend fun setSeedVersion(version: Int) {
        store.edit { it[SEED_VERSION] = version }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            firstRunCompleted = this[FIRST_RUN_COMPLETED] ?: d.firstRunCompleted,
            pinHash = this[PIN_HASH],
            pinSalt = this[PIN_SALT],
            gateLockoutUntil = this[GATE_LOCKOUT_UNTIL] ?: d.gateLockoutUntil,
            gateFailures = this[GATE_FAILURES] ?: d.gateFailures,
            gateLockoutLevel = this[GATE_LOCKOUT_LEVEL] ?: d.gateLockoutLevel,
            recoveryHash = this[RECOVERY_HASH],
            recoverySalt = this[RECOVERY_SALT],
            alwaysRequirePin = this[ALWAYS_REQUIRE_PIN] ?: d.alwaysRequirePin,
            soundEnabled = this[SOUND_ENABLED] ?: d.soundEnabled,
            showCardLabels = this[SHOW_CARD_LABELS] ?: d.showCardLabels,
            levelLocksEnabled = this[LEVEL_LOCKS_ENABLED] ?: d.levelLocksEnabled,
            lastMemoryLevel = this[LAST_MEMORY_LEVEL] ?: d.lastMemoryLevel,
            lastMemoryCategory = this[LAST_MEMORY_CATEGORY]?.takeIf { it >= 0 },
            lastPuzzleDifficulty = this[LAST_PUZZLE_DIFFICULTY] ?: d.lastPuzzleDifficulty,
            updateCheckOnStart = this[UPDATE_CHECK_ON_START] ?: d.updateCheckOnStart,
            updateWifiOnly = this[UPDATE_WIFI_ONLY] ?: d.updateWifiOnly,
            updateLastCheckAt = this[UPDATE_LAST_CHECK_AT] ?: d.updateLastCheckAt,
            updateEtag = this[UPDATE_ETAG],
            hideSystemBars = this[HIDE_SYSTEM_BARS] ?: d.hideSystemBars,
            keepScreenOn = this[KEEP_SCREEN_ON] ?: d.keepScreenOn,
            childAgeBand = this[CHILD_AGE_BAND]?.let(AgeBand::fromKey) ?: d.childAgeBand,
            hiddenGames = this[HIDDEN_GAMES] ?: d.hiddenGames,
            forcedGames = this[FORCED_GAMES] ?: d.forcedGames,
            prefixClips = this[PREFIX_CLIPS].orEmpty().mapNotNull { e -> e.split('|', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap(),
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[FIRST_RUN_COMPLETED] = s.firstRunCompleted
        setOrRemove(PIN_HASH, s.pinHash)
        setOrRemove(PIN_SALT, s.pinSalt)
        this[GATE_LOCKOUT_UNTIL] = s.gateLockoutUntil
        this[GATE_FAILURES] = s.gateFailures
        this[GATE_LOCKOUT_LEVEL] = s.gateLockoutLevel
        setOrRemove(RECOVERY_HASH, s.recoveryHash)
        setOrRemove(RECOVERY_SALT, s.recoverySalt)
        this[ALWAYS_REQUIRE_PIN] = s.alwaysRequirePin
        this[SOUND_ENABLED] = s.soundEnabled
        this[SHOW_CARD_LABELS] = s.showCardLabels
        this[LEVEL_LOCKS_ENABLED] = s.levelLocksEnabled
        this[LAST_MEMORY_LEVEL] = s.lastMemoryLevel
        this[LAST_MEMORY_CATEGORY] = s.lastMemoryCategory ?: -1L
        this[LAST_PUZZLE_DIFFICULTY] = s.lastPuzzleDifficulty
        this[UPDATE_CHECK_ON_START] = s.updateCheckOnStart
        this[UPDATE_WIFI_ONLY] = s.updateWifiOnly
        this[UPDATE_LAST_CHECK_AT] = s.updateLastCheckAt
        setOrRemove(UPDATE_ETAG, s.updateEtag)
        this[HIDE_SYSTEM_BARS] = s.hideSystemBars
        this[KEEP_SCREEN_ON] = s.keepScreenOn
        this[CHILD_AGE_BAND] = s.childAgeBand.name
        this[HIDDEN_GAMES] = s.hiddenGames
        this[FORCED_GAMES] = s.forcedGames
        this[PREFIX_CLIPS] = s.prefixClips.map { (k, v) -> "$k|$v" }.toSet()
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        const val FILE_NAME = "settings"

        private val FIRST_RUN_COMPLETED = booleanPreferencesKey("first_run_completed")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val PIN_SALT = stringPreferencesKey("pin_salt")
        private val GATE_LOCKOUT_UNTIL = longPreferencesKey("gate_lockout_until")
        private val GATE_FAILURES = intPreferencesKey("gate_failures")
        private val GATE_LOCKOUT_LEVEL = intPreferencesKey("gate_lockout_level")
        private val RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        private val RECOVERY_SALT = stringPreferencesKey("recovery_salt")
        private val ALWAYS_REQUIRE_PIN = booleanPreferencesKey("always_require_pin")
        private val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val SHOW_CARD_LABELS = booleanPreferencesKey("show_card_labels")
        private val LEVEL_LOCKS_ENABLED = booleanPreferencesKey("level_locks_enabled")
        private val LAST_MEMORY_LEVEL = stringPreferencesKey("last_memory_level")
        private val LAST_MEMORY_CATEGORY = longPreferencesKey("last_memory_category")
        private val LAST_PUZZLE_DIFFICULTY = stringPreferencesKey("last_puzzle_difficulty")
        private val UPDATE_CHECK_ON_START = booleanPreferencesKey("update_check_on_start")
        private val UPDATE_WIFI_ONLY = booleanPreferencesKey("update_wifi_only")
        private val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        private val UPDATE_ETAG = stringPreferencesKey("update_etag")
        private val HIDE_SYSTEM_BARS = booleanPreferencesKey("hide_system_bars")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val SEED_VERSION = intPreferencesKey("seed_version")
        private val CHILD_AGE_BAND = stringPreferencesKey("child_age_band")
        private val HIDDEN_GAMES = stringSetPreferencesKey("hidden_games")
        private val FORCED_GAMES = stringSetPreferencesKey("forced_games")
        private val PREFIX_CLIPS = stringSetPreferencesKey("prefix_clips")
    }
}
