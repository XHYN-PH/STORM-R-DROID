# STORM R R1 testing

The build is pinned to Revolution v1.1.11. Check results belong to this repository and APK; STORM 2/3 results are not reused as proof.

## Before assembly

`r2/preflight.py` runs isolated save operations, session-file bounds, FPS formatting, driver archive/policy checks, renderer-report checks, configuration/display math and six member-linkage regression tests. Save fixtures use Revolution paths; they never access installed game saves.

`r1/build_r1.py --check-only --r2 ...` prepares the complete patch tree and compiles the native helpers without assembling or signing an APK. It checks native logging under ASan/UBSan, English translation, callback/member linkage, smali labels/registers, the stock controller mapping screen, original touch/editor behavior, native executable/linkage sections, unchanged libraries/assets/bundled saves and license bodies.

## Android checks

The Android workflow builds an ephemeral test APK and runs launcher, mapping, save ZIP reimport, game menu, overlay sizing/collapse/rendering, closing-state, driver selection and logging tests at font scales 1.0 and 1.3. A separate startup smoke test opens the actual Revolution game activity and checks SDL main and the missing-game-file boundary. No game data is downloaded for CI.

Android results are pending until a run for this repository completes. CI tests cannot prove gameplay performance, physical controller behavior, native save completion or PanVK/RADV gameplay compatibility on a phone.

## Assembly

The build repeats source checks, then verifies signing, archive integrity, ARM64 helper exports and 16 KiB ZIP alignment. Signing keys/passwords and decoded upstream content are excluded from Git.
