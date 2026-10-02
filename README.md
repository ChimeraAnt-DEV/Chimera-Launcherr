<p align="center">
  <img src="https://raw.githubusercontent.com/ChimeraAnt-DEV/Glowberry-Client-LOGO/main/In%20Mod%20Menu%20Icon.png" width="150" height="150" alt="GlowberryClient">
</p>


# GlowberryClient


**A highly advanced Android launcher for Minecraft: Bedrock Edition**

[![GitHub Release](https://img.shields.io/github/v/release/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=blue)](https://github.com/ChimeraAnt-DEV/GlowberryClient/releases)
[![License: Apache 2.0](https://img.shields.io/github/license/ChimeraAnt-DEV/GlowberryClient)](https://github.com/ChimeraAnt-DEV/GlowberryClient/blob/main/LICENSE)
[![Issues](https://img.shields.io/github/issues/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=red)](https://github.com/ChimeraAnt-DEV/GlowberryClient/issues)
[![Stars](https://img.shields.io/github/stars/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=yellow)](https://github.com/ChimeraAnt-DEV/GlowberryClient)
[![Downloads](https://img.shields.io/github/downloads/ChimeraAnt-DEV/GlowberryClient/total.svg)](https://github.com/ChimeraAnt-DEV/GlowberryClient/releases)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)

---

## Introduction

GlowberryClient is a feature-rich, open-source Android launcher built specifically for legitimate players of Minecraft: Bedrock Edition (MCBE). It provides a powerful and user-friendly alternative to the standard Google Play installation, allowing you to manage multiple game versions and extend functionality with external modules.

GlowberryClient enables you to import your official Minecraft APK and run it directly without requiring system installation. The launcher supports loading external native modules to enhance gameplay, provides robust multi-version management with complete isolation between installations, and includes built-in tools for managing worlds, resource packs, skin packs, accounts, controllers, and launcher settings. It also ships a modern in-game Mod Menu (plus a premium VIP Mod Menu opened by an in-game bind), a proximity voice-chat system with optional internet relay, a replay recorder, a Java-to-Bedrock mod auto-porter, a cosmetics studio, and a performance/personalization layer on top of a fully custom UI. Whether you're looking to organize different game versions, test modifications, or optimize your gaming experience, GlowberryClient offers the flexibility you need. The repository also ships a full user guide (English and 简体中文), developer documentation, and Preloader API reference under `docs/`.

> **Independent project:** GlowberryClient is its own project with its own architecture, branding, feature set, and maintenance. It is not a fork of any other launcher. Its native-modding layer builds on ideas and conventions from the wider open-source Bedrock modding ecosystem, but every component described here is developed and maintained as part of GlowberryClient.

### Key Features

- **APK Import & Installation-Free Launching** – Import your official Minecraft APK (XAPK or composed APK supported) and run it directly without system installation
- **Installations Tab** – Browse the Bedrock versions a package mirror publishes and install a chosen version as its own isolated instance. Downloads survive a failed import so a retry never re-downloads hundreds of megabytes, listing pages are paged through cleanly, and a browser fallback handles a mirror that challenges plain requests. The source is pluggable, so supporting a new mirror needs no UI or pipeline changes
- **Native Mod System (Preloader)** – Load external native SO modules packaged as `.chimerapack` (or a plain `.so`), with a Preloader input/output pipeline, hook installation, patches, and keyboard/gamepad input callbacks for developers (see `docs/api` and `examples/full-cpp-mod`)
- **`.AntEgg` Mod Packaging** – A ZIP-based package format for shipping native or Lua-script mods with a validated manifest, sandboxed extraction, and atomic install. Script mods run on an embedded Lua VM and report failures in the Mods tab instead of taking the launcher down (see `examples/antegg-template`)
- **Multi-Version & Instance Management** – Manage multiple Minecraft versions independently, with complete isolation between configurations, game data, and worlds
- **Mod Menu (two screens)** – A touch Mod Menu with **General**, **HUD Editor**, **Cosmetics**, **Packs**, **Voice**, **Replay**, and **Settings** sections, plus a premium **VIP Mod Menu** opened by an in-game bind with controller and keyboard surfaces, a shared module grid, and a full draggable HUD overlay system
- **PvP Modules** – A dedicated **PvP** section in the Mod Menu groups combat tools: Aim Settings, CPS Display, Snaplook, Armor HUD, Crystal Optimizer, Hit Registration, Select Hit, and Hitboxes. Modules that need live game data say so on screen instead of showing a fabricated reading
- **PvP Suite (V1.1)** – Four overlay-only visual modules in the General tab: Reach Indicator, Trajectory Prediction, Hit Prediction, and Custom Kill Effects. Each fails closed (draws nothing) when it has no data rather than guessing
- **Proximity Voice Chat** – Talk to other players in the same world over LAN multicast, with multiple channels, shareable private join codes, a live in-game mic indicator, and a dedicated Voice tab for managing channels and members. An optional **internet relay** extends voice to players who are not on the same Wi-Fi, using Opus compression and a lightweight Go relay server
- **Replay System** – Record, trim, favorite, rename, and export clips from a shared Replay panel available in both Mod Menu screens, backed by a single capture/library backend
- **JavaBridge (Java Edition Mod Auto-Porter)** – Point it at a Java Edition `.jar` and it assesses portability, then (with your explicit consent and a configured LLM) decompiles and generates a Bedrock `.AntEgg` mod. It rewrites Java mods rather than running them, and says so — there is no Java Edition runtime
- **Cosmetics Studio & Status Diagnostic** – Preview your character with an applied skin, cape, and accessories, then apply a cape in-game through a resource pack. Scope is stated honestly: the preview animates, the in-game cape is a still texture. A **Cosmetics status** diagnostic turns the cape's silent failure modes into named checks
- **Mod Sourcing** – Import mods from **CurseForge** (user-provided API key) and install `.mcpack`/`.mcaddon` files, or pick local files. A **Mod Hub** also browses the **Modrinth** index and scans local Downloads, but Modrinth hosts Java Edition content that this launcher cannot install, so it is browse-only and says so. A `.jar` picked in the importer is routed to JavaBridge instead of being reported unsupported
- **Mod Load Diagnostics & Safe Mode** – Failed mods are recorded with a user-readable reason and badged inline instead of crashing the process, and a crash-loop safe mode offers to disable the mods that were loaded before a crash
- **Multiple Xbox Account Management** – Add and switch multiple Microsoft/Xbox accounts in the launcher, with in-app MSAL login and device-code flows, so the game always launches with the expected identity
- **Controller Support & Input Mapping** – Up to 5 profiles per controller type (Xbox, DualShock 4, DualSense), with button remapping (including remap layers), stick dead-zone tuning, sensitivity, rumble strength, anti-stick-drift with hysteresis, explicit calibration, and a Low Input Delay mode, applied live to gameplay. Controller screens use **3D illustrations** with a projection that lifts the shoulders toward the camera
- **Content Management & Skin Packs** – Import, export, and back up worlds, resource packs, skin packs, and launcher data from one place, plus skin-pack management outside the game
- **In-Game Pack Changer** – Optionally change an instance's resource packs from inside the running game, written to every candidate game-data root so the change is never silently ignored
- **Custom Flat Worlds** – Craft preset or fully customized superflat worlds before the game even opens
- **Options Editing & Quick Launch** – Edit per-version `options.txt`-style settings, and use Minecraft URI quick-launch actions to open screens, connect to servers, add servers, join Realms, load worlds, or run commands
- **Playtime Tracking** – Per-instance playtime is recorded and shown on the home hero card and the instance list
- **News, Updates, & Crash Reporting** – In-app news feed with notifications, a crash-reporting screen surfacing logs, automated release-content tooling (`docs/RELEASE_CONTENT.md`), and a Discord notification action for beta releases
- **Performance & Personalization** – One-tap performance presets (Battery/Balanced/Performance), an **FPS Optimization** coordinator that quiets the launcher and sheds speculative work under thermal/battery/memory pressure while a session runs, a "Reduce Network Latency" mode that tunes launcher-owned sockets and avoids polling during a game session, user-configurable accent color, glass-card/compact-mode theming, animations, empty states, a "last played" home hero card, and an optional blue firework touch layer

---

## System Requirements

Before installing GlowberryClient, ensure your device meets the following minimum specifications:

- **Operating System:** Android 9.0 (API 28) or higher
- **Device Architecture:** 64-bit (arm64-v8a) devices. Most Minecraft versions ship both arm64-v8a and armeabi-v7a libraries and run normally; only a version whose APK ships 32-bit libraries exclusively is refused, since a 64-bit process cannot load them.
- **RAM:** Minimum 1 GB available RAM (2 GB or more recommended)
- **Storage:** At least 2 GB of available storage for Minecraft and game data
- **License Requirement:** You must have Minecraft installed on your device for GlowberryClient to work

> **Note:** For optimal performance and stability, we recommend Android 9.0 or higher with at least 3 GB of available RAM and 5 GB of free storage.

---

## Installation

### Prerequisites

Before proceeding with GlowberryClient installation, ensure that you have the official Minecraft Bedrock Edition app installed on your device from Google Play. This is required for GlowberryClient to function properly.

### Installation Steps

1. Visit the [Releases Page](https://github.com/ChimeraAnt-DEV/GlowberryClient/releases) and download the latest Apk/Zip build (extract it)
2. Open your device Settings and navigate to Security or Applications
3. Enable "Unknown Sources" or "Allow installation from unknown sources" to permit APK installation
4. Locate the downloaded APK file using your file manager, and tap to install
5. Grant the necessary permissions when prompted during installation
6. Once installed, open GlowberryClient from your application drawer

> **Important:** GlowberryClient requires a legitimate, licensed copy of Minecraft Bedrock Edition. Do not use this launcher with pirated or unauthorized versions of the game. Ensure your Minecraft license is valid and properly linked to your Microsoft account.

> **Upgrading from Chimera Client:** GlowberryClient is a display-name rebrand of Chimera Client, and the app's package id (`org.chimeramc.client`) is unchanged, so it installs as an in-place update and your settings carry over.

> **Upgrading from the older "Chimera Launcher":** That earlier rename *did* change the package id, so Android will not offer an in-place update and the old app's private settings are not reachable. To carry them over, use **Settings → Backup & Restore → Export** in the old app, then **Backup & Restore → Import** in GlowberryClient. Account credentials and API keys are never included in the export.

---

## Development Setup

If you want to build GlowberryClient from source or contribute to development, follow these steps to set up your development environment:

### Prerequisites

- Git installed on your system
- Android Studio (latest version recommended)
- Java Development Kit (JDK) 21 or higher
- Android SDK with API level 28 or higher
- Android NDK r28 (for the native Preloader/LibHttpClient build)

### Setup Instructions

1. Clone the GlowberryClient repository (**including submodules** — the native Preloader and LibHttpClient code are git submodules the native build requires):


    ```bash
    git clone --recurse-submodules https://github.com/ChimeraAnt-DEV/GlowberryClient.git
    ```

    If you already cloned without them, run `git submodule update --init --recursive`.

2. Open the project directory in Android Studio
3. Allow Android Studio to download and install required dependencies and build tools
4. Wait for Gradle to complete the initial sync process
5. Connect your Android device or start an emulator (API 28+)
6. Click the "Run" button in Android Studio to build and deploy to your device
7. The app will launch automatically on successful build completion

> **Build Tip:** For faster builds during development, use `Build > Make Project` to compile incrementally instead of full rebuilds. The fast Java-only check is `./gradlew :app:compileDebugJavaWithJavac`; unit tests run with `./gradlew :app:testDebugUnitTest`.

---

### Continuous Integration

Every push to `main` and every pull request automatically triggers a CI workflow (`.github/workflows/build.yml`) that:

- Checks out the repository **with submodules** (the native Preloader/LibHttpClient code is fetched, so the full native build can run)
- Builds a debug APK with JDK 21 and Gradle 8.13 (via the existing wrapper, `./gradlew assembleDebug`)
- **Fails the run (and blocks/red-flags the PR)** if the build breaks — it never silently swallows errors
- Uploads the resulting APK as a workflow artifact, downloadable straight from the **Actions** tab

To grab the APK from a green run:

1. Open the **Actions** tab on the repository
2. Click the latest green **Build Debug APK** run
3. Scroll to the bottom and download the **app-debug-apk** artifact
4. Install it on your device (Android 9.0+/API 28+)

---

## Contribution Guidelines

We welcome contributions from the community to improve GlowberryClient. To ensure a high-quality codebase and smooth collaboration, please adhere to the following guidelines:

### Code Quality

Write clean, modular code with descriptive variable names and consistent formatting. Follow the Kotlin and Java style guidelines established in the project. Ensure your code is readable and well-structured for future maintainers.

### Commit Structure

Use small, focused commits with clear and descriptive messages. Each commit should address a single feature or bug fix. Example: "Fixed memory leak in version manager" or "Added support for ARM32 architecture".

### Documentation

Add comments for complex logic and update relevant documentation in the repository. If you add new features, update the README and any related documentation files.

### Performance

Optimize all additions to maintain low latency and smooth performance. Test your changes thoroughly to ensure they don't introduce lag or performance regressions. The controller input path and in-game overlays run on the input-to-photon critical path, so keep them allocation-free.

### Testing

Test all changes on multiple devices and Android versions to ensure compatibility and stability. Where logic can be isolated from Android and game APIs, add a pure unit test — many subsystems (controller response math, voice protocol, cosmetics, hit projection, mod packaging) are covered this way.

### Pull Requests

Submit PRs with a detailed description of changes, including the problem solved or feature added. Reference any related issues, and provide screenshots or videos if your changes affect the UI.

### Community Standards

Follow our Code of Conduct to maintain a respectful and inclusive environment. Be constructive in feedback, respect others' work, and communicate professionally with all contributors.

**Before Submitting:** Run a full build cycle and test on at least one device to minimize errors. We review all contributions promptly, and appreciate your efforts to enhance GlowberryClient.

---

## Usage Guidelines

GlowberryClient is designed for legitimate players of Minecraft Bedrock Edition. Please respect the following guidelines and terms of use:

### Permitted Uses

- Modify GlowberryClient for personal gameplay and to test new features
- Create educational content (videos, tutorials, blog posts) showcasing GlowberryClient's capabilities
- Create derivative projects, provided you comply with the Apache License 2.0
- Share your modified versions with others as long as you comply with the Apache License 2.0 terms

### Prohibited Uses

- Do not claim GlowberryClient as your own without crediting the Chimera Team and its contributors
- Do not use GlowberryClient to violate Mojang or Microsoft's user agreements

> **Disclaimer:** The authors and contributors of GlowberryClient are not responsible for bans, damages, or issues arising from the use of this software. Use it at your own risk and in accordance with Minecraft's terms of service.

For full legal details, see the LICENSE file in the repository.

---

## Acknowledgements

GlowberryClient is an independent project. It stands on the shoulders of the open-source Android and Minecraft modding ecosystems, and it would not be possible without the contributions of many talented individuals and organizations:

### Special Thanks To

- **Chimera Team** – For designing, building, and maintaining GlowberryClient and providing infrastructure support
- **Open-Source Bedrock Modding Community** – For the shared knowledge, tooling conventions, and ecosystem research that inform how native modules are packaged, loaded, and kept safe
- **Android Community** – For excellent documentation, libraries, and tools that made this launcher possible
- **Open Source Community** – For all the libraries, frameworks, and tools that power this project
- **Contributors** – A heartfelt thank you to all [contributors](https://github.com/ChimeraAnt-DEV/GlowberryClient/graphs/contributors) who have continuously improved and maintained GlowberryClient through their time and expertise

---

## Contact & Support

**Author / Team:** Chimera Team

**Project Repository:** [https://github.com/ChimeraAnt-DEV/GlowberryClient](https://github.com/ChimeraAnt-DEV/GlowberryClient)

**Report Issues:** [GitHub Issues Page](https://github.com/ChimeraAnt-DEV/GlowberryClient/issues)

**For support and questions:** Please create an issue on the GitHub repository.

---

<div align="center">

[![GitHub Release](https://img.shields.io/github/v/release/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=blue)](https://github.com/ChimeraAnt-DEV/GlowberryClient/releases)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Issues](https://img.shields.io/github/issues/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=red)](https://github.com/ChimeraAnt-DEV/GlowberryClient/issues)
[![Stars](https://img.shields.io/github/stars/ChimeraAnt-DEV/GlowberryClient?style=flat-square&color=yellow)](https://github.com/ChimeraAnt-DEV/GlowberryClient)
[![Downloads](https://img.shields.io/github/downloads/ChimeraAnt-DEV/GlowberryClient/total.svg)](https://github.com/ChimeraAnt-DEV/GlowberryClient/releases)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)

**Made with ❤️ by the Chimera Team Community**

<br>

<sub><span style="color: #808080;">All rights reserved.</span></sub>

<sub><span style="color: #808080;">This account is shared among the 20 members of the Official ChimeraTeam.</span></sub>

</div>
