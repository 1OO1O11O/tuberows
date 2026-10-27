package tv.tuberows.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    val store: SharedPreferences =
        context.applicationContext.getSharedPreferences("tuberows", Context.MODE_PRIVATE)

    fun isEnabled(row: Config.Row): Boolean = store.getBoolean("row_${row.key}", row.defaultOn)

    fun setEnabled(row: Config.Row, on: Boolean) {
        store.edit().putBoolean("row_${row.key}", on).apply()
    }

    fun signature(row: Config.Row): String? = store.getString("sig_${row.key}", null)

    fun setSignature(row: Config.Row, value: String?) {
        store.edit().putString("sig_${row.key}", value).apply()
    }

    var watchNextEnabled: Boolean
        get() = store.getBoolean("watch_next", true)
        set(value) { store.edit().putBoolean("watch_next", value).apply() }

    var watchNextSignature: String?
        get() = store.getString("sig_watch_next", null)
        set(value) { store.edit().putString("sig_watch_next", value).apply() }

    /** SmartTube packages actually seen publishing rows; preferred targets for refresh nudges. */
    var sourcePackages: Set<String>
        get() = store.getStringSet("source_pkgs", null).orEmpty()
        set(value) { store.edit().putStringSet("source_pkgs", value).apply() }

    val lastNudge: Long get() = store.getLong("last_nudge", 0L)
    val lastNudgeReason: String get() = store.getString("last_nudge_reason", null) ?: ""

    fun recordNudge(reason: String) {
        store.edit()
            .putLong("last_nudge", System.currentTimeMillis())
            .putString("last_nudge_reason", reason)
            .apply()
    }

    /**
     * When SmartTube's data last changed. SmartTube rewrites view counts and "x ago" text on every
     * refresh, so this is effectively when SmartTube last refreshed, whoever triggered it.
     */
    val lastSourceChange: Long get() = store.getLong("last_source_change", 0L)

    /** Records the fingerprint of SmartTube's rows; returns true if it differs from last time. */
    fun recordSourceSignature(signature: String): Boolean {
        if (store.getString("source_sig", null) == signature) return false
        store.edit()
            .putString("source_sig", signature)
            .putLong("last_source_change", System.currentTimeMillis())
            .apply()
        return true
    }

    /** Consecutive incomplete writes for a row, so a provider that keeps failing isn't retried forever. */
    fun writeFailures(row: Config.Row): Int = store.getInt("fail_${row.key}", 0)

    fun setWriteFailures(row: Config.Row, count: Int) {
        store.edit().putInt("fail_${row.key}", count).apply()
    }

    val status: String get() = store.getString("status", null) ?: "Not synced yet."
    val lastRun: Long get() = store.getLong("last_run", 0L)

    fun recordRun(status: String) {
        store.edit()
            .putString("status", status)
            .putLong("last_run", System.currentTimeMillis())
            .apply()
    }
}
