package tv.tuberows.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Listens for the screen turning on while this app's process is alive (it starts for every sync,
 * card open and app open). Android can't deliver SCREEN_ON to manifest receivers, which is also why
 * SmartTube's own wake hook never fires; registering at runtime is the only way to hear it.
 */
class TubeRowsApp : Application() {

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Throttled inside: refreshes now if the last refresh is over 10 minutes old, else soon after.
            SmartTube.request(context.applicationContext, "screen on")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // System broadcasts are delivered to NOT_EXPORTED receivers; nothing else can reach this one.
        ContextCompat.registerReceiver(
            this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
