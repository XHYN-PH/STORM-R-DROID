# Build STORM R R1

This repository adapts the retained STORM 3 R1–R2.4 customization modules to the pinned STORM R v1.1.11 APK. The historical `r1` and `r2` directory/package names are build internals; the installed app is **STORM R R1** (`com.naruto.storm4`, version code **122**).

## Dependencies

Use Python 3.11+, OpenJDK 21, GCC (host sanitizer checks), Apktool 3.0.3, Android API 30 `android.jar`, Android build-tools 35.0.0 and NDK r28c. Use your own private PKCS12 signing key; no signing secrets are included in this repository.

Pinned upstream APK: [NarutoStormRevolution-PortNativo-v1.1.11-arm64.apk](https://github.com/Masterllgod/naruto-storm-revolution-port-nativo/releases/download/v1.1.11/NarutoStormRevolution-PortNativo-v1.1.11-arm64.apk).

SHA-256: `38f2435a5dd517da1acbe133615eeb9a021857daf4923d9c996d1bd76b94b567`.

Apktool 3.0.3 SHA-256: `dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423`.

## Build

Add `--check-only` to the build command to prepare and verify the complete patched source tree without assembling or signing an APK.

```sh
python3 r2/preflight.py --android-jar /path/to/android.jar \
  --build-tools /path/to/build-tools/35.0.0 \
  --upstream /path/to/upstream.apk --apktool /path/to/apktool.jar

python3 r1/build_r1.py --r2 --android-jar /path/to/android.jar \
  --build-tools /path/to/build-tools/35.0.0 --ndk /path/to/android-ndk-r28c \
  --upstream /path/to/upstream.apk --apktool /path/to/apktool.jar \
  --keystore /private/release.p12 --password-file /private/release.pass \
  --alias xhyn-r1 --output /path/to/STORM-R-R1-arm64.apk
```

The `--r2` option applies the complete retained feature set and is required for this edition. Signature-only upstream stubs are excluded from the runtime DEX. Patch targets must match the pinned APK; mismatched hashes/methods cause an error instead of applying unchecked changes.

The build checks the engine's executable/linkage sections, original libraries, bundled save and other assets against upstream. Only the listed native display strings are translated in place. The launcher, dialogs and Android integration are customized separately. See [testing coverage](TESTING.md) and [driver requirements](DRIVERS.md).

## Source limitations

The original STORM R engine is closed source. This repository publishes the customization code, translations, patches, helper source and build scripts, not a complete game engine or any game files. Customization code is MIT-licensed; original licenses/notices remain in force.
