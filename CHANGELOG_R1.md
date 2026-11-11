# STORM R R1

Modified and Translated by XHYN_PH. Based on the original STORM Revolution Android port v1.1.11.

- English launcher, settings, controller labels, messages and native status text.
- Renamed the app to **STORM R**, keeping the original circular Revolution artwork.
- Replaced the PT-BR switch with **Logging**, off by default, and fixed game language to English.
- Updated the YouTube footer to **Modified and Translated by XHYN_PH**, opening https://youtube.com/@xhyn_ph.
- Removed decorative click particles while retaining simple bounce and press feedback.
- Refined launcher dialogs and the in-game menu with rounded dark panels and clearer controls.
- Added Fit, Stretch and proportional Crop options, plus brightness, shadow quality, GPU memory budget and streaming cache settings.
- Added save export, backups and restore tools adapted to Revolution’s `STORMR.S` saves and 100% save marker.
- Added a smaller FPS/statistics panel, adjustable from 50–125%, with compact mode and separate game/output FPS.
- Added confirmed return to the launcher, closing feedback and save-management blocking while the game shuts down.
- Retained Turnip imports and added experimental PanVK/Mali and RADV/Xclipse package support on compatible hardware.
- Kept the original **Send log** action and attachments, adding session details when logging is enabled.
- Brought over background statistics sampling, cached control artwork, bounded overlay compositing, shared animation-frame redraws and launcher background suspension.
- Directed update checks to this repository.

The original controller mappings and touch input are retained. Removed STORM 3 additions—rumble/right-stick helpers, controller preview, performance history, the added diagnostics exporter and the added performance menu—are excluded here too.

The engine remains closed source. These Android-side changes do not guarantee higher gameplay FPS or fix native engine crashes. Game files are not included.
