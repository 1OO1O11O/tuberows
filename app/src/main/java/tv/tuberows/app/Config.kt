package tv.tuberows.app

object Config {
    const val TAG = "TubeRows"
    const val TIZENTUBE = "io.gh.reisxd.tizentube.cobalt"
    const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    /** Safety cap only: SmartTube publishes up to ~4 pages per row; the launcher decides how many to show. */
    const val MAX_CARDS = 100

    // SmartTube has shipped under several package names over the years.
    private val SOURCE_HINTS = listOf("smarttube", "teamsmart", "liskovsoft")

    /** Known SmartTube builds, used before any row has been seen. Also declared in <queries>. */
    val SMARTTUBE_PACKAGES = listOf(
        "org.smarttube.stable", "org.smarttube.beta", "app.smarttube.fdroid", "app.smarttube",
        "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv",
    )

    /** SmartTube's exported receiver: any broadcast to it refreshes its rows immediately. */
    const val SMARTTUBE_RECEIVER = "com.liskovsoft.leanbackassistant.channels.UpdateChannelsReceiver"
    const val NUDGE_ACTION = "tv.tuberows.app.action.REFRESH"

    /** At most one SmartTube refresh per this gap; its own job also runs every 15 minutes. */
    const val NUDGE_GAP_MS = 10 * 60_000L
    /** After a video is opened, wait so it has landed in YouTube history before refreshing. */
    const val VIDEO_NUDGE_DELAY_MS = 2 * 60_000L
    /** Any run while the screen is on refreshes SmartTube if its last refresh is older than this (wake catch-up). */
    const val CATCH_UP_AFTER_MS = 30 * 60_000L
    /** Status screen warns when no row has changed for this long. */
    const val STALE_WARNING_MS = 24 * 60 * 60_000L

    const val WATCH_NEXT_CONTINUE_COUNT = 3
    const val WATCH_NEXT_NEW_COUNT = 5

    fun isSourcePackage(pkg: String?): Boolean =
        pkg != null && SOURCE_HINTS.any { pkg.contains(it, ignoreCase = true) }

    /**
     * SmartTube's rows. Matched by the internal provider ID it gives each channel, or, as a fallback,
     * by the system channel key it also sets (both from SmartTube's ClipService).
     */
    enum class Row(
        val key: String,
        val sourceProviderId: String,
        val sourceChannelKey: String,
        val label: String,
        val defaultOn: Boolean,
    ) {
        SUBSCRIPTIONS("subs", "1", "subs_channel_id", "Subscriptions", true),
        RECOMMENDED("recommended", "3", "recommended_channel_id", "Recommended", true),
        HISTORY("history", "2", "history_channel_id", "History", false);

        val providerId: String get() = "tuberows-$key"

        companion object {
            fun forSource(providerId: String?, channelKey: String?): Row? =
                entries.firstOrNull { it.sourceProviderId == providerId }
                    ?: entries.firstOrNull { channelKey != null && it.sourceChannelKey == channelKey }

            fun forOwn(providerId: String?): Row? = entries.firstOrNull { it.providerId == providerId }
        }
    }
}
