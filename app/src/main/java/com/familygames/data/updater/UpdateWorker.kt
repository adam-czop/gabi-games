package com.familygames.data.updater

import android.app.ActivityManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Periodic check (docs/07 §3); installs only at night while the app is not on screen (docs/07 §5). */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val manager: UpdateManager by inject()

    override suspend fun doWork(): Result {
        val state = manager.check()
        if (state is UpdateState.Error && state.reason == UpdateError.Network) return Result.retry()
        manager.installIfReady(
            onMenu = false,
            idleMs = 0,
            parentRequested = false,
            hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            appInBackground = !isAppInForeground(),
        )
        return Result.success()
    }

    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}

/** (Re)schedules [UpdateWorker] with the Wi-Fi-only constraint from settings. */
class UpdateScheduler(private val context: Context, private val settings: SettingsRepository) {
    suspend fun schedule() {
        val wifiOnly = settings.settings.first().updateWifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "update-check"
    }
}
