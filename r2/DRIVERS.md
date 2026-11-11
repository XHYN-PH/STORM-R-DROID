# Custom Vulkan drivers in STORM R R1

Open **GPU Driver → Change → Import driver**, choose the original Android driver ZIP and select it. Use **System** to return to the phone's driver. Close the game and wait for Closing game… to finish before importing or changing drivers. A change applies to the next game launch.

| GPU family | Driver | Scope |
| --- | --- | --- |
| Qualcomm Adreno | Compatible Turnip or other AdrenoTools-format Android package | Existing import path retained; match the package to the GPU and Android version. |
| ARM Mali | [FristOneRR PanVK](https://github.com/FristOneRR/FristOneRR-Panvk-Driver/releases) | Experimental import/loading integration. Version 3.7.0 lists G52/G57/G68/G71/G72/G76/G77/G78 families; this package is rejected for other detected Mali models. Its CSF/Midgard exclusions remain. Other releases may have different requirements. |
| Samsung Xclipse | [RADV Xclipse v1.8.0](https://github.com/JimVulkan/radv-xclipse/releases/tag/v1.8.0) | Experimental import/loading integration. Use `radv-xclipse-27.0.0-devel-269238e.zip`, the standard Android build. Its metadata requires Android 14/API 34; the upstream release lists Xclipse 530/540/550/920/940/950/960. |

These drivers target different GPU hardware. PanVK and RADV Xclipse cannot replace Turnip on an Adreno phone. The app's Android 11 minimum does not override a driver's higher Android requirement. Unknown/software GPUs do not get a hardware-driver import row. Driver information remains available under Tools.

No additional driver binaries are bundled. Package authorship and notices stay with their original authors. Winlator-specific wrapper settings do not configure this native renderer. The original engine still checks Vulkan features and may reject a driver even after a successful import. Import/loading support is not a claim that STORM R gameplay works on every listed device. No physical Mali or Xclipse device is available in the build workspace.

## Implementation

`native/gpu_probe.c` opens the system Vulkan loader in the launcher, creates a surface-free Vulkan 1.0 instance, reads the physical GPU name/vendor and destroys the instance. The result is cached per process. It does not create a device or modify engine dispatch. When the probe is unavailable, the prior Qualcomm manufacturer/hardware detection is retained as a fallback.

`DriverPolicy` separates device family, package family and minimum API. `DriverSupport` replaces the stock Qualcomm-only menu/configuration gate and validates both new imports and the current selection. The stock driver picker, configuration keys, native AdrenoTools loader and driver-failure recovery flow remain. Blocked selections use the system path and expose their reason in Driver information.

The pinned STORM R engine reads `NARUTO_GPU_DRIVER_DIR` and `NARUTO_GPU_DRIVER_LIBRARY` and uses its existing native AdrenoTools loader. The customization extends package import, GPU-family checks and the configuration writer. It does not replace that loader or patch the native renderer. The executable sections of the five bundled helper libraries, including `libhook_impl.so`, match the STORM 3 reference used for this integration. Mali/Xclipse gameplay remains unverified.

Imports use unique private staging, bounded compressed/expanded reads (64/256 MiB), at most 128 entries, checked flat filenames and ARM64 ELF shared-object headers. Standard root-level ZIPs and the stock single-directory layout flatten to the same existing format; duplicate flattened names are rejected. Metadata and library names stay inside the private driver folder. Linux/glibc, OpenGL-only and identifiable Wayland packages are rejected. An ID derived from every unpacked file, including dependencies and notices, keeps different packages separate and avoids replacing an existing driver with a same-name update.

Imports and selection own the existing game/save lock while making changes. A refused import cannot release another owner's lock. Driver changes clear only `shadowzero.txt`, the previous ICD's compatibility result; no saves or shader caches are deleted. The original save importer and bundled save assets are untouched.

Reference reviewed: [PT4Droid's driver integration](https://github.com/XHYN-PH/pt4droid/tree/2f43a90e3f910be4d59804f3b46a5132cc951d8e), especially the system-GPU probe, bounded package staging and system-driver fallback. This fork uses its own Android integration around the existing STORM loader. See also [AdrenoTools](https://github.com/ByLaws/libadrenotools).

## Upstream packages inspected

- FristOneRR PanVK v3.7.0 ZIP SHA-256: `6e9129a3b9db5204c315df2ba5836fb88dea063686a0d11426de992e9e2409c5`; ARM64 `libvulkan_panfrost.so`, `minApi: 28`.
- RADV Xclipse v1.8.0 Android ZIP SHA-256: `0545b1b24817cc0ed4a9e4fc440a7080cbaf69a9ce5d1e4bb22b0e697129c223`; ARM64 `vulkan.radeon.so`, `minApi: 34`.

Both export the Android `HMI` Vulkan HAL module and Vulkan ICD entry points expected by the loader and use the existing `meta.json` package format. Their dependencies and requirements were inspected statically; no claim of Mali/Xclipse gameplay testing is made.
