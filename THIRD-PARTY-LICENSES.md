# VEGAS DXVK

VEGAS 2.7.3 by [isygold](https://github.com/isygold/vegas-releases) is bundled from
[Bannerlator commit 601ea52b41c12b90d3c6a86a7bd566b27bb15e4c](https://github.com/The412Banner/Bannerlator/blob/601ea52b41c12b90d3c6a86a7bd566b27bb15e4c/app/src/main/assets/dxwrapper/vegas-2.7.3.tzst).
The archive is unchanged and renamed to `dxvk-2.7.3-vegas.tzst` to use the existing
DXVK installer and version selector. It contains x86 and x86-64 DLLs, including D3D8.
Credits: isygold, Bannerlator/The412Banner, and the DXVK contributors.

# FEXCore and Box64 presets

Extreme-gn, Box64 Extreme and Extreme-2, and additional preset variable definitions
and help text are adapted from the same Bannerlator revision above (GPL-3.0).
FEXCore Extreme-wn and Extreme (TSO)-wn retain the existing WinNative-derived values;
Extreme-gn derives from GameNative. Credits: Bannerlator/The412Banner,
WinNative-Emu/WinNative, utkarshdalal/GameNative, and the Box64 preset community.

# LSFG Native

Imported from [Bannerlator commit 63af72abc20c9924d68771940c2d15467825fe22](https://github.com/The412Banner/Bannerlator/tree/63af72abc20c9924d68771940c2d15467825fe22) on 2026-09-07.

The native LSFG engine, shader extraction/cache code, Java cache bridge and adapted
compositor integration are GPL-3.0-or-later. Credits: Bannerlator/The412Banner,
WinNative, Camille LaVey and the Eden Emulator Project, and PancakeTAS/lsfg-vk.
Original copyright headers are preserved; the license is included in
[lsfg/LICENSE](app/src/main/cpp/winlator/renderer/vulkan/lsfg/LICENSE).

DXVK's DXBC-to-SPIR-V translator is vendored from the same Bannerlator commit in
[thirdparty/dxbc](app/src/main/cpp/thirdparty/dxbc). Its original zlib license is
preserved in [LICENSE.md](app/src/main/cpp/thirdparty/dxbc/LICENSE.md). The local
`util_small_vector.h` change makes its integer overload unambiguous on macOS.

The LSFG shader chain is extracted on-device from the user's imported Lossless.dll;
neither that DLL nor its proprietary shaders are bundled.

Local integration changes adapt JNI names, renderer paths, compatible render-pass
dependencies, cursor rotation, descriptor target indexing, swapchain acquisition
limits, resource lifetime during reconfiguration, and input-history warm-up.
