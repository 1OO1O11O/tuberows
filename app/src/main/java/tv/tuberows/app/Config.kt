package tv.tuberows.app

object Config {
    const val TAG = "TubeRows"
    const val TIZENTUBE = "io.gh.reisxd.tizentube.cobalt"
    const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    const val MAX_CARDS = 40

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

    const val WATCH_NEXT_CONTINUE_COUNT = 3
    const val WATCH_NEXT_NEW_COUNT = 5

    fun isSourcePackage(pkg: String?): Boolean =
        pkg != null && SOURCE_HINTS.any { pkg.contains(it, ignoreCase = true) }

    /** SmartTube's rows, keyed by the internal provider ID it gives each channel. */
    enum class Row(val key: String, val sourceProviderId: String, val label: String, val defaultOn: Boolean) {
        SUBSCRIPTIONS("subs", "1", "Subscriptions", true),
        RECOMMENDED("recommended", "3", "Recommended", true),
        HISTORY("history", "2", "History", false);

        val providerId: String get() = "tuberows-$key"

        companion object {
            fun forSource(providerId: String?): Row? = entries.firstOrNull { it.sourceProviderId == providerId }
            fun forOwn(providerId: String?): Row? = entries.firstOrNull { it.providerId == providerId }
        }
    }
}
