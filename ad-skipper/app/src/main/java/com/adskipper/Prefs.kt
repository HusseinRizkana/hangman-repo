package com.adskipper

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around SharedPreferences shared by the settings screen and the service. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("ad_skipper", Context.MODE_PRIVATE)

    /** Master switch. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Mute media volume while a full-screen ad is showing. */
    var muteAds: Boolean
        get() = sp.getBoolean(KEY_MUTE, true)
        set(v) = sp.edit().putBoolean(KEY_MUTE, v).apply()

    /** Close rewarded ads early too (the app will not give you the reward). */
    var skipRewarded: Boolean
        get() = sp.getBoolean(KEY_REWARDED, false)
        set(v) = sp.edit().putBoolean(KEY_REWARDED, v).apply()

    /** Also tap small unlabeled buttons in the top corners of ad screens. */
    var aggressive: Boolean
        get() = sp.getBoolean(KEY_AGGRESSIVE, false)
        set(v) = sp.edit().putBoolean(KEY_AGGRESSIVE, v).apply()

    var skipCount: Int
        get() = sp.getInt(KEY_COUNT, 0)
        set(v) = sp.edit().putInt(KEY_COUNT, v).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MUTE = "mute_ads"
        private const val KEY_REWARDED = "skip_rewarded"
        private const val KEY_AGGRESSIVE = "aggressive"
        private const val KEY_COUNT = "skip_count"
    }
}
