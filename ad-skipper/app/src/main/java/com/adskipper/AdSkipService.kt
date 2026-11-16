package com.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches for full-screen ads opened by ad SDKs inside other apps. While one is showing it:
 *  - mutes media volume (optional),
 *  - presses Back once, which closes some ads straight away,
 *  - keeps checking the screen and taps Skip / Close / ✕ the moment it becomes available.
 *
 * Nothing can make another app's ad play faster or end sooner; this just removes the
 * delay between "the ad can be closed" and you closing it.
 */
class AdSkipService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val activityCache = HashMap<String, Boolean>()

    private var inAdScreen = false
    private var adEnteredAt = 0L
    private var triedBack = false
    private var mutedByUs = false
    private var lastScanAt = 0L
    private var lastTapAt = 0L
    private var scanPending = false

    private val poll = object : Runnable {
        override fun run() {
            if (!inAdScreen) return
            scan()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val scanRunnable = Runnable {
        scanPending = false
        scan()
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!prefs.enabled) {
            if (inAdScreen) leaveAdScreen()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()
            if (cls != null) {
                if (AdSignatures.isAdActivity(cls)) enterAdScreen()
                else if (isActivity(pkg, cls)) leaveAdScreen()
            }
        }
        scheduleScan()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        leaveAdScreen()
        super.onDestroy()
    }

    // ---- ad screen state -------------------------------------------------------------

    private fun enterAdScreen() {
        if (inAdScreen) return
        inAdScreen = true
        adEnteredAt = SystemClock.uptimeMillis()
        triedBack = false
        if (prefs.muteAds && !mutedByUs) {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            mutedByUs = true
        }
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, POLL_MS)
    }

    private fun leaveAdScreen() {
        inAdScreen = false
        handler.removeCallbacks(poll)
        if (mutedByUs) {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            mutedByUs = false
        }
    }

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** True if [cls] is a real activity of [pkg] (window events also fire for dialogs and views). */
    private fun isActivity(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        return activityCache.getOrPut(key) {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    // ---- scanning --------------------------------------------------------------------

    private fun scheduleScan() {
        if (scanPending) return
        val wait = (lastScanAt + MIN_SCAN_GAP_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        scanPending = true
        handler.postDelayed(scanRunnable, wait)
    }

    private fun scan() {
        lastScanAt = SystemClock.uptimeMillis()
        if (lastScanAt - lastTapAt < TAP_COOLDOWN_MS) return

        val roots = ArrayList<AccessibilityNodeInfo>()
        try {
            windows?.forEach { w -> w.root?.let { roots += it } }
        } catch (_: Exception) {
        }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots += it }
        if (roots.isEmpty()) return

        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes)
        val labels = nodes.associateWith { label(it) }

        val loseRewardDialog = labels.values.any { AdSignatures.isLoseRewardText(it) }
        if (loseRewardDialog) {
            val wanted = if (prefs.skipRewarded) AdSignatures.CONFIRM_CLOSE_LABELS else AdSignatures.RESUME_LABELS
            nodes.firstOrNull { labels[it] in wanted }?.let { tap(it) }
            return
        }

        // Strong signals ("Skip ad", AdMob's "Interstitial close button") work on any screen.
        nodes.firstOrNull { AdSignatures.isGlobalSkip(labels[it]!!) && tappable(it) }?.let {
            tap(it); return
        }

        if (!inAdScreen) return

        // Rewarded ad still counting down: leave it alone unless the user opted in.
        val rewardRunning = labels.values.any { AdSignatures.isRewardCountdown(it) }
        if (rewardRunning && !prefs.skipRewarded) return

        val candidate = nodes.firstOrNull { n ->
            val l = labels[n]!!
            tappable(n) && !AdSignatures.isCountdown(l) && !AdSignatures.isForbidden(l) &&
                (AdSignatures.isAdScreenClose(l) || (l.isEmpty() && AdSignatures.idLooksLikeClose(n.viewIdResourceName)))
        } ?: if (prefs.aggressive) cornerButton(nodes, labels) else null

        if (candidate != null) {
            tap(candidate)
            return
        }

        // Some ads close on Back even during the countdown. Try it once, after the ad has loaded.
        if (!triedBack && SystemClock.uptimeMillis() - adEnteredAt > BACK_DELAY_MS && !rewardRunning) {
            triedBack = true
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun collect(root: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val n = queue.removeFirst()
            if (n.isVisibleToUser) out += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
    }

    private fun label(n: AccessibilityNodeInfo): String {
        val t = AdSignatures.normalize(n.text)
        return if (t.isNotEmpty()) t else AdSignatures.normalize(n.contentDescription)
    }

    private fun tappable(n: AccessibilityNodeInfo): Boolean {
        if (!n.isEnabled) return false
        val r = Rect().also { n.getBoundsInScreen(it) }
        return r.width() > 0 && r.height() > 0
    }

    /** Small, unlabeled, clickable thing in a top corner of an ad screen: almost always the ✕. */
    private fun cornerButton(
        nodes: List<AccessibilityNodeInfo>,
        labels: Map<AccessibilityNodeInfo, String>,
    ): AccessibilityNodeInfo? {
        val dm = resources.displayMetrics
        val maxSize = 72 * dm.density
        return nodes.firstOrNull { n ->
            if (!n.isClickable || !tappable(n) || labels[n]!!.isNotEmpty()) return@firstOrNull false
            val r = Rect().also { n.getBoundsInScreen(it) }
            r.width() <= maxSize && r.height() <= maxSize &&
                r.bottom < dm.heightPixels * 0.2f &&
                (r.right > dm.widthPixels * 0.75f || r.left < dm.widthPixels * 0.25f)
        }
    }

    // ---- tapping ---------------------------------------------------------------------

    private fun tap(node: AccessibilityNodeInfo) {
        lastTapAt = SystemClock.uptimeMillis()
        prefs.skipCount = prefs.skipCount + 1

        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && depth < 4) {
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            target = target.parent
            depth++
        }
        // Not clickable through accessibility (common in WebView ads): tap its centre instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    companion object {
        private const val POLL_MS = 500L
        private const val MIN_SCAN_GAP_MS = 250L
        private const val TAP_COOLDOWN_MS = 800L
        private const val BACK_DELAY_MS = 1500L
        private const val MAX_NODES = 1500
    }
}
