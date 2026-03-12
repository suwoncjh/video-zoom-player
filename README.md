# Video Zoom Player (Android)

This app plays video with smooth pinch-to-zoom, drag-to-pan, and double-tap zoom behavior similar to Samsung Gallery playback.

## Features

- Pinch to zoom while video is playing (`1x` to `4x`)
- Drag to pan when zoomed
- Double-tap to zoom in / reset
- Clamp bounds so panning stays natural and seamless
- Pick a local video from storage, or play a default sample URL

## Project Structure

- `app/src/main/java/com/example/videozoomplayer/MainActivity.kt`
- `app/src/main/java/com/example/videozoomplayer/PlayerZoomController.kt`
- `app/src/main/res/layout/activity_main.xml`

## Run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run on a device/emulator with Android 7.0+.
4. Tap **Pick Video** to open a local file and test pinch zoom.

