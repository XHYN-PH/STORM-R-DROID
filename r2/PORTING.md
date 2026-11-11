# STORM Revolution integration

The retained customization code comes from STORM 2 R1 and STORM 3 R1–R2.4. The engine, libraries and bundled saves come from the pinned Revolution v1.1.11 APK.

| Hook | Revolution v1.1.11 |
| --- | --- |
| App package | `com.naruto.storm4` (preserved upstream identity) |
| Engine library | `librevolution.so` |
| Launcher settings / preferences | `v` / `c` |
| Configuration writer | `z()` |
| Card / switch row / button builders | `w()` / `v()` / `j()` |
| Switch interface callback | `a.u0.a(boolean)` |
| Driver descriptor / importer / menu gate | `a.h` / `a.i.k(Context, InputStream)` / `a.i.q()` |
| Selected driver / selection | `q()` / `p(String)` |
| Original Send log | `r(boolean)`; all eight original attachments retained |
| Touch view / button / stick / listener | `a.l0` / `a.i0` / `a.k0` / `a.j0` |
| Statistics / floating menu / drag | `a.g0` / `a.f0` / `a.e0` |
| Native polling dispatcher | `a.H` |
| Save directory / file | `data_revolution/content/stormr.s` / `STORMR.S` |
| Title ID | `4E4D0868` |
| 100% marker / preserved save | `save100-revolution.active` / `save100-original-revolution` |
| Bundled 100% save | `assets/save100/revolution/STORMR.S` |

Native JNI inspection confirms two integers for shader preparation and seven floats for statistics. The logging helper targets `librevolution.so`; its helper JNI package remains `com.naruto.port`.

The original controller mappings and every touch/editor method are checked against the translated upstream tree. Only the retained rendering cache, alpha-layer bounds and redraw scheduling hooks are allowed. No new axis, R3 or rumble implementation is added.

Only exact, length-bounded native display strings may change. Executable and linkage sections, other upstream libraries, assets, all bundled saves and legal bodies are verified against the pinned APK before assembly.

The twelve new proposals in the November 11 STORM 2/3 audit are deferred. This transfer does not claim those additional fixes.
