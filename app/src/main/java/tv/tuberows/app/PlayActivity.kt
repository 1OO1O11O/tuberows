package tv.tuberows.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/** Invisible hop: card → TizenTube, falling back to the SmartTube build the card came from. */
class PlayActivity : Activity() {

    companion object {
        const val EXTRA_SOURCE_PKG = "tv.tuberows.app.SOURCE_PKG"
        private val HOSTS = setOf("www.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val video = intent?.data
        if (video == null || video.scheme != "https" || video.host !in HOSTS) {
            finish()
            return
        }

        val opened = launch(Intent(Intent.ACTION_VIEW, video).setPackage(Config.TIZENTUBE)) || run {
            // Only ever fall back to a SmartTube package, never an arbitrary one from the extra.
            val source = intent.getStringExtra(EXTRA_SOURCE_PKG)?.takeIf { Config.isSourcePackage(it) }
            source != null && (
                launch(Intent(Intent.ACTION_VIEW, video).setPackage(source)) ||
                    launch(packageManager.getLeanbackLaunchIntentForPackage(source))
                )
        }

        if (opened) {
            // Refresh SmartTube once this video is in YouTube history (throttled, coalesced).
            SmartTube.request(this, "video opened", Config.VIDEO_NUDGE_DELAY_MS)
        } else {
            Toast.makeText(this, "TizenTube isn't installed", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun launch(target: Intent?): Boolean {
        if (target == null) return false
        return try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            Log.w(Config.TAG, "Launch blocked: ${target.`package`}", e)
            false
        }
    }
}
