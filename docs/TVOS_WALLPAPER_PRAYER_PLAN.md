# tvOS-Inspired Launcher, Wallpaper, and Prayer Times Plan

## Audit baseline

Repository: `b4byhuey/LeanbackLauncher`
Base branch: `main`
Baseline commit: `762df7622ea0f8bb5e4684514b478c5cf5e23f42`
Application module: `app`
Application ID: `com.amazon.tv.leanbacklauncher`
Current version: `1.63`
Minimum SDK: 22
Target SDK: 29
Compile SDK: 35

The project is an existing Android TV / Fire TV launcher, not a new launcher scaffold. Its manifest declares `MainActivity` as the HOME activity, has settings activities, and already declares a wallpaper-changed receiver. The app uses AndroidX Leanback, AppCompat, Glide, Material Components, Kotlin coroutines, and a local-weather library.

## Goals

1. Introduce an Apple tvOS-inspired home screen while retaining Android TV remote navigation and existing launcher behavior.
2. Let users choose a static wallpaper from the system photo picker / gallery.
3. Support live wallpaper where Android and the device's launcher rendering model allow it.
4. Add a prayer-times panel with the next prayer and countdown.

## UI direction

- Full-screen scenic background with a configurable dim/gradient overlay for legibility.
- A compact top information area for date/time and optional network/weather information.
- Large, clearly focused app cards with scale/elevation feedback and generous spacing.
- Horizontal content rows with D-pad focus, predictable focus restoration, and no touch-only interactions.
- A settings entry for wallpaper mode, image selection, background dimming, and prayer location/calculation preferences.

Keep this as a visual direction rather than copying Apple assets or branding.

## Wallpaper design

### Static image
- Prefer Android's system Photo Picker on supported versions, with a compatible fallback for older devices.
- Persist the selected URI permission when the provider supports it.
- Store the URI and display preferences in app preferences.
- Load images asynchronously and downsample to the display size; do not decode full-resolution images on the UI thread.
- Provide a fallback background if the URI is revoked or the media is removed.

### Live wallpaper
- First determine whether the existing launcher draws its own background or delegates to Android's system wallpaper.
- If the launcher can display the system wallpaper, use that integration rather than implementing a second wallpaper engine.
- If it cannot, add a controlled renderer/preview path and make the limitation explicit. A normal `WallpaperService` is not automatically a view that can be embedded inside another activity.
- Pause or reduce animation work while the launcher is not visible and test on low-memory TV devices.

## Prayer-times design

- Show Subuh, Syuruq (optional), Zuhur, Asar, Magrib, and Isya.
- Highlight the next prayer and show a countdown using the device's local time.
- Let users select city/location manually; avoid requiring continuous location access for a fixed city.
- Make calculation method, Asar juristic method, time zone, and per-prayer minute adjustments configurable.
- Cache the current schedule and handle date/time-zone changes.
- Clearly label the calculation source/method. Do not hardcode example times as real prayer schedules.
- Keep calculations behind a repository/interface so a tested calculation library or trusted API can be integrated without coupling the UI to network calls.

## Suggested implementation sequence

1. **UI audit:** identify the actual home-screen view hierarchy, focus/card adapters, background rendering path, and settings preference structure.
2. **Visual foundation:** add theme/background overlay and tvOS-style focus states without replacing app discovery or launch logic.
3. **Static wallpaper:** add picker, persisted URI/preferences, async loading, and fallback handling.
4. **Live wallpaper:** implement only after confirming the current background rendering path and device constraints.
5. **Prayer panel:** add data model, calculation/provider abstraction, settings, next-prayer calculation, and D-pad-accessible presentation.
6. **Validation:** build debug APK; verify D-pad navigation, process restart, picker URI persistence, missing media, time-zone changes, and slow/low-memory TV hardware.

## Current limitations of this audit

The repository connector successfully reads the manifest and Gradle configuration, but its file search did not return the home-screen implementation, and several guessed source paths returned 404. Therefore this document intentionally does not claim that the features have been implemented or prescribe edits to unverified classes. Before modifying production UI, inspect the actual source tree and trace the existing home-screen and wallpaper code.
