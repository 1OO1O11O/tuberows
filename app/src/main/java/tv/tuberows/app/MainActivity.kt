package tv.tuberows.app

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Status and settings screen. Opening it also kicks off a sync. */
class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var grantButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(36), dp(56), dp(36))
        }

        root.addView(text("TizenTube Rows", 30f, Color.WHITE, bold = true))
        root.addView(text("Mirrors SmartTube's home rows into Projectivy. Every card opens in TizenTube.", 16f, GREY))
        root.addView(spacer(20))

        root.addView(text("Rows", 20f, Color.WHITE, bold = true))
        for (row in Config.Row.entries) {
            root.addView(CheckBox(this).apply {
                text = row.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isChecked = prefs.isEnabled(row)
                setOnCheckedChangeListener { _, on ->
                    prefs.setEnabled(row, on)
                    Scheduler.syncNow(this@MainActivity)
                }
            })
        }
        root.addView(CheckBox(this).apply {
            text = "Watch Next (recent history + new uploads)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            isChecked = prefs.watchNextEnabled
            setOnCheckedChangeListener { _, on ->
                prefs.watchNextEnabled = on
                Scheduler.syncNow(this@MainActivity)
            }
        })
        root.addView(spacer(16))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        // First, so it's the focused button on a fresh install.
        grantButton = button("Grant access") {
            requestPermissions(arrayOf(Config.READ_TV_LISTINGS), REQUEST_PERMISSION)
        }
        actions.addView(grantButton)
        actions.addView(button("Sync now") { Scheduler.syncNow(this) })
        actions.addView(button("Refresh SmartTube") {
            if (SmartTube.nudgeNow(this, "manual", force = true)) {
                Toast.makeText(this, "SmartTube is refreshing; rows update in about a minute", Toast.LENGTH_SHORT).show()
            }
        })
        actions.addView(button("Open TizenTube") {
            packageManager.getLeanbackLaunchIntentForPackage(Config.TIZENTUBE)?.let { startActivity(it) }
        })
        root.addView(actions)
        root.addView(spacer(20))

        root.addView(text("Status", 20f, Color.WHITE, bold = true))
        statusView = text("", 16f, GREY).apply { typeface = Typeface.MONOSPACE }
        root.addView(statusView)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        prefs.store.registerOnSharedPreferenceChangeListener(this)
        Scheduler.ensure(this)
        Scheduler.syncNow(this)
        SmartTube.request(this, "app opened")
        render()
        if (checkSelfPermission(Config.READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) grantButton.requestFocus()
    }

    override fun onPause() {
        prefs.store.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(store: SharedPreferences?, key: String?) {
        runOnUiThread { render() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_PERMISSION) Scheduler.syncNow(this)
        render()
    }

    private fun render() {
        val granted = checkSelfPermission(Config.READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED
        val tizenTube = packageManager.getLeanbackLaunchIntentForPackage(Config.TIZENTUBE) != null
        grantButton.visibility = if (granted) View.GONE else View.VISIBLE
        val now = System.currentTimeMillis()
        val nudge = if (prefs.lastNudge > 0) "${ago(prefs.lastNudge)} (${prefs.lastNudgeReason})" else "never"
        val changed = prefs.lastContentChange
        val stale = changed > 0 && now - changed > Config.STALE_WARNING_MS
        val power = getSystemService(PowerManager::class.java)
        val smartTube = prefs.sourcePackages.ifEmpty { SmartTube.installedPackages(this) }
        fun isExempt(pkg: String) = power?.isIgnoringBatteryOptimizations(pkg) == true
        fun fix(pkg: String) = "  Fix: adb shell dumpsys deviceidle whitelist +$pkg"

        statusView.text = buildString {
            appendLine("Read access : ${if (granted) "granted" else "MISSING: press Grant access above"}")
            appendLine("TizenTube   : ${if (tizenTube) "installed" else "NOT FOUND (videos open in SmartTube)"}")
            appendLine("SmartTube   : ${if (smartTube.isEmpty()) "NOT FOUND" else smartTube.joinToString()}")
            appendLine("Last sync   : ${ago(prefs.lastRun)}")
            appendLine("Refreshed   : $nudge")
            appendLine("New content : ${ago(changed)}" +
                if (stale) "  STALE: SmartTube may not be refreshing (signed in? exempt below?)" else "")
            appendLine()
            appendLine("Battery exemption (keeps background refreshes running):")
            for (pkg in smartTube) {
                if (isExempt(pkg)) appendLine("  SmartTube ($pkg): exempt")
                else { appendLine("  SmartTube ($pkg): NOT exempt, rows will go stale"); appendLine(fix(pkg)) }
            }
            if (isExempt(packageName)) appendLine("  TubeRows: exempt")
            else { appendLine("  TubeRows: not exempt (optional; opening cards keeps it active)"); appendLine(fix(packageName)) }
            appendLine()
            append(prefs.status)
        }
    }

    private fun ago(time: Long): CharSequence =
        if (time <= 0) "never"
        else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(12) }
    }

    private fun spacer(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_PERMISSION = 1
        val BACKGROUND = Color.rgb(16, 20, 24)
        val GREY = Color.rgb(176, 184, 192)
    }
}
