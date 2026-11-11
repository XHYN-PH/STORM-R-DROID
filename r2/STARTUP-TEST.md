# STORM R original-startup test

This diagnostic build investigates a black screen reported after Play in R1. It is not a confirmed gameplay fix. The original v1.1.11 reportedly starts on the affected phone; turning frame generation off in R1 did not resolve the black screen.

The test retains the English launcher, STORM R name/icon, save tools, driver imports, modern menus and original controls. It restores the original game initialization, UI-thread JNI polling, refresh-rate handling, touch/statistics drawing, SDL lookup and game-window theme. Native file-log interception and the additional configuration overrides are bypassed. Native logs follow the original app so that Send log remains useful for diagnosis. Advanced display/runtime refinements are intentionally not exercised by this profile.

The APK is signed with the same XHYN_PH key as R1 and keeps package `com.naruto.storm4` and version code 122. Android can install it over R1, and R1 can be installed over the test again without a downgrade flag. Do not uninstall or clear app data. No save or preference tree is deleted by the diagnostic patch; bundled save assets and the native engine are checked against upstream.

Build it with the normal documented command plus `--startup-test`. Its version name is `R1-startup-test1`. The normal build is unchanged when this flag is omitted, apart from the corrected resolution label in Send log.

Before assembly, `startup_test.verify` compares the actual initialization, original configuration writer, native polling dispatcher, refresh selection, SDL input and drawing methods with the translated upstream tree. Only the existing save lock and Java metadata calls extend initialization. The Android startup smoke test also checks that the real Revolution engine is loaded in `:game`, while the native log-interception library and added statistics worker are absent.

The CI profile on branch `fix/revolution-startup` validates the compatibility UI, save tools, stock log sharing and native loader boundary. Actual game startup still needs the affected phone and its game files. Do not label this a verified fix without that result.
