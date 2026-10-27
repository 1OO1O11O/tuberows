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

    /** What one phrase tells us: the upload happened no later than [upperBound]. */
    data class Bound(val upperBound: Long, val unit: Unit) {
        /** Middle of YouTube's rounding bucket ("1 hour ago" means 1:00 to 1:59 ago). */
        val estimate: Long get() = upperBound - unit.millis / 2
    }

    data class Phrase(val range: IntRange, val prefix: String?, val bound: Bound)

    private val PHRASE = Regex(
        "\\b(?:(streamed|premiered)\\s+)?(\\d{1,4})\\s+(second|minute|hour|day|week|month|year)s?\\s+ago\\b",
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

    /** Keeps whichever bound is tighter (earlier). Both are valid; the earlier one is closer to the truth. */
    fun tighter(current: Bound?, candidate: Bound): Bound =
        if (current == null || candidate.upperBound < current.upperBound) candidate else current

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
