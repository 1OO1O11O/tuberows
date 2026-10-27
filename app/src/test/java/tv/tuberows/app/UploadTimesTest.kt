package tv.tuberows.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class UploadTimesTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val locale = Locale.ENGLISH
    private val at = ZonedDateTime.of(2026, 10, 27, 21, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun label(text: String, observedAt: Long = at): String? {
        val p = UploadTimes.find(text, observedAt) ?: return null
        return UploadTimes.label(p.bound, observedAt, zone, locale).lowercase(Locale.ROOT)
    }

    @Test fun hours() = assertEquals("~7 pm, 27 oct", label("Channel • 1.2K views • 1 hour ago"))
    @Test fun minutes() = assertEquals("~8:14 pm, 27 oct", label("45 minutes ago"))
    @Test fun days() = assertEquals("~25 oct", label("2 days ago"))
    @Test fun weeks() = assertEquals("~10 oct", label("Premiered 2 weeks ago"))
    @Test fun months() = assertEquals("~jul 2026", label("3 months ago"))
    @Test fun years() = assertEquals("~2025", label("1 year ago"))

    @Test fun otherYearShowsYear() {
        val jan = ZonedDateTime.of(2027, 1, 2, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("~27 dec 2026", label("5 days ago", jan))
    }

    @Test fun ignoresOtherText() {
        assertNull(UploadTimes.find("il y a 3 heures", at))
        assertNull(UploadTimes.find("12K watching", at))
        assertNull(UploadTimes.find(null, at))
    }

    @Test fun rewriteKeepsChannelAndViews() {
        val text = "Channel • 1.2K views • 1 hour ago"
        val p = UploadTimes.find(text, at)!!
        val out = UploadTimes.rewrite(text, p, UploadTimes.label(p.bound, at, zone, locale))
        assertEquals("channel • 1.2k views • uploaded ~7 pm, 27 oct", out.lowercase(Locale.ROOT))
    }

    @Test fun rewriteKeepsStreamedVerb() {
        val text = "Streamed 3 hours ago"
        val p = UploadTimes.find(text, at)!!
        assertEquals("streamed ~5 pm, 27 oct",
            UploadTimes.rewrite(text, p, UploadTimes.label(p.bound, at, zone, locale)).lowercase(Locale.ROOT))
    }

    @Test fun handlesBidiMarks() {
        val text = "⁨Channel⁩ • ⁨1 hour ago⁩"
        assertEquals("~7 pm, 27 oct", label(text))
    }

    /** A phrase read long after SmartTube wrote it must lose to a fresh reading. */
    @Test fun freshObservationWins() {
        val nextMorning = ZonedDateTime.of(2026, 10, 28, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val stale = UploadTimes.find("1 hour ago", nextMorning)!!.bound          // written at 21:00, read at 07:00
        val fresh = UploadTimes.find("11 hours ago", nextMorning + 15 * 60_000L)!!.bound
        assertSame(fresh, UploadTimes.tighter(stale, fresh))
        assertSame(fresh, UploadTimes.tighter(fresh, stale))
        assertEquals("~7 pm, 27 oct", UploadTimes.label(fresh, nextMorning, zone, locale).lowercase(Locale.ROOT))
    }
}
