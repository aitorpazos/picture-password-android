# Picture Password for Android

A shoulder-surfing resistant lock screen app inspired by [BlackBerry's Picture Password](https://en.wikipedia.org/wiki/BlackBerry_10#Security). Choose a photo, pick a secret number and a secret spot — then unlock by dragging the number grid to align your number with your spot. The gesture looks different every time, so observers can't figure out your password.

## How It Works

1. **You choose a photo** from your gallery as the lock screen background
2. **You pick a secret digit** (0–9) and a **secret location** on the photo
3. A **number grid** is overlaid on the photo — its layout **randomises every time** (different positions, different grid density)
4. **To unlock**, drag the grid until your secret digit sits over your secret spot
5. An observer sees a different gesture each time — they can't tell which number or which spot matters

<p align="center">
  <em>Same password, different gesture every time → shoulder-surfing resistant</em>
</p>

## Features

- 🔒 **Shoulder-surfing resistant** — the unlock gesture changes every attempt
- 🔀 **Variable grid density** — grid columns randomise between 5–8 per attempt
- 📷 **Any photo** — use any image from your gallery
- 🛡️ **Lock screen service** — runs as a foreground service, launches on screen off
- 🔐 **Biometric integration** — use Picture Password alone, biometrics alone, both as 2FA, or either
- 🚫 **Screenshot protection** — `FLAG_SECURE` blocks screenshots and screen recording
- 🔄 **Survives reboot** — auto-starts on device boot
- ⚙️ **Permissions guide** — in-app checklist with direct links to all required settings
- 🆓 **Free and open source** — GPLv3 licensed, no ads, no tracking

## Download

Grab the latest APK from [**GitHub Releases**](https://github.com/aitorpazos/picture-password-android/releases):

| APK | For |
|-----|-----|
| `arm64-v8a` | **Most modern phones** (Pixel, Samsung, OnePlus…) |
| `armeabi-v7a` | Older 32-bit ARM phones |
| `x86_64` | Emulators, Chromebooks |
| `x86` | Older emulators |
| `universal` | Works on everything (larger file) |

> **Tip:** If you're unsure, download `arm64-v8a` — it covers the vast majority of modern Android devices.

## Setup Guide

### 1. Install the APK

Download and install. You may need to allow "Install from unknown sources" for your browser or file manager.

### 2. Set Up Your Password

1. Open **Picture Password**
2. Tap **Set Up Password**
3. **Step 1** — Choose a photo from your gallery
4. **Step 2** — Tap the spot on the photo that will be your secret location
5. **Step 3** — Pick your secret digit (0–9) by dragging the grid so your chosen number aligns with the spot you just picked
6. Done! Tap **Test Unlock** to practice

### 3. Enable as Lock Screen

1. Tap **Enable as Lock Screen** on the main screen
2. Choose your **unlock mode**:
   - **Picture Password only** — just the grid unlock
   - **Biometrics only** — fingerprint/face only
   - **Both required (2FA)** — picture password first, then biometrics
   - **Either unlocks** — whichever method succeeds first
3. Grant the required permissions (see below)
4. Go to **Android Settings → Security → Screen Lock** and set it to **Swipe** or **None** so Picture Password becomes your primary lock

### 4. Grant Permissions

The app shows a permissions checklist with ✅/❌ status. Tap each item to open the correct settings screen:

| Permission | Why | How |
|------------|-----|-----|
| **Allow restricted settings** | Required before overlay permission can be granted (Android 13+) | App Info → ⋮ menu → *Allow restricted settings* |
| **Display over other apps** | Shows the lock screen on top of everything | Settings → Apps → Picture Password → Display over other apps |
| **Notifications** | Required for the foreground service | Grant when prompted, or Settings → Apps → Notifications |
| **Unrestricted battery** | Prevents Android from killing the service | Settings → Apps → Battery → Unrestricted |
| **Set system lock to Swipe** | Makes Picture Password the primary lock screen | Settings → Security → Screen Lock → Swipe |

> **Important:** On Android 13+, you must enable **"Allow restricted settings"** first, otherwise the overlay permission toggle will be greyed out.

## Requirements

- Android 9 (Pie) or newer
- A photo you'll remember the details of

## Building from Source

```bash
git clone https://github.com/aitorpazos/picture-password-android.git
cd picture-password-android
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Running Tests

```bash
./gradlew testDebugUnitTest
```

## FAQ

**Q: Can someone bypass it by just swiping away?**
A: If you set Android's system lock to "Swipe", the Picture Password overlay appears first. They'd need to unlock Picture Password before reaching the home screen. For maximum security, keep a system PIN/biometric as well and use the "Both required" mode.

**Q: What if I forget my picture password?**
A: If you have a system PIN/biometric set as backup, you can still get into your phone. Then open the app and re-set your picture password. If you've disabled the system lock entirely, you'd need to factory reset — so keep a backup authentication method.

**Q: Why does the grid look different each time?**
A: That's the core security feature. The grid density (5–8 columns) and digit positions randomise every attempt, so the drag gesture is never the same twice. An observer can't reproduce what they saw.

**Q: Why is there a persistent notification?**
A: Android requires foreground services to show a notification. This keeps the lock screen service alive. You can minimise the notification channel in Android settings if it bothers you.

**Q: Does it work with fingerprint/face unlock?**
A: Yes! Choose "Both required" for 2FA (picture password + biometrics), "Either unlocks" to use whichever is faster, or "Biometrics only" / "Picture Password only" for single-method unlock.

## Security Notes

- Your secret (digit + location) is stored locally using Android's encrypted SharedPreferences
- `FLAG_SECURE` prevents screenshots and screen recording on the lock and setup screens
- The number grid uses a cryptographically seeded random layout each time
- No data is sent anywhere — the app is fully offline

## License

[GNU General Public License v3.0](LICENSE)

## Contributing

Issues and pull requests welcome at [github.com/aitorpazos/picture-password-android](https://github.com/aitorpazos/picture-password-android).
