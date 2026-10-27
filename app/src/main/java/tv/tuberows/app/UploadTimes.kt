package tv.tuberows.app

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns YouTube's relative "3 hours ago" into a fixed time ("Uploaded ~6 pm, 27 Oct"), which can
 * never go stale. English phrases only; anything else is left exactly as SmartTube wrote it.
 *
 * Why an upper bound: SmartTube writes the phrase when it fetches, and we may read it much later.
 * "N units ago", read at time T, only guarantees upload <= T - N units (fetch time <= T). The
 * smallest such bound across observations is always valid, and a fresh observation makes it tight.
 */
object UploadTimes {

    enum class Unit(val millis: Long) {
        SECOND(1_000L), MINUTE(60_000L), HOUR(3_600_000L), DAY(86_400_000L),
        WEEK(7 * 86_400_000L), MONTH(30 * 86_400_000L), YEAR(365 * 86_400_000L),
    }

    /**
     * What we know: the upload happened no later than [upperBound]. [exact] means a fresh read caught
     * YouTube's wording tick over ("1 hour" to "2 hours"), so the bound is within one refresh of the truth.
     */
    data class Bound(val upperBound: Long, val unit: Unit, val exact: Boolean = false) {
        /** Exact: just before the bound. Otherwise the middle of YouTube's bucket ("1 hour ago" = 1:00 to 1:59). */
        val estimate: Long
            get() = upperBound - if (exact) minOf(unit.millis / 2, EXACT_SLACK_MS) else unit.millis / 2
    }

    data class Phrase(val range: IntRange, val prefix: String?, val bound: Bound)

    /** Half of SmartTube's 15-minute refresh: how far an "exact" bound can sit after the real upload. */
    private const val EXACT_SLACK_MS = 7 * 60_000L + 30_000L

    // Spaces may be non-breaking in some YouTube text.
    private const val SP = "[\\s\\u00A0\\u202F]+"
    private val PHRASE = Regex(
        "\\b(?:(streamed|premiered)$SP)?(\\d{1,4})$SP(second|minute|hour|day|week|month|year)s?${SP}ago\\b",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String?, observedAt: Long): Phrase? {
        if (text.isNullOrBlank()) return null
        val m = PHRASE.find(text) ?: return null
        val n = m.groupValues[2].toLongOrNull() ?: return null
        val unit = Unit.valueOf(m.groupValues[3].uppercase(Locale.ROOT))
        val prefix = m.groupValues[1].takeIf { it.isNotEmpty() }
        return Phrase(m.range, prefix, Bound(observedAt - n * unit.millis, unit))
    }

    /**
     * Keeps whichever bound is tighter (earlier). Both are valid; the earlier one is closer to the truth.
     *
     * When the new bound is earlier by less than one unit of the current one, YouTube's wording has just
     * ticked over ("1 hour" to "2 hours", or "23 hours" to "2 days"), so the new bound is exact and the
     * finer unit is kept. Earlier by more than that means the current reading was stale: start again.
     */
    fun tighter(current: Bound?, candidate: Bound): Bound = when {
        current == null -> candidate
        candidate.upperBound >= current.upperBound -> current
        candidate.unit >= current.unit && candidate.upperBound > current.upperBound - current.unit.millis ->
            Bound(candidate.upperBound, current.unit, exact = true)
        else -> candidate
    }

    fun label(bound: Bound, now: Long, zone: ZoneId, locale: Locale): String {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(bound.estimate), zone)
        val thisYear = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).year
        val withYear = t.year != thisYear
        val pattern = when (bound.unit) {
            Unit.SECOND, Unit.MINUTE -> if (withYear) "h:mm a, d MMM yyyy" else "h:mm a, d MMM"
            Unit.HOUR -> if (withYear) "h a, d MMM yyyy" else "h a, d MMM"
            Unit.DAY, Unit.WEEK -> if (withYear) "d MMM yyyy" else "d MMM"
            Unit.MONTH -> "MMM yyyy"
            Unit.YEAR -> "yyyy"
        }
        return "~" + DateTimeFormatter.ofPattern(pattern, locale).format(t)
    }

    /** Replaces the relative phrase in [text] with the fixed label, keeping the rest (channel, views). */
    fun rewrite(text: String, phrase: Phrase, label: String): String {
        val verb = phrase.prefix?.replaceFirstChar { it.titlecase(Locale.ROOT) } ?: "Uploaded"
        return text.replaceRange(phrase.range, "$verb $label")
    }
}
