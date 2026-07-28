<p align="center">
  <img src="logo.png" alt="Ludashi Plus" width="600">
</p>

# Ludashi Plus

Ludashi Plus is a community fork of
[Winlator Ludashi by StevenMXZ](https://github.com/StevenMXZ/Winlator-Ludashi).
It runs Windows `x86_64` applications on Android through Wine and supports
conventional Box86/Box64 containers as well as Arm64EC containers using
FEXCore or optional WowBox64 support.

The upstream lineage is Original Winlator →
[Winlator Bionic by Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
→ Winlator Ludashi → Ludashi Plus.

## Feature highlights

### Runtime and compatibility

- Box86/Box64 and FEXCore translation backends, including Arm64EC containers.
- FEXCore DLL and UnixLibs modes with per-container and per-shortcut selection.
- Stability, Compatibility, Intermediate, Performance, Performance TSO,
  Extreme, Extreme TSO, Denuvo, and custom FEXCore presets.
- Downloadable Wine, Proton, FEXCore, Box64/Box86, DXVK, VKD3D, and graphics
  driver components.
- Fullscreen aspect-ratio modes, per-game shortcut overrides, Wine Mono tools,
  input controls, and external-storage support.

### Graphics and presentation

- OpenGL, Vulkan, and SurfaceFlinger/ASurfaceRenderer host renderers.
- Native rendering, direct scanout, color-channel correction, scaling and
  filtering controls, post-processing, and configurable present modes.
- SGSR, FSR/CAS-style scaling, NVIDIA Image Scaling, DLS, and sharpening
  controls where supported.
- Original, Pipetto, Leegao, and GameNative Vulkan wrapper variants.
- Turnip, Zink, VirGL, and wrapper-based graphics-driver support.

### Frame generation

- Native Vulkan optical-flow frame generation with `2x`, `3x`, and `4x`
  multipliers and adjustable Smoothness.
- Bionic-FG with selectable Default, Traced, V2, FSR3, and FSR3+ models.
- LSFG-VK support using a user-imported `Lossless.dll`.
- Live frame-generation controls in the runtime sidebar.

### Library and app experience

- Integrated GOG, Epic Games, Amazon Games, and Steam library/download flows.
- Content feeds for installing and updating runtime components.
- Light/dark themes and layouts adjusted for smaller screens.
- Optional XR mode through the WinlatorXR/OpenXR integration.

## Build variants

### Ludashi

Uses a Ludashi-like package identity. Some Android devices, particularly
Xiaomi devices, may apply vendor performance policies to recognized benchmark
or gaming packages. This behavior is device- and firmware-dependent.

### Dev Vanilla

Uses the normal development package identity so it can coexist with other
Winlator forks.

### RedMagic

Uses a game-like package identity intended for RedMagic devices that expose
package-specific gaming enhancements. Availability and behavior depend on the
device firmware.

## Installation

1. Download an APK from the
   [Releases page](https://github.com/StevenMXZ/Winlator-Ludashi/releases).
2. Choose the desired build variant.
3. Install and launch the APK, then wait for the initial runtime extraction to
   finish.

## Useful tips

- [ZeroKimchi's Winlator Bionic tutorial](https://youtu.be/EJDWZUGF9sk)
  provides a useful introduction.
- For Box86/Box64 containers, start with the **Performance** preset. Use
  **Intermediate** when an installer is unstable.
- For Arm64EC containers, test different FEXCore versions and presets. Extreme
  presets trade compatibility and memory consistency checks for speed.
- Start frame generation at `2x`; increase the multiplier only after confirming
  stable pacing and image quality.
- Native Framegen Smoothness can reduce visible warp artifacts by favoring real
  frames in uncertain regions.
- If an older OpenGL game does not launch, try
  `MESA_EXTENSION_MAX_YEAR=2003` in the container environment.
- Per-game shortcuts can override container graphics, runtime, input, and
  frame-generation settings.

## Components and drivers

- [Winlator-Contents by StevenMXZ](https://github.com/StevenMXZ/Winlator-Contents)
- [WinNative-Components by Nicholas/Xnick417x](https://github.com/nicholasx417/WinNative-Components)
- [Bannerlator component feed](https://github.com/The412Banner/winlator-contents)
- [Kimchi's AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers/releases)
- [StevenMXZ Turnip builds](https://github.com/StevenMXZ/freedreno_turnip-CI/releases)

## Credits and attribution

### Project lineage and maintainers

- [brunodev85](https://github.com/brunodev85) — original Winlator.
- [coffincolors](https://github.com/coffincolors) — Winlator CMod fork and
  related integration work.
- [longjunyu2](https://github.com/longjunyu2) — glibc-based Winlator work.
- [Pipetto-crypto](https://github.com/Pipetto-crypto) — Winlator Bionic,
  renderer work, and the Pipetto wrapper lineage.
- [StevenMXZ](https://github.com/StevenMXZ) — Winlator Ludashi, renderer,
  scanout, component, and driver integration.
- [squalle0nhart](https://github.com/squalle0nhart) — current Ludashi feature
  ports, adaptations, fixes, and integration.

### Feature sources and contributors

| Feature or integrated work | Original author/project |
| --- | --- |
| Native optical-flow frame generation | [MaxsTechReview / maxjivi05](https://github.com/maxjivi05), [WinNative PR #537](https://github.com/WinNative-Emu/WinNative/pull/537) |
| FEXCore UnixLibs mode | [MaxsTechReview / maxjivi05](https://github.com/maxjivi05), [WinNative commit 48fe6b9](https://github.com/WinNative-Emu/WinNative/commit/48fe6b9b6fda36512a30950df255eed99e175de1) |
| Extreme and Extreme TSO FEXCore presets | [Xnick417x](https://github.com/Xnick417x), [WinNative PR #670](https://github.com/WinNative-Emu/WinNative/pull/670) |
| Bionic-FG present-path/runtime fixes | [clintOnSky](https://github.com/clintOnSky), [Bannerlator PR #96](https://github.com/The412Banner/Bannerlator/pull/96) |
| Bionic-FG models, including FSR3+ | [The412Banner](https://github.com/The412Banner), including [model 4 commit b5a71194](https://github.com/The412Banner/Bannerlator/commit/b5a71194d643eeff986434f692ad3310fddec474) |
| Bannerlator fullscreen modes, component feeds, and related ports | [The412Banner/Bannerlator](https://github.com/The412Banner/Bannerlator) |
| SurfaceFlinger ASR and fenced AHardwareBuffer work | [André Vito / AndreVto](https://github.com/AndreVto), [GameNative PR #1582](https://github.com/utkarshdalal/GameNative/pull/1582) and [PR #1620](https://github.com/utkarshdalal/GameNative/pull/1620), adapted through Bannerlator and StevenMX's scanout foundation |
| GameNative store research/integration and wrapper | [Utkarsh Dalal and the GameNative team](https://github.com/utkarshdalal/GameNative); the current wrapper also incorporates work by [Lee Gao](https://github.com/leegao) through [GameNative PR #1771](https://github.com/utkarshdalal/GameNative/pull/1771) |
| LSFG-VK Vulkan layer | [PancakeTAS/lsfg-vk](https://github.com/PancakeTAS/lsfg-vk). Lossless Scaling and `Lossless.dll` remain the work of their respective authors and are not distributed as part of this project. |
| Vegas runtime/component support | [isygold/vegas-releases](https://github.com/isygold/vegas-releases) |
| WinlatorXR integration | [lvonasek](https://github.com/lvonasek) and the [Khronos OpenXR SDK](https://github.com/KhronosGroup/OpenXR-SDK) |
| NVIDIA Image Scaling shader reference | [NVIDIAImageScaling](https://github.com/NVIDIAGameWorks/NVIDIAImageScaling), adapted under its MIT license |
| Snapdragon Game Super Resolution reference | [Qualcomm Snapdragon Game Studios](https://github.com/SnapdragonGameStudios/snapdragon-gsr) |
| FidelityFX FSR/CAS references | [AMD GPUOpen FidelityFX FSR](https://github.com/GPUOpen-Effects/FidelityFX-FSR) and [FidelityFX CAS](https://github.com/GPUOpen-Effects/FidelityFX-CAS) |
| Steam protocol and depot support | [JavaSteam](https://github.com/Longi94/JavaSteam) |
| Turnip driver development | [Danylo Piliaiev](https://blogs.igalia.com/dpiliaiev/) and the Mesa/Freedreno contributors |
| Mods and compatibility tips | [alexvorxx](https://github.com/alexvorxx) |
| Big Picture Mode music | Dale Melvin Blevens III (Fumer) |

### Runtime and third-party projects

- [Wine](https://www.winehq.org/)
- [Box86 and Box64 by ptitSeb](https://github.com/ptitSeb)
- [FEX-Emu](https://github.com/FEX-Emu/FEX)
- [PRoot](https://proot-me.github.io/)
- [Mesa](https://www.mesa3d.org/) and
  [xMeM's wrapper branch](https://github.com/xMeM/mesa/tree/wrapper)
- [DXVK](https://github.com/doitsujin/dxvk)
- [Wine VKD3D](https://gitlab.winehq.org/wine/vkd3d)
- [D8VK](https://github.com/AlpyneDreams/d8vk)
- [CNC DDraw](https://github.com/FunkyFr3sh/cnc-ddraw)
- [dxwrapper](https://github.com/elishacloud/dxwrapper)
- [libadrenotools](https://github.com/bylaws/libadrenotools)
- [Termux packages](https://github.com/termux/termux-packages)
- [Ubuntu Bionic root filesystem](https://releases.ubuntu.com/bionic/)

Project and dependency license files remain authoritative. If a port or
contribution is missing attribution, please open an issue with the upstream
source and author so it can be corrected.

Additional code contributors are preserved in the
[Git history](https://github.com/squalle0nhart/winlator_glibc/commits) and
[contributors graph](https://github.com/squalle0nhart/winlator_glibc/graphs/contributors).

Winlator Ludashi is a community project and is not affiliated with Microsoft,
Valve, Epic Games, Amazon, GOG, NVIDIA, Qualcomm, or Lossless Scaling.
