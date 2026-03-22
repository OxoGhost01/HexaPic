# HexaPic

An Android gallery app inspired by the iPhone Photos app. Free, open-source, non-commercial.

## Features

### v1.0 — Foundation
- Reads photos and videos from device storage via MediaStore
- 4-column grid (portrait) / 6-column grid (landscape), square cells, 1dp gaps
- Runtime permission handling (READ_MEDIA_IMAGES + READ_MEDIA_VIDEO on Android 13+, READ_EXTERNAL_STORAGE on Android ≤12)
- Empty state with Settings deep link when permission is denied
- Thumbnails loaded with Coil (placeholder → crossfade transition)
- Bottom navigation: Library, For You, Albums, Search
- Tab state persists on switch (scroll position, no fragment recreation)
- Tapping the active tab scrolls the grid back to top

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

No internet permission. All processing is on-device.

## Tech Stack

- Kotlin
- AndroidX Fragment + ViewModel + LiveData
- RecyclerView + GridLayoutManager
- Coil (image loading)
- Material Components 3
- ViewBinding

## License

HexaPic Non-Commercial License — see [LICENSE](LICENSE).
