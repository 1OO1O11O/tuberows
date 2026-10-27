package tv.tuberows.app

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import java.security.MessageDigest

/**
 * Copies SmartTube's home-screen rows into rows owned by this app, with every card
 * pointing at [PlayActivity] so the video opens in TizenTube.
 *
 * TvProvider rule that shapes this code: an app without ACCESS_ALL_EPG_DATA may not pass
 * a selection when querying, so whole tables are read and filtered here.
 */
object Mirror {

    private object Col {
        const val ID = "_id"
        const val PACKAGE = "package_name"
        const val TYPE = "type"
        const val PROVIDER_ID = "internal_provider_id"
        const val SYSTEM_KEY = "system_channel_key"
        const val CHANNEL_ID = "channel_id"
        const val INTENT_URI = "intent_uri"
        const val TITLE = "title"
        const val DESCRIPTION = "short_description"
        const val POSTER = "poster_art_uri"
        const val DURATION = "duration_millis"
        const val LIVE = "live"
        const val WEIGHT = "weight"
    }

    // TvContract values, inlined: tvprovider kept PreviewProgramColumns restricted for years.
    private const val TYPE_PREVIEW = "TYPE_PREVIEW"
    private const val TYPE_CLIP = 4
    private const val ASPECT_RATIO_16_9 = 0
    private const val WATCH_NEXT_CONTINUE = 0
    private const val WATCH_NEXT_NEW = 2
    private const val MAX_WRITE_RETRIES = 3
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val VIDEO_ID_IN_TEXT = Regex("(?:[?&]v=|youtu\\.be/|/vi(?:_webp)?/|/shorts/)([A-Za-z0-9_-]{11})")

    data class Outcome(val ok: Boolean, val summary: String)

    data class Card(
        val videoId: String,
        val title: String?,
        val description: String?,
        val poster: String?,
        val durationMs: Long,
        val live: Boolean,
        val weight: Int,
    )

    private data class Source(val channelId: Long, val pkg: String, val row: Config.Row)

    /** One of our own cards as it is in the provider now, keyed by video ID for in-place updates. */
    private data class Existing(val id: Long, val videoId: String?, val values: Map<String, String?>)

    /** Columns compared to decide whether an existing card needs updating. Numbers compare as numbers. */
    private val COMPARED_TEXT = listOf(Col.TITLE, Col.DESCRIPTION, Col.POSTER, Col.INTENT_URI)
    private val COMPARED_NUMBERS = listOf(Col.WEIGHT, Col.LIVE, Col.DURATION)

