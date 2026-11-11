<p align="center">
  <img src="docs/icon.svg" width="128" alt="Sticker Maker icon">
</p>

<h1 align="center">Sticker Maker</h1>

<p align="center">
  Turn a photo into a WhatsApp sticker in a few taps. No ads, no tracking, no network.
</p>

## What it does

1. Share a photo from Google Photos (or any gallery app), or open the app and pick one.
2. Cut it out: freehand, square, circle, polygon, or **Magic** for an automatic cutout.
3. Add text and an outline.
4. Tap **Copy**, then paste the sticker in a WhatsApp chat.

Everything runs on the device. The app has no `INTERNET` permission, and your photos never leave the phone.

## Install

Download the APK from the [latest release](https://github.com/damianogiusti/android-sticker-maker/releases/latest)
and open it on your phone. Android 7.0 or later.

## Good to know

- **Magic** uses an on-device DeepLab v3 model. It recognizes 21 classes: people, pets, vehicles and some
  common objects. For other subjects, cut by hand.
- The sticker goes to the clipboard as PNG. WhatsApp does not paste a WebP image from the clipboard.
- On Android 13 and later the system shows its own clipboard preview, so the app shows no extra message.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug                    # debug APK
./gradlew testDebugUnitTest :domain:test   # unit tests
./gradlew :app:assembleRelease             # R8-minified release APK
```

The release build is signed only if a `keystore.properties` file exists in the repo root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it, the APK is unsigned.

Stack: Kotlin, Jetpack Compose, Koin, Arrow, kotlinx.serialization, LiteRT (TensorFlow Lite).

```
app/             entry point, share target, navigation, Home, About
core-ui/         theme and shared composables
domain/          pure Kotlin: clipboard port, image format, limits
imaging/         cutout, segmentation, outline, text, export
feature-editor/  cut and text screens, Copy, clipboard FileProvider
```

## Credits

- Segmentation model: [DeepLab v3](https://github.com/tensorflow/models/tree/master/research/deeplab)
  (MobileNetV2, PASCAL VOC), the official TensorFlow Lite example model, Apache License 2.0.
  Details in [`imaging/MODEL_LICENSE.md`](imaging/MODEL_LICENSE.md).
- Fonts from [Google Fonts](https://github.com/google/fonts), SIL Open Font License 1.1: Alfa Slab One,
  Anton, Comic Neue, Cutive Mono, DynaPuff, Freckle Face, Fredoka, Nunito, Sedgwick Ave Display.
  License texts in `imaging/src/main/assets/licenses/`.

WhatsApp is a trademark of its owner. This project is not affiliated with WhatsApp or Meta.
