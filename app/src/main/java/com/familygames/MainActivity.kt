package com.familygames

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.familygames.domain.repository.SettingsRepository
import com.familygames.kiosk.KioskManager
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.navigation.FamilyNavHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale

private val APP_LOCALE: Locale = Locale.forLanguageTag("pl-PL")

/** Single activity (docs/01 §1): landscape, edge-to-edge, immersive, kiosk hooks (docs/06). */
class MainActivity : ComponentActivity() {

    private val kiosk: KioskManager by inject()
    private val settings: SettingsRepository by inject()
    private var hideSystemBars = true

    // The app is Polish only (Q10); without this a tablet set to English picks English plural rules
    // for the Polish strings ("6 karty" instead of "6 kart").
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(APP_LOCALE) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applySystemBars()
        // After a crash the picker/camera could still be allowed in lock task: always start closed (docs/06 §5).
        kiosk.endParentSession()
        if (BuildConfig.KIOSK_ENABLED) kiosk.applyPolicies()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.settings.distinctUntilChanged().collect { s ->
                    hideSystemBars = s.hideSystemBars
                    applySystemBars()
                    if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // "Remove animations" in accessibility settings sets the animator scale to 0 (docs/08 §5).
        val reducedMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) { FamilyNavHost() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.KIOSK_ENABLED) kiosk.enterLockTask(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the photo picker or a transient swipe bring the bars back; hide them again on return.
        if (hasFocus) applySystemBars()
    }

    private fun applySystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideSystemBars) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
