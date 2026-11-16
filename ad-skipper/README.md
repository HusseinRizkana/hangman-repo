# Ad Skipper (Android)

Closes the full-screen ads that apps force on you (for example a video ad with a
"19s" countdown in the corner) **the instant they can be closed**, and mutes them while they play.

## What it does

When an app opens a full-screen ad from an ad network (AdMob, Unity, AppLovin, ironSource,
Meta, Vungle, Mintegral, Pangle, InMobi and others), Ad Skipper:

1. **Mutes** media volume until the ad is gone.
2. **Presses Back once**, which closes some ads right away.
3. **Keeps watching** the screen and taps **Skip / Close / ✕** as soon as it appears.
   It ignores countdowns like "19s" and never taps "Install", "AdChoices" or info icons.
4. Leaves **rewarded ads** (watch an ad to get coins or lives) alone so you still get the reward,
   unless you turn on "Also close rewarded ads early".

Any "Skip ad" button on any screen is also tapped automatically.

### What it cannot do

No app can make another app's ad play faster or end before its forced countdown finishes.
Android doesn't allow that without root. If you don't want to sit through the countdown at all,
block the ads from loading instead (below).

## Block most in-app ads from loading (no app needed)

Settings → Network & internet → **Private DNS** → *Private DNS provider hostname* →
`dns.adguard-dns.com` → Save.

Ads from most ad networks then never load, so the full-screen ad never shows up.
Downside: "watch an ad for a reward" buttons may stop working. Ad Skipper and Private DNS work
well together.

## Install

1. Open the repository's **Actions** tab → **Build Ad Skipper APK** → latest run →
   download the **ad-skipper-apk** artifact, unzip it, and copy the `.apk` to your phone.
2. Open the APK on the phone and allow installing from that source.
3. Open **Ad Skipper** → **Open Accessibility settings** → *Ad Skipper* → turn it on.
   - **Android 13+:** if the switch is greyed out ("restricted setting"), go to
     **App info → ⋮ (top right) → Allow restricted settings**, then turn it on.

## Build locally

Requires the Android SDK (or Android Studio): `cd ad-skipper && ./gradlew assembleRelease`.

## Privacy

The app has no internet permission and does not collect or send anything. The Accessibility
permission is used only to find and tap ad close buttons.
