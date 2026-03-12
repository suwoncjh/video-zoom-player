# Video Zoom Player (Android)

This app plays video with smooth pinch-to-zoom, drag-to-pan, and double-tap zoom behavior similar to Samsung Gallery playback.

## Features

- Pinch to zoom while video is playing (`1x` to `4x`)
- Drag to pan when zoomed
- Double-tap to zoom in / reset
- Clamp bounds so panning stays natural and seamless
- Pick a local video from storage, or play a default sample URL
- For merged MP4 playback, decoded PCM is processed in 20ms chunks by native C++ and the processed output is played

## Project Structure

- `app/src/main/java/com/example/videozoomplayer/MainActivity.kt`
- `app/src/main/java/com/example/videozoomplayer/PlayerZoomController.kt`
- `app/src/main/java/com/example/videozoomplayer/NativeOutputAudioProcessor.kt`
- `app/src/main/java/com/example/videozoomplayer/NativePcmProcessor.kt`
- `app/src/main/java/com/example/videozoomplayer/PcmTapRenderersFactory.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/cpp/audio_processor_jni.cpp` (reference JNI implementation)

## Run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run on a device/emulator with Android 7.0+.
4. Tap **Pick Video** to open a local file and test pinch zoom.

## Native Audio Processor Integration

- The app now processes playback audio in real time through `NativeOutputAudioProcessor`.
- Audio is converted to interleaved `int32`, sent to native in 20ms blocks, then converted back and rendered.
- Native contract used by the bridge:
  - `process(int *out, const int *in, int length)`
  - `in/out`: interleaved `[mic1][mic2][mic3]...`
  - `length`: fixed `960` sample-frames (20ms at 48kHz)
- To connect your real C++ processor, provide a prebuilt shared library named `libpcmprocessor_jni.so` in:
  - `app/src/main/jniLibs/arm64-v8a/`
  - (and other ABIs if needed)
- JNI entry points expected by the app are in `NativePcmProcessor.kt` and shown in `audio_processor_jni.cpp`.