    // Periodic, trigger and "now" workers can run in parallel on WorkManager's pool;
    // serialise them so two runs never both create the same row.
    @Synchronized
    fun run(context: Context): Outcome {
        val prefs = Prefs(context)
        val resolver = context.contentResolver
        val ownPackage = context.packageName
        val now = System.currentTimeMillis()

        val sources = mutableListOf<Source>()
        val own = mutableMapOf<Config.Row, Long>()
        val visible = sortedMapOf<String, Int>()

        // 1. Channels: SmartTube's (searchable rows we're allowed to see) plus our own.
        val channels = try {
            resolver.query(TvContractCompat.Channels.CONTENT_URI, null, null, null, null)
        } catch (e: SecurityException) {
            return finish(prefs, false, "No access to TV rows. Open TizenTube Rows and press Grant access.", e)
        } ?: return finish(prefs, false, "TV provider unavailable on this device.")

        channels.use { c ->
            val iId = c.getColumnIndex(Col.ID)
            val iPkg = c.getColumnIndex(Col.PACKAGE)
            val iType = c.getColumnIndex(Col.TYPE)
            val iProvider = c.getColumnIndex(Col.PROVIDER_ID)
            val iKey = c.getColumnIndex(Col.SYSTEM_KEY)
            while (c.moveToNext()) {
                val pkg = c.str(iPkg) ?: continue
                visible[pkg] = (visible[pkg] ?: 0) + 1
                val id = c.getLong(iId)
                val providerId = c.str(iProvider)
                when {
                    pkg == ownPackage -> {
                        val row = Config.Row.forOwn(providerId)
                        if (row == null || own.containsKey(row)) {
                            // Orphan or duplicate from an earlier run: remove it.
                            resolver.delete(TvContractCompat.buildChannelUri(id), null, null)
                        } else {
                            own[row] = id
                        }
                    }
                    Config.isSourcePackage(pkg) && c.str(iType) == TYPE_PREVIEW -> {
                        Config.Row.forSource(providerId, c.str(iKey))?.let { sources += Source(id, pkg, it) }
                    }
                }
            }
        }

        // 2. Programs: group SmartTube's cards by channel, and count ours.
        val sourceIds = sources.associateBy { it.channelId }
        val ownIds = own.values.toSet()
        val cards = mutableMapOf<Long, MutableList<Card>>()
        val existing = mutableMapOf<Long, MutableList<Existing>>()
        var programsSeen = 0
        var skipped = 0

        val programs = try {
            resolver.query(TvContractCompat.PreviewPrograms.CONTENT_URI, null, null, null, null)
        } catch (e: SecurityException) {
            return finish(prefs, false, "No access to TV cards. Open TizenTube Rows and press Grant access.", e)
        } ?: return finish(prefs, false, "TV provider returned no cursor for cards.")

        programs.use { c ->
            val iChannel = c.getColumnIndex(Col.CHANNEL_ID)
            val iIntent = c.getColumnIndex(Col.INTENT_URI)
            val iTitle = c.getColumnIndex(Col.TITLE)
            val iDesc = c.getColumnIndex(Col.DESCRIPTION)
            val iPoster = c.getColumnIndex(Col.POSTER)
            val iDuration = c.getColumnIndex(Col.DURATION)
            val iLive = c.getColumnIndex(Col.LIVE)
            val iWeight = c.getColumnIndex(Col.WEIGHT)
            val iId = c.getColumnIndex(Col.ID)
            val iProvider = c.getColumnIndex(Col.PROVIDER_ID)
            while (c.moveToNext()) {
                programsSeen++
                val channelId = c.getLong(iChannel)
                if (channelId in ownIds) {
                    val snapshot = (COMPARED_TEXT + COMPARED_NUMBERS).associateWith { c.str(c.getColumnIndex(it)) }
                    existing.getOrPut(channelId) { mutableListOf() } += Existing(c.getLong(iId), c.str(iProvider), snapshot)
                    continue
                }
                if (channelId !in sourceIds) continue
                val poster = c.str(iPoster)
                val videoId = videoIdFrom(c.str(iIntent)) ?: videoIdFrom(poster)
                if (videoId == null) { skipped++; continue }
                cards.getOrPut(channelId) { mutableListOf() } += Card(
                    videoId = videoId,
                    title = c.str(iTitle),
                    description = c.str(iDesc),
                    poster = poster,
                    durationMs = c.long(iDuration),
                    live = c.long(iLive) == 1L,
                    weight = c.long(iWeight).toInt(),
                )
            }
        }

        if (sources.isNotEmpty()) prefs.sourcePackages = sources.map { it.pkg }.toSet()

        // If more than one SmartTube build is installed, use the fuller row. Ordered by SmartTube's weight.
        val best = mutableMapOf<Config.Row, Pair<Source, List<Card>>>()
        val sourceCounts = mutableListOf<String>()
        for (row in Config.Row.entries) {
            val source = sources.filter { it.row == row }.maxByOrNull { cards[it.channelId]?.size ?: 0 } ?: continue
            val unique = cards[source.channelId].orEmpty().sortedByDescending { it.weight }.distinctBy { it.videoId }
            sourceCounts += "${row.label}=${unique.size}"
            best[row] = source to unique.take(Config.MAX_CARDS)
        }

        Log.i(
            Config.TAG,
            "Visible channel packages=$visible; SmartTube rows=${sources.map { "${it.row.label}@${it.pkg}" }}; " +
                "source cards (before cap ${Config.MAX_CARDS}): ${sourceCounts.joinToString()}; " +
                "cards seen=$programsSeen; unparsable=$skipped"
        )

        // Fingerprint SmartTube's raw rows (descriptions included: their view counts and "x ago" text change
        // on every SmartTube refresh), so we know when SmartTube last refreshed, whoever triggered it.
        if (best.isNotEmpty()) {
            val raw = best.entries.joinToString("\n") { (row, pair) ->
                pair.second.joinToString("\n") { "${row.key}|${it.videoId}|${it.title}|${it.description}" }
            }
            if (prefs.recordSourceSignature(sha256(raw))) Log.i(Config.TAG, "SmartTube data changed")
        }

        // Cards show SmartTube's own description ("Channel • 1.2K views • 11 minutes ago").
        val shown = HashMap<String, String?>()
        for ((_, list) in best.values) for (card in list) shown.putIfAbsent(card.videoId, card.description)

        // 3. Reconcile each row.
        val lines = mutableListOf<String>()
        for (row in Config.Row.entries) {
            val ownId = own[row]
            if (!prefs.isEnabled(row)) {
                if (ownId != null) {
                    resolver.delete(TvContractCompat.buildChannelUri(ownId), null, null)
                    prefs.setSignature(row, null)
                }
                lines += "${row.label}: off"
                continue
            }

            val source = best[row]?.first
            val list = best[row]?.second.orEmpty()

            if (source == null || list.isEmpty()) {
                // SmartTube may be mid-refresh (it wipes then refills). Keep what we have.
                lines += "${row.label}: no SmartTube cards" + if (ownId != null) " (kept previous)" else ""
                continue
            }

            val signature = signatureOf(list)
            val sameContent = signature == prefs.signature(row)
            val failures = if (sameContent) prefs.writeFailures(row) else 0
            val ownCount = ownId?.let { existing[it]?.size } ?: 0
            if (ownId != null && sameContent && (ownCount == list.size || failures >= MAX_WRITE_RETRIES)) {
                lines += "${row.label}: ${list.size} cards (unchanged)"
                continue
            }

            val channelId = ownId ?: createChannel(context, row)
            val result = reconcile(context, channelId, list, source.pkg, shown, existing[channelId].orEmpty())
            prefs.setSignature(row, signature)
            if (result != null) {
                prefs.setWriteFailures(row, 0)
                lines += "${row.label}: ${list.size} cards ($result)"
            } else {
                // Our write fires the content trigger, so the next sync retries within seconds; stop after a few
                // tries so a provider that keeps refusing isn't hammered. New content gets a fresh set of tries.
                prefs.setWriteFailures(row, failures + 1)
                lines += "${row.label}: write failed" +
                    if (failures + 1 >= MAX_WRITE_RETRIES) ", giving up until the row changes" else ", will retry"
            }
        }

        // 4. Watch Next: recent history to continue, plus new subscription uploads.
        lines += runCatching { syncWatchNext(context, prefs, best, shown, now) }
            .getOrElse { Log.e(Config.TAG, "Watch Next sync failed", it); "Watch Next: failed (${it.javaClass.simpleName})" }

        val summary = lines.joinToString("\n")
        val ok = sources.isNotEmpty()
        val header = if (ok) "Synced from ${sources.map { it.pkg }.distinct().joinToString()}" else
            "SmartTube rows not visible. Check SmartTube is signed in and its Channels service is on."
        return finish(prefs, ok, "$header\n$summary")
    }

