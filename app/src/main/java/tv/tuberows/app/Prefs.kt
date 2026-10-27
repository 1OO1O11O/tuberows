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

    val status: String get() = store.getString("status", null) ?: "Not synced yet."
    val lastRun: Long get() = store.getLong("last_run", 0L)

    fun recordRun(status: String) {
        store.edit()
            .putString("status", status)
            .putLong("last_run", System.currentTimeMillis())
            .apply()
    }
}
