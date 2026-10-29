# tvOS-Inspired Launcher, Wallpaper, and Prayer Times

## Repository baseline

- Repository: `b4byhuey/LeanbackLauncher`
- Base branch: `main`
- Application ID: `com.amazon.tv.leanbacklauncher`
- Version: `1.63`
- Min SDK 22; target SDK 29; compile SDK 35

## Implemented on `feature/tvos-wallpaper-prayer-widget`

- Added a D-pad-friendly TV customization activity with a dark, rounded-card visual treatment.
- Added gallery image selection through `ACTION_OPEN_DOCUMENT`, persisted URI permission when supported, and saved the URI in preferences.
- Updated the existing launcher wallpaper loader to read the selected content URI and retain legacy/default wallpaper fallback.
- Added an animated tvOS-inspired ambient background rendered inside the launcher, selectable from the customization activity.
- Added a prayer-times card to the home screen. It loads Fajr, Dhuhr, Asr, Maghrib, and Isha from the Aladhan address API using the saved city and refreshes periodically.
- Registered the customization activity with the Android TV `LEANBACK_LAUNCHER` category so it can be opened from the TV app launcher.
- Kept the work on a feature branch; `main` has not been changed.

## How to use

1. Install the built APK and open **TV Home Customization** from the TV app list.
2. Choose **Choose photo from gallery** to save a photo background.
3. Choose **Animated tvOS ambience** to enable the animated background inside the launcher, or **Use photo wallpaper** to turn that mode off.
4. Enter the city/area and select **Save prayer-time location**. The prayer panel appears on the launcher home screen.

## Known limitations / follow-up

- The animated mode is an in-launcher renderer, not an Android `WallpaperService`; the separate system live-wallpaper chooser opens device-provided live wallpaper options. This avoids implying that a system wallpaper automatically embeds into an app view.
- Prayer times currently come from the Aladhan API using calculation method 20 (Indonesia). The panel displays the five daily prayers plus a next-prayer countdown. The calculation method is not yet configurable, and the countdown uses the device's local clock, so devices set to a different time zone than the selected city need on-device verification.
- The gallery image decode currently follows the launcher's existing wallpaper bitmap path; large images and low-memory TV devices still need performance testing/downsampling.
- A provider may revoke a selected URI or remove the image; fallback behavior should be tested on-device.
- A full visual replacement of the existing app rows/cards has not been attempted. This pass adds the tvOS-inspired background, prayer panel, and a matching customization screen while preserving existing launcher navigation.
- Build and on-device tests have not been run in this environment. Please build the branch and verify D-pad navigation, gallery persistence after reboot, the prayer API/network failure state, and behavior on the target TV device.
