package tv.tuberows.app

import android.content.Context
import java.time.ZoneId
import java.util.Locale

/** Remembers the tightest upload-time bound seen for each video, so labels stay accurate and stable. */
class UploadCache(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("upload_times", Context.MODE_PRIVATE)
    private val bounds = HashMap<String, UploadTimes.Bound>()
    private var dirty = false

    init {
        for ((videoId, value) in store.all) {
            val parts = (value as? String)?.split(':') ?: continue
            val ub = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val unit = parts.getOrNull(1)?.let { runCatching { UploadTimes.Unit.valueOf(it) }.getOrNull() } ?: continue
            bounds[videoId] = UploadTimes.Bound(ub, unit)
        }
    }

    /** Records what [description] says now and returns the text with a fixed upload time, or as-is. */
    fun describe(videoId: String, description: String?, now: Long): String? {
        val phrase = UploadTimes.find(description, now) ?: return description
        val best = UploadTimes.tighter(bounds[videoId], phrase.bound)
        if (best != bounds[videoId]) { bounds[videoId] = best; dirty = true }
        return UploadTimes.rewrite(description!!, phrase, UploadTimes.label(best, now, ZoneId.systemDefault(), Locale.getDefault()))
    }

    /** The label alone, for change detection (view counts in descriptions change too often). */
    fun labelFor(videoId: String, now: Long): String? =
        bounds[videoId]?.let { UploadTimes.label(it, now, ZoneId.systemDefault(), Locale.getDefault()) }

    fun save() {
        if (!dirty) return
        // Keep the newest videos; old ones fall out of every row long before this matters.
        val keep = bounds.entries.sortedByDescending { it.value.upperBound }.take(MAX_ENTRIES)
        store.edit().clear().apply {
            for ((videoId, b) in keep) putString(videoId, "${b.upperBound}:${b.unit.name}")
        }.apply()
        dirty = false
    }

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}
