# Native Frame Generation

Source: [Bannerlator 3.0.9](https://github.com/The412Banner/Bannerlator/releases/tag/3.0.9)
(`e6f3410`, imported 2026-09-10).

Choose the **Vulkan** renderer, then select **Win-FG Native** for built-in 2x
generation or import your Lossless.dll and select **LSFG Native 2x–4x**.
Flow Scale applies to either engine. EGL and DisplayX are not supported.

LSFG Native additionally requires Vulkan 1.3, Vulkan memory model, storage-image
writes without a format, and extended storage-image formats. Both engines require
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