    fun videoIdFrom(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val fromIntent = runCatching {
            Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).data?.getQueryParameter("v")
        }.getOrNull()
        if (fromIntent != null && VIDEO_ID.matches(fromIntent)) return fromIntent
        return VIDEO_ID_IN_TEXT.find(raw)?.groupValues?.get(1)
    }

    /**
     * What a card shows, minus view counts (which change constantly). The "x ago" wording is included,
     * so it stays within one SmartTube refresh of correct instead of freezing until the videos change.
     */
    private fun signatureOf(list: List<Card>): String =
        sha256(list.joinToString("\n") { "${it.videoId}|${it.title}|${it.live}|${agoOf(it.description)}" })

    private val AGO = Regex("\\d+[\\s\\u00A0\\u202F]+\\p{L}+[\\s\\u00A0\\u202F]+ago\\b", RegexOption.IGNORE_CASE)

    /** The "11 minutes ago" part of SmartTube's description, if it has one in English. */
    private fun agoOf(description: String?): String? = description?.let { AGO.find(it)?.value }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun createChannel(context: Context, row: Config.Row): Long {
        val pm = context.packageManager
        val appLink = pm.getLeanbackLaunchIntentForPackage(Config.TIZENTUBE)
            ?: pm.getLaunchIntentForPackage(Config.TIZENTUBE)
            ?: Intent(context, MainActivity::class.java)

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(row.label)
            .setAppLinkIntent(appLink)
            .setInternalProviderId(row.providerId)
            .build()

        val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error("TV provider refused to create the ${row.label} row")
        val id = ContentUris.parseId(uri)

        runCatching { ChannelLogoUtils.storeChannelLogo(context, id, logo(context)) }
            .onFailure { Log.w(Config.TAG, "Logo not stored for ${row.label}", it) }
        // No requestChannelBrowsable: Google TV ignores it and Projectivy lists rows itself.
        Log.i(Config.TAG, "Created row ${row.label} (channel $id)")
        return id
    }

    /**
     * Brings a row's cards in line with [list] in place: inserts new videos, deletes removed ones, and
     * updates only cards whose text, order or details changed. Unchanged cards keep their identity, so
     * launchers don't redraw the whole row. Returns a short summary, or null if the provider refused.
     */
    private fun reconcile(
        context: Context, channelId: Long, list: List<Card>, sourcePkg: String,
        shown: Map<String, String?>, current: List<Existing>,
    ): String? {
        val resolver = context.contentResolver
        val ops = ArrayList<ContentProviderOperation>()
        val byVideo = HashMap<String, Existing>()
        for (e in current) {
            // Cards without a video ID, or duplicates, can't be matched: remove them.
            if (e.videoId == null || e.videoId in byVideo) ops += delete(e.id) else byVideo[e.videoId] = e
        }
        val wanted = list.mapTo(HashSet()) { it.videoId }
        var removed = ops.size
        for ((videoId, e) in byVideo) if (videoId !in wanted) { ops += delete(e.id); removed++ }

        var added = 0
        var updated = 0
        list.forEachIndexed { index, card ->
            val values = buildProgram(context, channelId, card, shown[card.videoId], sourcePkg, weight = list.size - index)
                .toContentValues()
            val e = byVideo[card.videoId]
            when {
                e == null -> {
                    ops += ContentProviderOperation.newInsert(TvContractCompat.PreviewPrograms.CONTENT_URI).withValues(values).build()
                    added++
                }
                differs(e, values) -> {
                    ops += ContentProviderOperation.newUpdate(TvContractCompat.buildPreviewProgramUri(e.id)).withValues(values).build()
                    updated++
                }
            }
        }
        val summary = "$added new, $updated updated, $removed removed"
        if (ops.isEmpty()) return "no changes"

        try {
            resolver.applyBatch(TvContractCompat.AUTHORITY, ops)
            Log.i(Config.TAG, "Channel $channelId: $summary")
            return summary
        } catch (e: Exception) {
            // Some TV builds may not accept batches; fall back to replacing the row.
            Log.w(Config.TAG, "Batch update refused for channel $channelId, rewriting the row", e)
        }
        resolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
        val values: Array<ContentValues> = list.mapIndexed { index, card ->
            buildProgram(context, channelId, card, shown[card.videoId], sourcePkg, weight = list.size - index).toContentValues()
        }.toTypedArray()
        val inserted = resolver.bulkInsert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
        Log.i(Config.TAG, "Rewrote $inserted/${values.size} cards to channel $channelId")
        return if (inserted == values.size) "rewritten" else null
    }

    private fun delete(programId: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(TvContractCompat.buildPreviewProgramUri(programId)).build()

    /** True if what we'd write differs from what the card holds now. */
    private fun differs(e: Existing, values: ContentValues): Boolean =
        COMPARED_TEXT.any { (values.getAsString(it) ?: "") != (e.values[it] ?: "") } ||
            COMPARED_NUMBERS.any { (values.getAsString(it)?.toLongOrNull() ?: 0L) != (e.values[it]?.toLongOrNull() ?: 0L) }

    private fun syncWatchNext(
        context: Context, prefs: Prefs, best: Map<Config.Row, Pair<Source, List<Card>>>,
        shown: Map<String, String?>, now: Long,
    ): String {
        val resolver = context.contentResolver
        val ownIds = mutableListOf<Long>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
            val iId = c.getColumnIndex(Col.ID)
            val iPkg = c.getColumnIndex(Col.PACKAGE)
            while (c.moveToNext()) if (c.str(iPkg) == context.packageName) ownIds += c.getLong(iId)
        }

        if (!prefs.watchNextEnabled) {
            ownIds.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
            prefs.watchNextSignature = null
            return "Watch Next: off"
        }

        val history = best[Config.Row.HISTORY]
        val subs = best[Config.Row.SUBSCRIPTIONS]
        val continueCards = history?.second.orEmpty().take(Config.WATCH_NEXT_CONTINUE_COUNT)
        val seen = continueCards.map { it.videoId }.toSet()
        val newCards = subs?.second.orEmpty().filter { it.videoId !in seen }.take(Config.WATCH_NEXT_NEW_COUNT)
        val entries = continueCards.map { Triple(it, WATCH_NEXT_CONTINUE, history?.first?.pkg.orEmpty()) } +
            newCards.map { Triple(it, WATCH_NEXT_NEW, subs?.first?.pkg.orEmpty()) }

        if (entries.isEmpty()) {
            return "Watch Next: no SmartTube cards" + if (ownIds.isNotEmpty()) " (kept previous)" else ""
        }
        val signature = sha256(entries.joinToString("\n") {
            "${it.second}|${it.first.videoId}|${it.first.title}|${agoOf(it.first.description)}"
        })
        if (signature == prefs.watchNextSignature && ownIds.size == entries.size) {
            return "Watch Next: ${entries.size} (unchanged)"
        }

        ownIds.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
        val values: Array<ContentValues> = entries.mapIndexed { index, (card, type, pkg) ->
            // Engagement time sets the order launchers show; keep SmartTube's order.
            buildWatchNext(context, card, shown[card.videoId], type, pkg, engagedAt = now - index * 60_000L).toContentValues()
        }.toTypedArray()
        val inserted = resolver.bulkInsert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
        prefs.watchNextSignature = if (inserted == values.size) signature else null
        Log.i(Config.TAG, "Wrote $inserted/${values.size} Watch Next entries")
        return "Watch Next: ${continueCards.size} continue, ${newCards.size} new"
    }

    private fun buildWatchNext(
        context: Context, card: Card, description: String?, type: Int, sourcePkg: String, engagedAt: Long,
    ): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
        builder.setWatchNextType(type)
        builder.setLastEngagementTimeUtcMillis(engagedAt)
        builder.setType(TYPE_CLIP)
        builder.setTitle(card.title ?: "YouTube video")
        description?.let { builder.setDescription(it) }
        builder.setPosterArtUri(Uri.parse(posterFor(card)))
        builder.setPosterArtAspectRatio(ASPECT_RATIO_16_9)
        builder.setIntent(openIntent(context, card.videoId, sourcePkg))
        builder.setInternalProviderId("wn-${card.videoId}")
        builder.setContentId(card.videoId)
        if (card.durationMs in 1..Int.MAX_VALUE.toLong()) builder.setDurationMillis(card.durationMs.toInt())
        return builder.build()
    }

    private fun openIntent(context: Context, videoId: String, sourcePkg: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
            .setClassName(context.packageName, PlayActivity::class.java.name)
            .putExtra(PlayActivity.EXTRA_SOURCE_PKG, sourcePkg)

    private fun posterFor(card: Card): String =
        card.poster?.takeIf { it.startsWith("https://") } ?: "https://i.ytimg.com/vi/${card.videoId}/hqdefault.jpg"

    private fun buildProgram(
        context: Context, channelId: Long, card: Card, description: String?, sourcePkg: String, weight: Int,
    ): PreviewProgram {
        val builder = PreviewProgram.Builder()
        builder.setChannelId(channelId)
        builder.setType(TYPE_CLIP)
        builder.setTitle(card.title ?: "YouTube video")
        description?.let { builder.setDescription(it) }
        builder.setPosterArtUri(Uri.parse(posterFor(card)))
        builder.setPosterArtAspectRatio(ASPECT_RATIO_16_9)
        builder.setIntent(openIntent(context, card.videoId, sourcePkg))
        builder.setInternalProviderId(card.videoId)
        builder.setContentId(card.videoId)
        builder.setWeight(weight)
        builder.setLive(card.live)
        builder.setSearchable(true)
        if (card.durationMs in 1..Int.MAX_VALUE.toLong()) builder.setDurationMillis(card.durationMs.toInt())
        return builder.build()
    }

    private fun logo(context: Context): Bitmap =
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_app)).toBitmap(160, 160)

    private fun finish(prefs: Prefs, ok: Boolean, message: String, error: Throwable? = null): Outcome {
        if (error != null) Log.e(Config.TAG, message, error) else Log.i(Config.TAG, message.replace("\n", " | "))
        prefs.recordRun(message)
        return Outcome(ok, message)
    }

    private fun Cursor.str(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
    private fun Cursor.long(index: Int): Long = if (index < 0 || isNull(index)) 0L else getLong(index)
}
