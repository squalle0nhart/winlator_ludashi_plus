# Pipetto wrapper update

Imported range: [`60c291b9..f194ef97`](https://github.com/Pipetto-crypto/winlator/compare/60c291b9dea9c9eaf1dd5562f80eb90f3305baaa...f194ef9761ce004a6828f3409c08a67308b31019), branch `winlator_bionic`.

- Replaced `app/src/main/assets/graphics_driver/wrapper.tzst` with the exact target asset. SHA-256: `b5d76cfbfe698aa730c4f5b215506dfdaf471128581702eb3e95b87a4afb44ce`.
- Bumped the extracted graphics runtime marker so existing containers receive the wrapper.
- Ported EGL RGBA channel swapping, minimum root-window stacking order, empty hidden-window-class handling, opaque DisplayX buffers, and conditional Vulkan validation layers.
- Serialized DisplayX event mutations and composition batches with a mutex. Retained the GPU completion wait because this fork does not pass a Vulkan completion fence to Android's surface transaction. Failed conversions retain the original buffer.
- Retained equivalent existing features: FEX Unix-library trust entries; container/shortcut renderer selection; EGL surface format and nearest/bilinear filtering; DisplayX configuration and wrapper environment variables. EGL now honors the chosen drawable surface format at launch.

Validation: `./gradlew assembleDebug testDebugUnitTest --console=plain`. The source wrapper matches the target Git blob and the asset packaged in the debug APK. Rendering still needs validation on an Android device.
