# HexaPic

An Android gallery app inspired by the iPhone Photos app. Free, open-source, non-commercial.

## Features

- Browse all your photos and videos in a fast, clean grid
- Photos grouped by month with sticky date headers
- Tap a photo to open it full-screen with a fluid hero transition
- Pinch, double-tap, and swipe to navigate — feels native
- **Video playback** — scrubber, seek preview, mute, no autoplay
- Bottom navigation: Library, For You, Albums, Search
- Auto-updates — new versions install in one tap

## Requirements

- Android 8.1+ (API 27)
- Android Studio Meerkat or newer
- Gradle 9.2+

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Permissions

| Permission | Purpose |
|---|---|
| READ_MEDIA_IMAGES | Access photos (Android 13+) |
| READ_MEDIA_VIDEO | Access videos (Android 13+) |
| READ_EXTERNAL_STORAGE | Access media (Android ≤12) |
| INTERNET | Check for updates from GitHub releases |
| REQUEST_INSTALL_PACKAGES | Install downloaded update APK |

## Tech Stack

- Kotlin
- AndroidX Fragment + ViewModel + LiveData
- RecyclerView + GridLayoutManager
- Coil (image loading)
- Media3 / ExoPlayer (video playback)
- Material Components 3
- ViewBinding

## License

HexaPic Non-Commercial License — see [LICENSE](LICENSE).
