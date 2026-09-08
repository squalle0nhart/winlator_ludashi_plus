# LSFG Native

Source: [Bannerlator 63af72a](https://github.com/The412Banner/Bannerlator/tree/63af72abc20c9924d68771940c2d15467825fe22), the current main revision checked on 2026-09-07.

Import your Lossless.dll using the existing Settings importer. Choose the **Vulkan**
renderer, select **LSFG Native** under Frame Generation, and choose **2x**, **3x** or
**4x**. Flow Scale reuses the existing LSFG setting. The native engine does not use
LSFG-VK's Performance Mode switch. EGL and DisplayX are not supported by this port.

The selected compositor driver must support Vulkan 1.3, Vulkan memory model,
storage-image writes without a format, extended storage-image formats, and storage
access for the compositor's image format. Unsupported drivers display a reason
and continue without native frame generation. A container-local Lossless.dll can
also be used if no global DLL has been imported.

The shader cache is translated on a worker thread into `files/lsfg-native/shaders.cache`.
The imported DLL remains data and is never executed. Native LSFG disables the
external LSFG-VK and win-fg layers for that launch. The existing backends remain
separately selectable.

The compositor renders into its own image ring, runs the imported LSFG chain,
and presents generated frames before the real frame using FIFO. The cursor is
drawn separately for every present. Generation is capped by the number of images
the swapchain can safely acquire; hardware with a shallow swapchain may provide
fewer generated frames than the chosen multiplier. Surface recreation and setting
changes reset or rebuild the relevant GPU resources after submitted work completes.

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
