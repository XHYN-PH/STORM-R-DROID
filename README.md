<div align="center">
  <img src="docs/screenshots/icone.png" alt="STORM R circular green and gold Leaf emblem" width="144">

# STORM R

**Modified and Translated by XHYN_PH**

An English Android edition of the STORM R native port, with refined menus, save tools and a smaller, adjustable FPS panel.

[**Download the latest APK**](https://github.com/XHYN-PH/STORM-R-DROID/releases/latest) · [**Telegram**](https://t.me/xhynports) · [**YouTube**](https://youtube.com/@xhyn_ph) · [**R1 changes**](CHANGELOG_R1.md)

</div>

## About this edition

STORM R is my modified English edition of the [original native port](https://github.com/Masterllgod/naruto-storm-revolution-port-nativo), based on Android **v1.1.11**. It runs native ARM64 code and renders through **Vulkan**.

The focus is a cleaner interface, more useful Android options and less unnecessary work in the menus and overlays. Performance and compatibility still depend on the phone, driver and game scene.

**Startup compatibility testing:** the system Vulkan driver has been reported working on an Adreno 750 phone. Turnip Gen8 V39-patched still produces a black screen/crash on that phone. [Startup test 2](https://github.com/XHYN-PH/STORM-R-DROID/releases/tag/R1-startup-test2) restores the STORM 2/3 menu styling, adjustable statistics and added display settings while retaining the original native startup/polling. It is a test build, not a confirmed Turnip fix. Native game logging follows the original app during this test.

## What changed from the original?

| Feature | Original app | This edition |
| --- | --- | --- |
| Language | Portuguese launcher and settings | English launcher, settings, controls and messages |
| App identity | Original name and icon | **STORM R** with the HD circular green and gold Leaf emblem |
| Menus and effects | Legacy dialogs and decorative particles | Rounded dark menus with gold accents; simple bounce and press feedback retained |
| FPS/statistics panel | Fixed size | Smaller by default, adjustable from **50–125%**, with separate game/output FPS and a compact mode |
| Save management | Save import | Import, export, backups and restoration safeguards |
| Display choices | Fit and Stretch | Fit, Stretch and proportional Crop to fill the display |
| In-game options | Basic display/control options | Touch visibility and opacity, brightness, panel size and confirmed return to launcher |
| GPU driver imports | Qualcomm-only picker | Turnip for Adreno, plus experimental PanVK for compatible Mali and RADV for compatible Xclipse |
| Logging | Regular app log capture | Optional **Logging**, off by default; original **Send log** retained with added session details |

The original Revolution launcher layout, touch controls, controller mapping screen and graphics controls are retained. Its existing PT-BR switch is replaced with Logging; game language is fixed to English. Interface refinements also reuse button artwork, collect statistics in the background and stop unnecessary polling when panels are hidden. See the [release history](#release-history) for changes in each version.

## Requirements

- **Android 11 or newer** on a **64-bit ARM device**, with Vulkan support.
- Your own copy of **Naruto Shippuden: Ultimate Ninja Storm Revolution for Xbox 360, supported North American / NTSC-U version**.
- The complete extracted game folder, containing the required `default.xex` and all game data.

**Game files are not included in the APK or this repository.**

## Install or update

1. Open the [latest release](https://github.com/XHYN-PH/STORM-R-DROID/releases/latest) and download **STORM.R.apk**.
2. Install the APK. Future releases signed with the same XHYN_PH key can update this edition without uninstalling it.
3. Open **STORM R** and grant file access when requested.
4. Select the extracted game folder containing `default.xex`, choose your settings, then tap **Play**.

This edition keeps the upstream Android package, `com.naruto.storm4`, and uses its own signing key. That package may also be used by other Revolution/Connections variants, so those installations can conflict. If Android reports a signing conflict with the original app or another build, **back up your saves before uninstalling it**.

<details>
<summary>Check the required game executable</summary>

The port requires the exact supported `default.xex`. A different region or executable revision may not work, even if the game title matches.

| File | Size | SHA-1 |
| --- | --- | --- |
| `default.xex` | 11,776,000 bytes | `c9c1cf1fc4d7a535542edb1aae19751d4ed7e090` |

Copy the complete extracted disc folder; selecting an executable alone is not enough. See the [original project's game requirements](https://github.com/Masterllgod/naruto-storm-revolution-port-nativo#readme) for more details.

</details>

## Useful options

- **Resize the FPS panel:** open **More options → Display → FPS / statistics size**. The same control is available in **Game options**, where resizing applies immediately. The default is 70%; Reset restores it. Drag the panel to move it, or use its minus button to collapse it.
- **Read the FPS panel:** **Game** is the game's frame rate; **Output** includes frame generation. Higher output FPS does not mean the game itself runs faster. `~` means an estimate. Compact mode uses **G** and **O** for the same two values.
- **Open Game options:** tap the floating menu button during gameplay, or use Android Back.
- **Return to the launcher:** wait for the game's save indicator to finish, then choose **Return to launcher** and confirm. Wait for **Closing game…** to finish before managing saves. Exiting does not create a manual save.
- **Export or back up saves:** open **More options → Saves**. Export reads your current save without changing it. Backup restoration is blocked while the game is running or the bundled 100% save is active. The bundled STORM R 100% save is preserved.
- **Fill the display:** **Fit** keeps the full image, **Stretch** changes its proportions, and **Crop** fills the display by cutting off edges while keeping proportions. Crop does not expand the game's camera view.
- **Collect a log:** enable **Logging**, restart the game, reproduce the issue, then use the original **Send log** link. App and session details are attached automatically.

## GPU drivers

The phone's **system Vulkan driver** is available by default. To try a custom driver, open **GPU Driver → Change → Import driver** and choose a compatible Android Vulkan ZIP. Close the game and wait for shutdown to finish before importing or changing drivers. Changes apply on the next game launch.

| Phone GPU | Custom driver option |
| --- | --- |
| Qualcomm Adreno | Compatible Turnip / AdrenoTools-format packages |
| ARM Mali | **Experimental** PanVK imports for supported Mali families |
| Samsung Xclipse | **Experimental** RADV Xclipse imports; the linked v1.8.0 package requires Android 14 or newer |

Drivers must match your GPU and Android version. PanVK and RADV Xclipse cannot replace Turnip on an Adreno phone. The linked FristOneRR PanVK 3.7.0 package excludes newer CSF/Immortalis GPUs.

Additional PanVK/RADV packages are downloaded separately. Successful import does not guarantee working gameplay; the Mali/Xclipse paths still need testing on real devices. See [driver requirements and downloads](r2/DRIVERS.md) for supported packages and restrictions.

## Updates and support

- **[Telegram — @xhynports](https://t.me/xhynports)**
- **[YouTube — @xhyn_ph](https://youtube.com/@xhyn_ph)**
- **[GitHub releases](https://github.com/XHYN-PH/STORM-R-DROID/releases)** — APK downloads and release notes.
- **[Report an issue](https://github.com/XHYN-PH/STORM-R-DROID/issues)** — describe what happened and how to reproduce it.

For bug reports, include your phone model, Android version, app version, GPU driver and the affected game mode or scene. If possible, attach a log collected using **Logging → restart game → reproduce issue → Send log**.

## Release history

- [R1 — English interface, refined menus, save tools, display options and experimental driver imports](CHANGELOG_R1.md).

This edition brings the retained customizations developed for [STORM 3 R1–R2.4](https://github.com/XHYN-PH/STORM-3-DROID) to STORM R. It uses Revolution’s own engine, `STORMR.S` save format and bundled 100% save. STORM 2 and 3 saves are incompatible. The rumble/right-stick helpers, controller preview, performance history and added diagnostics exporter that were removed from STORM 3 are excluded here too. The stock controller mapping screen and original Send log remain.

## Source code and limitations

The original port's engine is **closed source**, and I do not have access to its source code. This limits the bugs I can fix, the performance improvements I can make and the features I can add, especially inside the engine and renderer. Some crashes, graphical problems and compatibility issues require changes to that engine.

My customization code, translations, patches and build tools are published in this repository. They cover the launcher, interface, settings and Android integration; they are not the full engine source. See the [build instructions](r2/README.md).

Customization code is [MIT-licensed](r1/LICENSE). The original port and its components retain their existing authorship and licenses; see [Third-party notices](THIRD_PARTY_NOTICES.md). This is an unofficial modification, not an official game release.
