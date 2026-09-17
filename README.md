# ShortFormBlocker

An Android app that closes short-form video feeds the moment they open: YouTube Shorts, Instagram
Reels, TikTok, Facebook Reels, Snapchat Spotlight, and the same sites in a browser. Hard block, no
quota, no "5 more minutes". Personal sideload build - not intended for the Play Store.

## How it works

Short-form feeds live *inside* apps you otherwise want to keep, on the same domains and the same TLS
connections as everything else, so no network or DNS blocker can see them. The only thing on stock
Android that can is an **AccessibilityService** reading the foreground window.

```
AccessibilityEvent (window state / content changed, throttled to 150ms)
  -> SnapshotCollector   bounded walk of the node tree -> ScreenSnapshot
  -> RuleMatcher         pure function: snapshot + enabled rules -> matched rule?
  -> Enforcer            Back, Back, then out of the feed + a full-screen block card
```

Rule matching is a pure function over a plain data snapshot
([`BlockRule.kt`](app/src/main/java/com/mati/shortformblocker/detect/BlockRule.kt)), and so is the
DM pass ([`ReelAllowancePolicy.kt`](app/src/main/java/com/mati/shortformblocker/detect/ReelAllowancePolicy.kt)),
so all detection logic is unit tested on the JVM with no device involved.

The last escalation step prefers to stay inside the app: in Instagram it taps the Home tab
([`InAppHome.kt`](app/src/main/java/com/mati/shortformblocker/service/InAppHome.kt)), so someone who
opened the app to answer a DM is not thrown out to the launcher. When there is no such tab on screen
- the Reels viewer opened from a DM, or any other app - it falls back to the device home screen.

Two details that decide whether this app is usable:

- **Selected labels, not labels.** Instagram's Reels button and YouTube's Shorts tab are on screen on
  *every* screen of those apps. Rules only look at labels of nodes reporting `isSelected`, i.e. the
  tab actually open, so the home feed is never mistaken for the Reels feed.
- **A reel a friend sent you still plays.** Instagram Reels has one exception: if you reached a reel
  from a DM conversation, it plays. The pass covers that reel only - the moment the pager scrolls or
  the reel in front of you is no longer the one the pass was granted for, blocking resumes. Go back
  to the conversation and open another reel and you get a fresh pass, so there is no cap on reels
  friends actually send, only on the feed behind them. The Reels tab never earns a pass.
- **Facebook has no view ids at all.** It ships with its resource names stripped, so every id it
  reports is the literal string `(name removed)`, and its full-screen reel player - what you get
  opening a reel from the feed, a profile, a share or a notification - marks nothing as selected
  either. That surface is matched on the labels Facebook puts on the player itself
  ([`FacebookSurfaces.kt`](app/src/main/java/com/mati/shortformblocker/detect/FacebookSurfaces.kt)),
  never on the bare `Reel` label of a video playing inline in the home feed.
- **The player, not the shelf.** The Shorts shelf on the YouTube home feed is deliberately not a
  signal; matching it would bounce you out of the home feed itself.
- **On screen, not merely present.** Instagram's home feed and Reels are two pages of one swipeable
  pager, so the home feed's node tree carries every `clips_*` id and even reports Reels as selected.
  The Instagram rule therefore matches visible nodes only (`visibleSignalsOnly`). Without it the rule
  fired on the home feed, and the second Back press of the escalation exited Instagram entirely.

## The home feeds get a budget, not a block

