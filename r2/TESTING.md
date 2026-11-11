# STORM R R1 testing

The build is pinned to Revolution v1.1.11. Check results belong to this repository and APK; STORM 2/3 results are not reused as proof.

## Before assembly

`r2/preflight.py` runs isolated save operations, session-file bounds, FPS formatting, driver archive/policy checks, renderer-report checks, configuration/display math and six member-linkage regression tests. Save fixtures use Revolution paths; they never access installed game saves.

`r1/build_r1.py --check-only --r2 ...` prepares the complete patch tree and compiles the native helpers without assembling or signing an APK. It checks native logging under ASan/UBSan, English translation, callback/member linkage, smali labels/registers, the stock controller mapping screen, original touch/editor behavior, native executable/linkage sections, unchanged libraries/assets/bundled saves and license bodies.

## Android checks

The Android workflow builds an ephemeral test APK and runs launcher, mapping, save ZIP reimport, game menu, overlay sizing/collapse/rendering, closing-state, driver selection and logging tests at font scales 1.0 and 1.3. A separate startup smoke test opens the actual Revolution game activity and checks SDL main and the missing-game-file boundary. No game data is downloaded for CI.

[Android validation run 43056432875](https://github.com/XHYN-PH/STORM-R-DROID/actions/runs/43056432875) passed against runtime source `8919e3c8a95a9217c0efd46f4dd04ff35c64e04a` on Android 11 with ARM translation at font scales **1.0 and 1.3**, with **137 instrumentation assertions per run**. Both actual Revolution game-activity smoke tests reached SDL main and the expected missing-game-file dialog with the game process alive. Session version/display metadata was also checked.

The run covers the installed name/circular icon, original Send log/footer, settings layout, driver imports and configuration, save ZIP reimport/restore and 100% protection, cached pressed/unpressed controls, compact-layout alpha bounds, moving-stick artwork, statistics, game menu, shutdown coordination, logging JNI and stock controller mappings. The pressed-state cache was corrected before this passing run. Screenshot comparisons wait for a committed hardware frame and retain the existing pixel tolerance.

Full screenshots are retained as workflow artifacts. Long inline preview chunks from this run were truncated by GitHub annotations and cannot establish visual correctness; they were not used as evidence of a successful visual review. Future preview output uses 4000-character chunks to fit the annotation limit.

CI tests cannot prove gameplay performance, physical controller behavior, native save completion or PanVK/RADV gameplay compatibility on a phone.

## Assembly

The build repeats source checks, then verifies signing, archive integrity, ARM64 helper exports and 16 KiB ZIP alignment. Signing keys/passwords and decoded upstream content are excluded from Git.
