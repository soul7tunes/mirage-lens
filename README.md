# Mirage Lens

An offline on-device AI camera for Android, powered by the
[Mirage](https://cactuscompute.com/blog/mirage) vision model
(Cactus Compute, 51.3M parameters, 22 MB, Apache-2.0).

Take a photo or pick one from the gallery, and the app will:

- Describe the scene
- Read text in the photo (OCR)
- Find objects and draw boxes around them

Everything runs on the phone. No internet, no server, no account,
no ads, free.

## What works well

- Clean printed **English** text: signs, labels, documents.
- Scene description and object detection with boxes.

## Known limits (tested 19 Nov 2026)

- **Hindi / Devanagari text is not read correctly** by the model.
  It guesses Latin-looking letters instead.
- Small or crowded text (for example a dense lecture slide)
  is only partly read.

## Tech

- Native Kotlin + CameraX + JNI
- Cactus engine (`libcactus.a`) + `mirage.cact` weights, loaded
  from the app assets and called through `cactus_load` /
  `cactus_describe` (see `app/src/main/cpp/native-lib.cpp`)
- Universal APK: `arm64-v8a` + `armeabi-v7a`, minSdk 24 (Android 7.0+)

## Build

Push a tag `vX.Y.Z` and GitHub Actions builds the release APK
and attaches it to the release. Or build locally:

```sh
gradle assembleRelease
```

## License

- App code: MIT (see `LICENSE`)
- Mirage model weights and Cactus engine: Apache-2.0, by Cactus Compute