Facebook's and Instagram's home feeds are the other endless thing on the phone, but they cannot be
blocked the way a Reels tab can: the feed is the front door of the app, and closing it takes your
messages, events and notifications with it. So those two rules are **budgeted**
([`FeedBudgetPolicy.kt`](app/src/main/java/com/mati/shortformblocker/detect/FeedBudgetPolicy.kt)):
you get about a dozen screens of feed per visit, measured in real pixels from the scroll events, and
when it runs out the feed closes until you have left the feed alone for fifteen minutes. Both
numbers are in the app, under **Feed budget** - tightening them applies as you tap, loosening them
waits out the same cooldown as switching a rule off. The same card shows where each feed stands
right now - full budget, part spent, or closed with the refill counting down - read live off the
accessibility service through
[`FeedBudgetHolder.kt`](app/src/main/java/com/mati/shortformblocker/detect/FeedBudgetHolder.kt),
since a per-visit budget is not a setting and never reaches disk.

The wait is measured from your last **scroll of the feed**, not from the last time the app was on
screen. That distinction is the whole difference between a rule you can live with and one you cannot:
measuring it from the app meant answering a DM at minute fourteen pushed the refill back another
fifteen minutes, so anyone who checks their messages often enough never got the feed back at all.
Using the rest of the app now costs you nothing; scrolling the feed again is what restarts the wait.

Three details, each of them something the phone taught us rather than the other way round:

- **Blocking follows the scroll, not the state.** An overspent feed you open, glance at and leave is
  never blocked, so the app still works for the message you came for. Scroll it again and it closes
  again at once - there is no fresh budget, only a fresh refusal. The first version blocked on
  arrival and threw you out of Instagram the moment you opened it.
- **A scrolled feed does not look like a feed.** Facebook hides its bottom nav as soon as you scroll
  and leaves it hidden, so nothing *visible* says "this is the feed" - the Home tab is still in the
  tree and still selected, just off screen. The feed rules therefore read the open tab from the whole
  tree, and the policy latches the feed until a screen turns up that is clearly somewhere else.
- **A selected tab may not carry its own name.** Instagram marks a bare icon node as selected and
  keeps the word "Home" on its parent, so a selected node with no label asks its ancestors what it
  is.

The block card is a `TYPE_ACCESSIBILITY_OVERLAY` window rather than an Activity, which needs no
overlay permission and is not subject to background-activity-launch restrictions. It swallows
touches (so the feed underneath cannot be swiped while we navigate away) but is not focusable, so
Back and Home still work.

## Install it (no build required)

Grab `shortformblocker.apk` from the [latest release](../../releases/latest) on the phone itself -
the direct link, the one worth sending to someone, is
<https://github.com/rosinski141/ShortFormBlocker/releases/latest/download/shortformblocker.apk> and
it always points at the newest version. Open it, and let the browser install an app from an unknown
source when Android asks. Android 8+
asks per app, so the switch you are granting is "allow Chrome to install apps", which you can turn
straight back off afterwards.

The APK is signed with a personal key, not by Google, so Play Protect will warn that it cannot scan
it - "Install anyway" is the button. The source of everything it does is in this repository.

Then, on the phone:

1. Open the app and tap **Open accessibility settings**, find ShortFormBlocker, switch it on.
2. Allow notifications and exclude the app from battery optimisation (both offered on the setup card).
3. On Xiaomi / Samsung / OnePlus and similar, also allow **autostart** in the system settings, or the
   OEM battery manager will eventually kill the watchdog.

