package com.adskipper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        bindSwitch(R.id.switch_enabled, prefs.enabled) { prefs.enabled = it }
        bindSwitch(R.id.switch_mute, prefs.muteAds) { prefs.muteAds = it }
        bindSwitch(R.id.switch_rewarded, prefs.skipRewarded) { prefs.skipRewarded = it }
        bindSwitch(R.id.switch_aggressive, prefs.aggressive) { prefs.aggressive = it }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_app_info).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btn_dns).setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        findViewById<TextView>(R.id.status).text =
            if (on) "✅ Ad Skipper is ON" else "⚠️ Ad Skipper is OFF. Turn it on in Accessibility settings."
        findViewById<TextView>(R.id.count).text = "Ads skipped or closed: ${prefs.skipCount}"
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val s = findViewById<Switch>(id)
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, AdSkipService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
