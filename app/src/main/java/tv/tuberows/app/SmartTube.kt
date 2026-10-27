package tv.tuberows.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Asks SmartTube to refresh its home rows. Its exported UpdateChannelsReceiver re-enqueues its
 * channel worker with CANCEL_AND_REENQUEUE, which runs at once; when SmartTube rewrites its
 * cards, our content trigger mirrors them about 20 seconds later.
 */
object SmartTube {
    private const val WORK = "tuberows-smarttube-refresh"
    const val KEY_REASON = "reason"

    /**
     * Throttled request: runs no sooner than [minDelayMs] from now and no sooner than
     * [Config.NUDGE_GAP_MS] after the last refresh. A pending request is kept, not replaced,
     * so bursts of card opens coalesce into one refresh.
     */
    fun request(context: Context, reason: String, minDelayMs: Long = 0L) {
        val now = System.currentTimeMillis()
        val earliest = maxOf(now + minDelayMs, Prefs(context).lastNudge + Config.NUDGE_GAP_MS)
        val delay = (earliest - now).coerceAtLeast(0L)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_REASON to reason))
                .build(),
        )
    }

    /** Sends the broadcast now unless the gap hasn't passed. [force] skips the gap (manual button). */
    fun nudgeNow(context: Context, reason: String, force: Boolean = false): Boolean {
        val prefs = Prefs(context)
        if (!force && System.currentTimeMillis() - prefs.lastNudge < Config.NUDGE_GAP_MS - 5_000L) {
            Log.i(Config.TAG, "SmartTube refresh skipped ($reason): within ${Config.NUDGE_GAP_MS / 60_000} min gap")
            return false
        }
        val targets = prefs.sourcePackages.ifEmpty { installedPackages(context) }
        if (targets.isEmpty()) {
            Log.w(Config.TAG, "SmartTube refresh skipped ($reason): no SmartTube install found")
            return false
        }
        for (pkg in targets) {
            val intent = Intent(Config.NUDGE_ACTION)
                .setClassName(pkg, Config.SMARTTUBE_RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            runCatching { context.sendBroadcast(intent) }
                .onFailure { Log.w(Config.TAG, "Refresh broadcast to $pkg failed", it) }
        }
        prefs.recordNudge(reason)
        Log.i(Config.TAG, "SmartTube refresh requested ($reason) -> $targets")
        return true
    }

    fun installedPackages(context: Context): Set<String> =
        Config.SMARTTUBE_PACKAGES.filter { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }.toSet()
}
