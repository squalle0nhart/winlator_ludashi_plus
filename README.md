

<p align="center">
  <img src="logo.png" alt="Winlator Bionic" width="600">
</p>

# Winlator Bionic

Winlator is an Android application that lets you run Windows (x86\_64) applications with Wine. It supports standard `x86_64` containers using Box86/Box64, as well as `Arm64EC` containers which utilize FEXCore (for 64/32-bit) or an optional WowBox64 (for 32-bit).

This is a fork of the **Winlator Bionic** project by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator) and - **Winlator Ludashi** by [StevenMXZ](https://github.com/StevenMXZ/Winlator-Ludashi)

# Installation

1.  Download and install the latest APK from this repository's [Releases section](https://github.com/squalle0nhart/winlator_ludashi_plus/releases).
2.  Launch the app and wait for the installation process to finish.

# Useful Tips

  - Use Proton 11.0-b5-arm64ec-steam-unix or Proton 11.1GE-arm64ec-steam at first. If not work try using proton 10 or 9
  - If you are using an `x86_64` container and experiencing performance issues, try changing the Box86/Box64 preset to **Performance** in Container Settings -\> Advanced Tab.
  - If you are using an `Arm64EC` container, try swapping between different FEXCore versions (2505,2507 etc) in the container settings for better compatibility or performance.
  - For applications that use .NET Framework, try installing Wine Mono found in Start Menu -\> System Tools.
  - If some older games don't open, try adding the environment variable MESA\_EXTENSION\_MAX\_YEAR=2003 in Container Settings -\> Environment Variables.
  - Try running the games using the shortcut on the Winlator home screen, there you can define individual settings for each game.
  - To speed up the installers, try changing the Box86/Box64 preset to Intermediate in Container Settings -\> Advanced Tab.

# Additional Components & Updates

You can find updated components (known as `wcps`) to improve compatibility and performance, as well as new drivers, at the links below:

  - **Winlator Components (FEXCore, Box64/Box86, DXVK, etc.):**
      - [StevenMXZ's Winlator-Contents Repository](https://github.com/StevenMXZ/Winlator-Contents)
  - **Adreno GPU Drivers (Turnip):**
      - [Kimchi's AdrenoToolsDrivers Releases](https://www.google.com/search?q=https://github.com/K11MCH1/AdrenoToolsDrivers/releases)

# Frame Generation

The app provides separately selectable **Bionic-FG**, **win-fg**, and **LSFG-VK** backends. **win-fg** is Bannerlator's clean-room Vulkan frame-generation layer: its motion estimation adapts AMD FidelityFX FSR3 optical flow under the MIT license, while its synthesis was written from first principles and bundles no proprietary model weights. It needs no external DLL. In the current device-proven runtime, win-fg uses an Off/On control (On = 2x) with live Optical flow/Bidirectional model and flow-scale updates.

# Credits and Third-party apps

  - **Original Winlator** by [brunodev85](https://github.com/brunodev85/winlator)
  - **Original Winlator Bionic** by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
  - **Winlator (coffincolors fork)** by [coffincolors](https://github.com/coffincolors/winlator)
  - **Winlator Ludashi** by [StevenMXZ](https://github.com/StevenMXZ/Winlator-Ludashi)
  - **win-fg** by [The412Banner/Bannerlator](https://github.com/The412Banner/Bannerlator) — clean-room frame generation using an MIT-licensed adaptation of AMD FidelityFX FSR3 optical flow
  - **FusionHUD © The412Banner** — [github.com/The412Banner/FusionHUD](https://github.com/The412Banner/FusionHUD), GPL-3.0 with the upstream attribution requirement preserved in-app and in this documentation
  - Ubuntu RootFs (Bionic Beaver): [releases.ubuntu.com/bionic](https://www.google.com/search?q=https://releases.ubuntu.com/bionic)
  - Wine: [winehq.org](https://www.winehq.org/)
  - Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
  - FEX-Emu by [FEX-Emu](https://github.com/FEX-Emu/FEX)
  - PRoot: [proot-me.github.io](https://proot-me.github.io)
  - Mesa (Turnip/Zink/VirGL): [mesa3d.org](https://www.mesa3d.org)
  - DXVK: [github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk)
  - VKD3D: [gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d)
  - D8VK: [github.com/AlpyneDreams/d8vk](https://github.com/AlpyneDreams/d8vk)
  - CNC DDraw: [github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw)
[ptitseb](https://github.com/ptitSeb) (Box86/Box64), [Danylo](https://blogs.igalia.com/dpiliaiev/tags/mesa/) (Turnip), [alexvorxx](https://github.com/alexvorxx) (Mods/Tips) and others.








