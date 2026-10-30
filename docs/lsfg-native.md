# Native Frame Generation

Source: [Bannerlator 3.0.9](https://github.com/The412Banner/Bannerlator/releases/tag/3.0.9)
(`e6f3410`, imported 2026-09-10).

Choose the **Vulkan** renderer, then select **Win-FG Native** for built-in 2x
generation or import your Lossless.dll and select **LSFG Native 2x–4x**.
Flow Scale applies to either engine. EGL and DisplayX are not supported.

LSFG Native normally requires Vulkan 1.3. The experimental **LSFG Vulkan 1.1/1.2
compatibility** switch in the container and shortcut editors allows older drivers
with the required extensions. It defaults off, inherits from the container unless
overridden for a shortcut, and requires a relaunch. Vulkan memory model,
storage-image writes without a format, and extended storage-image formats are
still required. Both engines require
storage access for the compositor's image format. Unsupported drivers continue
without native frame generation. A container-local Lossless.dll can also be used
if no global DLL has been imported.

LSFG's shader cache is translated on a worker thread into `files/lsfg-native/shaders.cache`.
The imported DLL remains data and is never executed. Native LSFG disables the
external LSFG-VK and win-fg layers left by previous installations. Win-FG Native
uses its bundled MIT-licensed shader chain and needs no DLL.

The compositor renders into its own image ring, runs the selected native chain,
and presents generated frames before the real frame using FIFO. The cursor is
drawn separately for every present. Generation is capped by the number of images
the swapchain can safely acquire; hardware with a shallow swapchain may provide
fewer generated frames than the chosen multiplier. Surface recreation and setting
changes reset or rebuild the relevant GPU resources after submitted work completes.
Bannerlator 3.0.9's LSFG fixes are included: history priming after startup or a
pacing gap, guest-size configuration before the shader-chain build, and swapchain
recreation on both abandoned-acquire error paths.

Checks:

```sh
./gradlew assembleDebug testDebugUnitTest
python3 app/src/test/cpp/run_lsfg_native_test.py "$ANDROID_NDK_HOME"
```

The host check requires a C++17 compiler with AddressSanitizer/UndefinedBehaviorSanitizer
and an installed Android NDK for Vulkan headers. It exercises pacing limits, malformed
DLL/cache handling, and malformed DXBC rejection using the real imported code.
An Android GPU/game smoke test is still required to measure frame generation,
check cursor/rotation, and test background/foreground transitions.

See [third-party notices](../THIRD-PARTY-LICENSES.md) for source and license details.

## October 30 update

Checked [Bannerlator main `dbd0289c7ef18cb0716be07aa850c2fc3c4eb13b`](https://github.com/The412Banner/Bannerlator/tree/dbd0289c7ef18cb0716be07aa850c2fc3c4eb13b).
The latest LSFG engine changes are [#512 review fixes `ea93c2e3`](https://github.com/The412Banner/Bannerlator/commit/ea93c2e3e51549c120c715030f108fef317a2c6e),
following experimental driver compatibility in `28ae0197`. The shader chain,
translator, pacing, and governor otherwise match this fork's existing import.

Imported the Vulkan 1.1/1.2 capability probe, extension/feature enablement with
fallback to ordinary rendering, and SPIR-V lowering at shader-load time. Vulkan
1.1 needs `VK_KHR_spirv_1_4`, `VK_KHR_shader_float_controls`, and
`VK_KHR_vulkan_memory_model`; Vulkan 1.2 uses core SPIR-V 1.5. Only DXBC-translated
modules are lowered; precompiled modules are not relabeled. Cached shader bytes
remain unchanged. Module or pipeline startup failures are reported by the frame
generation status and disarm the session when status is checked.

Preserved automatic capture at the game/render height and the existing panel
aspect/clamp, filtered upscale, effects, and Win-FG integration. Did not add
Bannerlator's separate Panel/Game/custom capture picker or Wayland/cloud-player
backends. Win-FG launches do not enable experimental LSFG device features.

Validation: debug APK and JVM tests; native host checks with ASan/UBSan cover
SPIR-V 1.4/1.5 lowering, memory-model extension insertion, unsupported/malformed
preambles, Vulkan 1.1/1.2 opt-in gates, missing required extensions/features,
and the existing pacing, DLL/cache, DXBC, and capture-resolution checks.
Device testing of proprietary Vulkan 1.1/1.2 drivers remains required.
