package com.adskipper

/**
 * Recognises the full-screen ad screens that ad SDKs open inside apps, and the
 * labels of their Skip / Close buttons.
 */
object AdSignatures {

    /** Activity classes (or class-name prefixes) used by common ad SDKs. */
    private val AD_ACTIVITY_PREFIXES = listOf(
        "com.google.android.gms.ads.AdActivity",                 // AdMob / Google Mobile Ads
        "com.unity3d.services.ads.adunit.",                      // Unity Ads
        "com.unity3d.ads.adplayer.",                             // Unity Ads (newer)
        "com.applovin.adview.AppLovinFullscreen",                // AppLovin MAX
        "com.applovin.adview.AppLovinInterstitial",
        "com.applovin.impl.adview.",
        "com.ironsource.sdk.controller.ControllerActivity",      // ironSource / LevelPlay
        "com.ironsource.sdk.controller.InterstitialActivity",
        "com.facebook.ads.AudienceNetworkActivity",              // Meta Audience Network
        "com.facebook.ads.internal.ipc.RemoteANActivity",
        "com.vungle.",                                           // Vungle / Liftoff
        "com.mbridge.msdk.reward.player.",                       // Mintegral
        "com.mbridge.msdk.activity.",
        "com.bytedance.sdk.openadsdk.activity.",                 // Pangle
        "com.inmobi.ads.rendering.",                             // InMobi
        "com.chartboost.sdk.view.",                              // Chartboost
        "com.adcolony.sdk.AdColony",                             // AdColony
        "com.fyber.inneractive.sdk.activities.",                 // DT Exchange / Fyber
        "com.yandex.mobile.ads.common.AdActivity",               // Yandex
        "com.my.target.common.MyTargetActivity",                 // myTarget
        "com.smaato.sdk.interstitial.",                          // Smaato
        "com.smaato.sdk.rewarded.",
        "com.amazon.device.ads.DTB",                             // Amazon Publisher Services
        "com.tapjoy.TJAdUnitActivity",                           // Tapjoy
        "com.startapp.sdk.adsbase.activities.",                  // Start.io
        "com.moloco.sdk.",                                       // Moloco
        "com.bigossp.",                                          // Bigo Ads
        "sg.bigo.ads.",
    )

    /** Fallback: words that show up in the class names of most ad screens. */
    private val AD_ACTIVITY_HINTS = listOf(
        "interstitial", "rewardvideo", "rewardedvideo", "fullscreenvideo",
        "fullscreenad", "adactivity", "adunitactivity", "rewardedad",
    )

    fun isAdActivity(className: String): Boolean {
        if (AD_ACTIVITY_PREFIXES.any { className.startsWith(it) }) return true
        val simple = className.substringAfterLast('.').lowercase()
        return AD_ACTIVITY_HINTS.any { simple.contains(it) }
    }

    /** Labels that mean "skip this ad" anywhere on screen, even outside a known ad screen. */
    private val GLOBAL_SKIP = setOf(
        "skip ad", "skip ads", "skip this ad", "skip advert", "skip advertisement",
        "close ad", "close ads", "close advertisement", "interstitial close button",
    )

    /** Labels that are only treated as an ad's skip/close button on a known ad screen. */
    private val AD_SCREEN_CLOSE = setOf(
        "skip", "skip video", "close", "close button", "close video", "dismiss",
        "x", "×", "✕", "✖", "╳", "⨯", "continue to app", "no thanks", "skip to app",
        "close icon", "btn close", "exit", "done",
    )

    /** Shown on the "you'll lose your reward" dialog that some rewarded ads pop up. */
    private val LOSE_REWARD_HINTS = listOf("lose your reward", "lose the reward", "won't get", "will not get", "lose reward")
    val RESUME_LABELS = setOf("resume video", "resume", "continue watching", "keep watching", "continue", "back to video")
    val CONFIRM_CLOSE_LABELS = setOf("close video", "close", "skip video", "skip", "close ad", "yes", "exit")

    /** Labels of icons we must never tap (they open the browser or settings). */
    private val NEVER_TAP = listOf(
        "adchoices", "ad choices", "ad options", "info", "why this ad", "privacy",
        "learn more", "install", "download", "open", "get", "visit", "mute", "unmute", "sound",
    )

    /** Lower-cases a label and strips arrows/punctuation around it: "Skip Ad ›" → "skip ad". */
    fun normalize(raw: CharSequence?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.toString()
            .lowercase()
            .replace(Regex("[>›»▶►→⏭|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** "Skip in 5", "Reward in 10s" etc. are countdowns, not tappable yet. */
    fun isCountdown(label: String): Boolean = label.any { it.isDigit() }

    fun isGlobalSkip(label: String): Boolean = label in GLOBAL_SKIP

    fun isAdScreenClose(label: String): Boolean = label in GLOBAL_SKIP || label in AD_SCREEN_CLOSE

    fun isLoseRewardText(label: String): Boolean = LOSE_REWARD_HINTS.any { label.contains(it) }

    fun isRewardCountdown(label: String): Boolean = label.contains("reward") && isCountdown(label)

    fun isForbidden(label: String): Boolean = NEVER_TAP.any { label == it || label.startsWith("$it ") }

    /** Resource ids like "com.foo:id/btn_close" or "ad_skip_button". */
    fun idLooksLikeClose(viewId: String?): Boolean {
        if (viewId == null) return false
        val name = viewId.substringAfter(":id/").lowercase()
        if (name.contains("adchoice") || name.contains("privacy") || name.contains("info") ||
            name.contains("mute") || name.contains("sound")) return false
        return name.contains("close") || name.contains("skip") || name.contains("dismiss")
    }
}
