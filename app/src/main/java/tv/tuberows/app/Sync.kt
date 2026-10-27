package tv.tuberows.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_FROM_TRIGGER = "from_trigger"
        const val KEY_PERIODIC = "periodic"
    }

    override fun doWork(): Result {
        try {
            Mirror.run(applicationContext)
        } catch (t: Throwable) {
            Log.e(Config.TAG, "Sync failed", t)
            Prefs(applicationContext).recordRun("Sync failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        // Every run doubles as a freshness check: after wake (overdue jobs) and hourly (periodic job),
        // refresh SmartTube if the screen is on and its last refresh is over 30 minutes old.
        SmartTube.catchUp(applicationContext, if (inputData.getBoolean(KEY_PERIODIC, false)) "hourly" else "catch-up")
        // Content triggers fire once, so the triggered run re-arms the next one.
        if (inputData.getBoolean(KEY_FROM_TRIGGER, false)) Scheduler.armTrigger(applicationContext, chained = true)
        // Always succeed: a failed run is recorded on the status screen, not retried in a loop.
        return Result.success()
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        SmartTube.nudgeNow(applicationContext, inputData.getString(SmartTube.KEY_REASON) ?: "scheduled")
        return Result.success()
    }
}

object Scheduler {
    private const val PERIODIC = "tuberows-periodic-v2"
    private const val LEGACY_PERIODIC = "tuberows-periodic"
    private const val TRIGGER = "tuberows-trigger"
    private const val NOW = "tuberows-now"

    fun ensure(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_PERIODIC)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setInputData(workDataOf(SyncWorker.KEY_PERIODIC to true))
                .build(),
        )
        armTrigger(context, chained = false)
    }

    /**
     * Re-sync shortly after SmartTube rewrites its cards. Our own writes also fire this,
     * but the next run sees an unchanged signature and writes nothing, so it settles.
     */
    fun armTrigger(context: Context, chained: Boolean) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(TvContractCompat.PreviewPrograms.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(20))
            .setTriggerContentMaxDelay(Duration.ofMinutes(2))
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_FROM_TRIGGER to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TRIGGER,
            if (chained) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().build(),
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // SmartTube refreshes itself on boot; we just make sure our jobs exist and mirror.
        Scheduler.ensure(context)
        Scheduler.syncNow(context)
    }
}
