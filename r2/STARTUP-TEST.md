# STORM R startup compatibility builds

These builds investigate the startup crash reported with Turnip Gen8 V39-patched in R1.

Trace 2 completed a 3-minute 26-second session on Adreno 750 with Turnip Gen8 V39-patched at 1080p, FXAA+, FSR and 2× frame generation, followed by a normal exit. The regular R1.1 build uses the same original startup path and disables automatic game and session log capture.

## Turnip crash trace

Startup test 2 loads Turnip Gen8 V39-patched and begins rendering. On the affected phone it exits with status 3 both with AMD frame generation on and off. This matches the original engine's fatal signal handler, which calls `_exit(3)` and leaves Android a self-exit record.

Build with `--turnip-trace` to replace the call at the end of that handler with Android libc's `abort()`. This profile keeps Startup test 2's menus, startup behavior and save handling. The handler still returns normally from recoverable signals. The build verifies the original instruction and both libc call targets against the pinned engine.

The traces captured with frame generation off and at 2× show the same failure in a data-copy routine of the original Revolution engine. Turnip is loaded and the renderer has already produced frames. The recorder's `SIGABRT` comes from the diagnostic handler. The stack identifies the failing routine and leaves the underlying cause open.

`R1-turnip-trace2` uses version code 122 so it can install over existing XHYN_PH builds. Its **Send log** includes the matching Android exit and a backtrace with module names and relative addresses when Android provides one. The first trace build also shared `tombstone.pb` through the stock log exporter. The second trace build shares the summary in `session-details.txt` and excludes raw crash dumps. It uses the recorded crash time to match renderer logs written after the last activity snapshot. Android CI checks this with a controlled crash.

Choosing **More options → Display → Shadows → Auto (original)** now clears a previously applied shadow cap from the launch configuration. This restores the engine's default for that setting. Turnip gameplay still requires verification on the affected phone.

## Startup test 2

Keeps the original game initialization, UI-thread JNI polling, refresh-rate handling, SDL lookup and touch drawing. The native file-log interception remains bypassed; native logs follow the original app.

Restores the shared STORM 2/3 presentation that test 1 unnecessarily reverted: Material game/dialog theme, compact adjustable FPS/statistics drawing, Crop and touch visibility, and the configuration extension for brightness, shadows and memory options. These do not start the added native-statistics worker. The English launcher, STORM R branding, driver imports, original controller mapping, modern in-game menu, save export/backups, 100% save protection and original Send log are retained.

The uploaded `10.7z` is a working system-driver session lasting about 142 seconds. `0.7z` is a failed Turnip Gen8 V39-patched session; it loads Vulkan, prepares all 413 pipelines and renders initial frames, then stops logging after about 5.9 seconds. Both logs use 2x frame generation. No crash stack in these archives identifies the exact native fault. This does not establish that all Turnip versions fail, or that frame generation is the cause.

## Installation and rollback

Build with the normal documented command plus `--startup-test`. Test 2 is `R1-startup-test2`, package `com.naruto.storm4`, code 122, signed with the same XHYN_PH key as R1. Install over R1/test 1; do not uninstall or clear app data. R1/test 1 can also be reinstalled without a downgrade flag. No save or preference tree is deleted. Bundled saves and engine code are checked against upstream.

The source checks compare actual initialization, polling, refresh, SDL input and touch methods against the translated upstream tree. Android CI checks the shared game theme, functional settings writer, statistics resizing/collapse, stock Send log, save safeguards and menus. The smoke test reaches the real native loader with an empty fixture and confirms the additional native logging library/worker are absent. It cannot prove gameplay compatibility without the phone and game files.

The system driver is the working option reported on the affected phone. If comparing the fork with the original release, keep the game folder, graphics settings and driver version consistent across both runs.

## Startup test 1

Test 1 also reverted the game theme, statistics drawing and additional configuration extension. The user confirmed system-driver gameplay, but reported the old dialog styling and a Turnip black screen/crash. Test 2 restores the presentation without replacing the original native startup/polling.
