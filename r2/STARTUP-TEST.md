# STORM R startup compatibility builds

These builds investigate startup failures in R1. The engine is closed source; neither test is a confirmed Turnip fix.

## Startup test 2

Keeps the original game initialization, UI-thread JNI polling, refresh-rate handling, SDL lookup and touch drawing. The native file-log interception remains bypassed; native logs follow the original app.

Restores the shared STORM 2/3 presentation that test 1 unnecessarily reverted: Material game/dialog theme, compact adjustable FPS/statistics drawing, Crop and touch visibility, and the configuration extension for brightness, shadows and memory options. These do not start the added native-statistics worker. The English launcher, STORM R branding, driver imports, original controller mapping, modern in-game menu, save export/backups, 100% save protection and original Send log are retained.

The uploaded `10.7z` is a working system-driver session lasting about 142 seconds. `0.7z` is a failed Turnip Gen8 V39-patched session; it loads Vulkan, prepares all 413 pipelines and renders initial frames, then stops logging after about 5.9 seconds. Both logs use 2x frame generation. No crash stack in these archives identifies the exact native fault. This does not establish that all Turnip versions fail, or that frame generation is the cause.

## Installation and rollback

Build with the normal documented command plus `--startup-test`. Test 2 is `R1-startup-test2`, package `com.naruto.storm4`, code 122, signed with the same XHYN_PH key as R1. Install over R1/test 1; do not uninstall or clear app data. R1/test 1 can also be reinstalled without a downgrade flag. No save or preference tree is deleted. Bundled saves and engine code are checked against upstream.

The source checks compare actual initialization, polling, refresh, SDL input and touch methods against the translated upstream tree. Android CI checks the shared game theme, functional settings writer, statistics resizing/collapse, stock Send log, save safeguards and menus. The smoke test reaches the real native loader with an empty fixture and confirms the additional native logging library/worker are absent. It cannot prove gameplay compatibility without the phone and game files.

For the Turnip investigation, keep the same folder/driver and explicitly test frame generation Off, then collect a new Send log if it fails. Until compatibility is established, the system driver is the working option reported on the affected phone.

## Startup test 1

Test 1 also reverted the game theme, statistics drawing and additional configuration extension. The user confirmed system-driver gameplay, but reported the old dialog styling and a Turnip black screen/crash. Test 2 restores the presentation without replacing the original native startup/polling.
