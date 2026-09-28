# F-Droid Build Instructions for GlowberryClient

## Prerequisites
- Android SDK 35+
- NDK r28
- Java 21

## Building for F-Droid

This build is fully de-Googled and contains no proprietary Google services.

### Removed Google Dependencies:
- Firebase Crashlytics
- Google Play Services
- Google Services Plugin

### Build Commands:
```bash
./gradlew clean assembleRelease
```

### Verification:
- No Google Play Services dependencies
- No Firebase libraries
- No proprietary tracking
- Fully open-source compatible

## Microsoft OAuth Setup (Optional)
To enable Microsoft login, users must:
1. Register an Azure AD application at https://portal.azure.com
2. Configure redirect URI: `msauth://org.chimeramc.client`
3. Add client ID to app settings

## Java Edition Support
Not supported. Java Edition cannot run on Android's ART runtime: it needs a full desktop
JVM class library, AWT/Java2D, desktop OpenGL and a JNI-compatible LWJGL, none of which
Android provides. A real implementation requires a bundled per-architecture JRE plus a
GL-to-GL ES/Vulkan translation layer (GL4ES/Zink/virglrenderer), and licensing constraints
mean it must be built from permissive components only. There is no stub to configure.

## Mod Sources
- CurseForge API (user must provide API key)
- Modrinth index (browse-only; Modrinth hosts Java Edition content this launcher cannot install)
- Local mod imports

## License
Open-source compatible - ready for F-Droid submission
