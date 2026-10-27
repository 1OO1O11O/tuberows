package tv.tuberows.app

import android.content.Context
import java.time.ZoneId
import java.util.Locale

/** Remembers the tightest upload-time bound seen for each video, so labels stay accurate and stable. */
class UploadCache(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("upload_times", Context.MODE_PRIVATE)
    private val bounds = HashMap<String, UploadTimes.Bound>()
    private val seen = HashSet<String>()
    private var dirty = false

    init {
        for ((videoId, value) in store.all) {
            val parts = (value as? String)?.split(':') ?: continue
            val ub = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val unit = parts.getOrNull(1)?.let { runCatching { UploadTimes.Unit.valueOf(it) }.getOrNull() } ?: continue
            bounds[videoId] = UploadTimes.Bound(ub, unit, exact = parts.getOrNull(2) == "1")
        }
    }

    /** Records what [description] says now and returns the text with a fixed upload time, or as-is. */
    fun describe(videoId: String, description: String?, now: Long): String? {
        seen += videoId
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
        // Keep every video still in a row (an old History video must not lose its bound and come back
        // looser next sync, changing its label), then the newest of the rest.
        val keep = bounds.entries
            .sortedWith(compareByDescending<Map.Entry<String, UploadTimes.Bound>> { it.key in seen }
                .thenByDescending { it.value.upperBound })
            .take(MAX_ENTRIES)
        store.edit().clear().apply {
            for ((videoId, b) in keep) putString(videoId, "${b.upperBound}:${b.unit.name}:${if (b.exact) 1 else 0}")
        }.apply()
        dirty = false
    }

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}
