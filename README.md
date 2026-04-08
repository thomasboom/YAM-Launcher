<p align="center">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/featureGraphic.png" width=100%/>
</p>

> [!NOTE]
> GitHub suspended my personal account and the YAM Launcher repository. While both are back up, I've permanently moved all code hosting to Codeberg. The GitHub mirror remains available for browsing, but contributions (PRs, issues) happen on Codeberg only.
# YAM Launcher

YAM (Yet Another Minimalist) Launcher is a minimalist, open, customisable and text-based launcher for Android.

Key features:
- No flashy effects, icons or other distractions. Perfect for digital minimalism.
- Customizable shortcuts for your most used apps.
- Weather integration with [Open-Meteo](https://open-meteo.com/). (optional)
- Work profile support.
- Search on the bottom of the screen.
- Contacts searching functionality. (optional)
- Customizable fonts and font styles, defaults to your system font.
- Material You support.
- Simple by default, customizable under the hood. 
- Accessibility Actions support.
- Open-source under the MIT License.

## Installation

### F-Droid
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/en/packages/eu.ottop.yamlauncher)

### IzzyOnDroid
[<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/badges/izzyondroid.png"
alt="Get it on IzzyOnDroid"
height="55">](https://apt.izzysoft.de/fdroid/index/apk/eu.ottop.yamlauncher)

### Google Play
[<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/badges/googleplay.png"
    alt="Get it on Google Play"
    height="55">](https://play.google.com/store/apps/details?id=eu.ottop.yamlauncher)

### APK

1. Download the [APK](https://codeberg.org/thomasboom/YAM-Launcher/releases).
2. Enable the permission to install apps for your browser/files (app you are installing the launcher from).
3. Install the launcher.

#### Important for APK installations

If you wish to use the double tap to lock feature, Android blocks the accessibility settings for manually installed APKs by default. 

To fix (only if you want to enable the double tap to lock feature):

1. Go to Settings -> Apps -> YAM Launcher
2. Click on the three-dot button on the top right of the screen. 
3. Allow restricted settings

## Screenshots

<p align="center">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/phoneScreenshots/1.png"
    alt="Weather At Your Fingertips"
    width="19%">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/phoneScreenshots/2.png"
    alt="Absolute Minimalism"
    width="19%">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/phoneScreenshots/3.png"
    alt="Function Over Form"
    width="19%">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/phoneScreenshots/4.png"
    alt="Material You"
    width="19%">
<img src="https://codeberg.org/thomasboom/YAM-Launcher/raw/branch/main/metadata/en-US/images/phoneScreenshots/5.png"
    alt="Make It Yours"
    width="19%">
</p>

## Privacy

Read the full privacy policy [here](https://codeberg.org/thomasboom/YAM-Launcher/src/branch/main/PrivacyPolicy.md).

No data leaves your device by default and no data is sent to the developer, ever. 

Enabling weather integration will send the coordinates that are set for the weather to Open-Meteo and they may also store your IP address.

### Permissions

- `REQUEST_DELETE_PACKAGES` - Allows uninstallation of apps through the launcher's activity menu.
- `EXPAND_STATUS_BAR` - Allows pulling down the status bar from the app.
- `INTERNET` - Required for API calls to Open-Meteo. Only used if weather integration is enabled.
- `ACCESS_COARSE_LOCATION` - Requested when enabling GPS location. Needs to be allowed to use GPS location.
- `READ_CONTACTS` - Used to find contacts. Only necessary if the contacts menu is enabled. 
- `SET_ALARM` - Used for the clock opening gesture on clicking the time. 
- `QUERY_ALL_PACKAGES` - Used to ensure that the app properly detects all installed apps. 

### Accessibility Services

You can optionally enable accessibility services for YAM Launcher if you choose to use the "Double Tap to Lock Screen" gesture. The accessibility services are exclusively used to lock the screen and are not used to collect any data.

## Repositories

[Codeberg (main)](https://codeberg.org/thomasboom/YAM-Launcher/)

[GitHub (secondary, read-only)](https://github.com/ThomasNowProductions/YAM-Launcher/)