Nothing you do in the app leaves the phone - see [Privacy](#privacy).

## Build it yourself

Needs JDK 17+ and the Android SDK (platform 37). No Android Studio required; the Gradle wrapper
downloads everything else.

```bash
./gradlew :app:testDebugUnitTest      # 87 detection, feed-budget, DM-pass and cooldown tests, no device
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mati.shortformblocker/.ui.MainActivity
```

`./gradlew :app:assembleRelease` signs with the release key when `keystore.properties` and the
keystore it points at are present, and falls back to the debug key when they are not. Both are
gitignored, so a fresh clone builds a debug-signed release APK: good enough to run yourself, not
something to hand to anyone else.

The releases on this repository are built from the tagged commit and signed with that key, which is
why an update installs over an older version instead of asking you to uninstall it first.

## Turning it off

Protection cannot be switched off instantly - that is the point. Toggling it off starts a **2 hour
cooldown**; the switch keeps enforcing until the countdown ends, and cancelling is instant. The same
applies per app rule. Re-enabling is always immediate. The cooldown can be lengthened but never
shortened (`SettingsRepository.setCooldownMinutes`). Uninstalling is not restricted.

## When an app redesign breaks a rule

Apps rename their view ids without warning; this is the maintenance loop:

1. Open the feed that is no longer being blocked.
2. Switch to ShortFormBlocker -> **Debug: inspect the last screen**. It shows two things: the last
   screen the service saw, and **why the last block fired** - the dump of the screen that triggered
   it, which is persisted, so it survives the service restarts that OEM builds do constantly.
   **Copy dump** puts either on the clipboard. (For an app that has no rule yet, turn on **Capture
   every app** first, then turn it back off - it widens the accessibility filter to every app.)
3. Add the new id to the relevant rule in
   [`RuleCatalog.kt`](app/src/main/java/com/mati/shortformblocker/detect/RuleCatalog.kt) and add a
   test case for it in `RuleMatcherTest` - both a positive one and a negative one taken from a normal
   screen of the same app.
4. Rebuild and reinstall.

Ground truth for view ids, with the phone connected:

```bash
adb shell uiautomator dump /sdcard/win.xml && adb pull /sdcard/win.xml
adb logcat -s ShortFormBlocker
# logcat drops lines on some OEM builds - the stored counters never lie:
adb shell run-as com.mati.shortformblocker cat files/datastore/blocker.preferences_pb | strings
```

## Known limits

- **View ids drift.** Rules carry redundant signals so one rename does not break detection, but this
  app needs occasional upkeep. The debug screen exists for exactly that.
- **The DM pass depends on recognising a DM thread**, via the view-id fragments in
  `InstagramSurfaces.DM_SCREEN_ID_FRAGMENTS`. If Instagram renames them the feature fails closed:
  reels from friends get blocked like everything else, and the fix is to add the new id there. Note
  what is *not* in that list - `direct_tab`, the bottom-nav DM button, which is on screen everywhere
  and would hand a pass to the entire feed.
- **Browser detection reads the address bar.** Some browser versions truncate the displayed URL to
  the domain, in which case `youtube.com/shorts/...` cannot be distinguished from `youtube.com`; the
  in-app rules still catch the native apps.
- **Reddit and X rules ship disabled** - their feeds are harder to identify without false positives.
  Turn them on and confirm normal browsing still works before trusting them.
- **OEM battery managers** may kill the watchdog service. The high-priority "not running"
  notification is the backstop.
- **Verified on device** (HONOR PTP_N49, Android 16 / API 36, 2026-09-16): TikTok, YouTube Shorts
  (via the Shorts tab), Instagram Reels and `tiktok.com` in Chrome are all blocked within about a
  second; YouTube's home feed and watch page, Instagram's home feed, Chrome on a normal site and
  Reddit (rule off by default) are all left alone. Blocking still applies while a disable cooldown
  is counting down, and cancelling the cooldown works. 43 unit tests pass.
- **The DM pass has not been confirmed against a real DM thread yet** - the thread view ids are the
  one part of it that is still an educated guess. Until that is checked, reels opened from DMs may
  still be blocked (the safe direction).
- The Instagram rule matched on `clips_swipe_refresh_container` and the selected label "Reels";
  `clips_viewer` is *not* in the current Instagram build, which is exactly why rules carry several
  signals each.

## Privacy

Everything stays on the phone. Screen content is read in memory, matched, and dropped. What is
written to disk is your settings, per-day block counts, and a dump of the single most recent
*blocked* screen (view ids and labels of a Reels or Shorts feed, kept so the debug screen can
explain why a rule fired). Nothing is sent anywhere, and the stored dump is overwritten by the next
block. To drop it, clear the app's storage.
