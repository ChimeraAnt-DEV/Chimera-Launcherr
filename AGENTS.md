# AGENTS.md

## Build
- `./gradlew :app:compileDebugJavaWithJavac` — fast Java-only validation.
- `./gradlew :app:compileDebugKotlin` — validates the Kotlin module (also compiles Java).
  - **Submodules required before assembling**: `app/src/main/cpp/preloader` (https://github.com/ChimeraAnt-DEV/preloader-android) and `app/src/main/cpp/libHttpClient` (https://github.com/microsoft/libHttpClient( are git submodules; `CMakeLists.txt` add_subdirectories both. If uninitialized (`git submodule status` shows `-` prefix(, run `git submodule sync && git submodule update --init --recursive` first, then `./gradlew clean` (stale stub `libgxcore.so` can persist otherwise(.
  - Toolchain installed at `/opt/android-sdk` (`local.properties`; java at `/usr/lib/jvm/java-21-openjdk-amd64`(. `libgxcore.so` is a **prebuilt** drop-in at `app/src/main/jniLibs/` **arm64-v8a only**, ~3 MB; no armeabi-v7a gxcore by design. Verify in APK: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep gxcore`. Kit: `adb uninstall org.chimeramc.client` before installing a new APK (stale extracted native libs cached under app storage(.

## Controller architecture (org.chimeramc.client.launcher.controller + ui.views + ui.activities.ControllerActivity)
- `ControllerType` — enum mapping vendor/product IDs to Xbox (vendor 0x045E, any product), DS4 (0x054C / 0x05C4|0x09CC), DualSense (0x054C / 0x0CE6). `matches()` returns true for any product when `productIdA == -1`.
- `ControllerProfile` — serializable POJO: button remaps (Map<Integer,Integer> key→key), L/R stick dead zones, L/R sensitivity, vibration flag. Constants: MAX_SLOTS=5, DEFAULT_DEAD_ZONE=0.15f.
- `ControllerProfileManager` — persists up to 5 profiles per type + active slot index in SharedPreferences ("controller_profiles") as JSON (Gson). Methods: getProfiles/saveProfiles/getActiveSlot/setActiveSlot/getActiveProfile/addProfile/deleteProfile/duplicateProfile/renameProfile.
- `ControllerInputProcessor` — static bridge that applies the active profile to the real gameplay pipeline. `MinecraftActivity.kt` calls `detectAndLoad(this)` on create, `processKeyEvent(keyCode)` in `dispatchKeyEvent` (key remap), and `isWithinDeadZone(event)` in `dispatchGenericMotionEvent` (dead zone). `setActiveProfile(type, profile)` is called from ControllerActivity whenever the active profile changes.
  - **Hot path — keep it lock-free and allocation-free.** Every method here runs on the UI thread inside `dispatchKeyEvent`/`dispatchGenericMotionEvent`, so any cost is added directly to input-to-photon latency. Nothing on the read path may be `synchronized`; the active response is one immutable `volatile ControllerResponse` so readers never lock and never observe a half-applied profile.
  - `ControllerResponse` (launcher.controller) holds all profile-derived math precomputed once per profile change: dead-zone divisor folded into a multiply, stick remaps as a flat `int[]` indexed by key code (not a `HashMap`), and the response curve as a polynomial (not `Math.pow`). Rebuild it via `setActiveProfile`/`refresh`/`reload`, never per event.
  - Low Input Delay tightens the dead zone to `LOW_LATENCY_DEAD_ZONE` and applies an ease-out curve so identical stick travel yields more look output. After the toggle or a profile edit, call `ControllerInputProcessor.reload(context)` — otherwise a running session keeps the old curve.
  - `isWithinDeadZone` must only report true when an event's *entire* content is stick movement the dead zone flattens. Returning true for an all-zero event swallows controller **button** presses, which also arrive via `dispatchGenericMotionEvent`; returning true for an event that also carries a trigger pull/hat/scroll discards that input, and a pad reports every axis in one event, so a constant drift offset would ride along with an analogue trigger pull.
  - **Anti stick drift is radial, not per-axis.** `ControllerResponse.adjustStickPair` measures `sqrt(x*x+y*y)` against the threshold and rescales both axes together to preserve push direction; the old per-axis `adjustAxis` check cannot see a stick resting off-centre on one axis, which reads as a constant diagonal push. `adjustAxis`/`isOutsideDeadZone` remain per-axis for the non-anti-drift path (existing tests pin that behaviour). `StickDriftGate` adds `ENTRY_EVENTS = 2` of hysteresis so a one-frame noise crossing is rejected while a flick above `impulseBypass(threshold)` skips the delay. Gates live as plain static fields in `ControllerInputProcessor` (UI-thread only, never `synchronized`) and are reset in `setActiveProfile`; they are consulted at the event's own timestamp so the dead-zone check and the rewrite agree about one event without double-counting it.
  - **Calibration is explicit, not at connect.** `StickCalibrationSession` samples while the user holds a button (a modal dialog would cover the pad and block motion events, so the control lives on the controller screen, not the editor dialog). Frames above `StickCalibration.MAX_FLOOR` are treated as the player holding the stick and restart the run rather than being recorded as a floor. `StickCalibration.effectiveDeadZone` only ever *widens* the dead zone — anti-drift can never silently shrink the user's setting — and is bounded so a very worn stick cannot swallow real input. Anti-drift is off by default so enabling it cannot silently change an existing profile's feel.
  - Unit tests: `app/src/test/java/org/chimeramc/client/launcher/controller/ControllerResponseTest.java` and `AntiStickDriftTest.java` (no mocks). Run `./gradlew :app:testDebugUnitTest`; JUnit must be fetched once (not available `--offline`).
- `ControllerIllustrationView` — custom `View` drawing flat top-down controller silhouettes + individually-highlightable button/stick regions per type; `setType`, `setRegionGlow`, `handleKeyEvent`, `handleMotionEvent`. Must keep both `(Context)` and `(Context, AttributeSet)` constructors so XML layout can inflate it.
- `ControllerActivity` — registers an `InputManager.InputDeviceListener`, auto-selects the illustration for a detected controller, otherwise shows the first (Xbox) with Manual "Next" (Xbox→DS4→DualSense→Xbox); provides profile chips + Create/Rename/Duplicate/Delete + editor dialog (name, L/R dead zone, L/R sensitivity, vibration).
- Curve editors live in the profile dialog in `ui/fragments/ControllerSettingsFragment` (the old `ControllerActivity` is gone; `CustomizeActivity` hosts Controller + Skins fragments). The dialog now carries stick/trigger preset + shape + exponent controls and a live `CurvePreviewView`, wrapped in a `ScrollView` (the content outgrew the dialog). Preset spinners are attached through `primedPresetListener`, which drops the Spinner's first post-layout `onItemSelected` — without it a custom curve sitting on spinner position 0 is silently reset to the first preset when the dialog opens. All curve controls are initialised from the profile before the listener is attached, so nothing is lost.
- `TriggerCurve.MIN_EXPONENT`/`MAX_EXPONENT` exist so the editor slider bounds and the clamp agree; using raw literals drifts.

## Settings backup (`LauncherSettingsBackup`) pref typing
- `applySharedPrefs` must restore the *type each key was stored with*. SharedPreferences is typed per key, so writing a float where an int pref (`PersonalizationManager` stores accent/blur/rounding as ints) reads back with `getInt` throws `ClassCastException` at the read site, after the import already reported success. Gson preserves the original literal, so a `.`/`e` in the literal selects `putFloat`, otherwise `putInt`.

## Editor note
- Android Activity/View APIs used: `android.hardware.input.InputManager`, `android.view.InputDevice` (getDeviceIds/getDevice/vendor/product/sources), `android.view.KeyEvent`/`MotionEvent` (getAxisValue(getAxisValue; MotionEvent has NO setAxisValue in this SDK). Custom `View` inflation requires `(Context)`/`(Context, AttributeSet)` ctors.
- Controller remaps/dead zones must be wired through `ControllerInputProcessor` (not just UI) to affect actual gameplay input; key remaps are applied to the `PreloaderInput.onKeyEvent` path in `MinecraftActivity.dispatchKeyEvent`.

## Rebrand: Chimera Launcher → Chimera Client (org.chimeramc.launcher → org.chimeramc.client)
- The Java/Kotlin package, `namespace` and `applicationId` are `org.chimeramc.client`. `git grep org\.chimeramc\.launcher` should stay empty. Note the directory `app/src/main/java/org/chimeramc/client/launcher/...` still exists — `launcher` there is a **subpackage name**, not the old root, so path-style greps for `org/chimeramc/launcher` are false positives only if they match the whole prefix; the root is `org/chimeramc/client`.
- **The preloader submodule's JNI symbols were renamed to match** (`Java_org_chimeramc_client_*`) and the parent pins that commit. If the submodule is ever rolled back to an older commit while the Java classes stay in `org.chimeramc.client`, every native input/mod call fails with `UnsatisfiedLinkError`. Verify with `llvm-nm -D --defined-only <built libpreloader.so> | grep Java_org_chimeramc_client` (expect ~35, and zero `..._launcher_`).
- **`org.levimc.*` classes are still load-bearing and must not be "finished off".** The prebuilt `libgxcore.so` / `libinbuiltmods.so` are arm64-only binaries with no source in this repo and still export `Java_org_levimc_*`, so `NativeBridgeHelper`, `MinecraftRuntimePreparer` and `core.mods.inbuilt.nativemod.*` stay in `org.levimc` on purpose.
- **Renaming `applicationId` changes app identity.** Android will not offer an in-place update and the old package's private `SharedPreferences` (settings, personalization, controller profiles, accounts) are unreachable from the new app. Game data under `Android/media/<packageId>` and legacy `games/org.chimeramc` still migrate via `StorageMigrationManager`. The supported settings path is `LauncherSettingsBackup` export/import; say so in release notes rather than implying a seamless upgrade.
- **Prefs keys and storage roots are not package paths.** `org.chimeramc.xal.crypto` (prefs key) and `games/org.chimeramc` (legacy root) were deliberately left alone — renaming them would strand existing data. Same for `chimeralauncher_instance_backup` (backup `FORMAT_ID`, validated on import) and the `chimeralauncher_*` managed-pack/skin state filenames.
- **The canonical GitHub repo is `ChimeraAnt-DEV/ChimeraLauncher`, not `.../ChimeraClient`.** The rename PR repointed every URL (update check, news feed, signature-rule source, README badges, docs) at `ChimeraAnt-DEV/ChimeraClient`, which does not exist — the GitHub API 404s and nothing redirects, so the news feed, signature-rule refresh and update check all failed at runtime. The brand name changed but the repo slug did not. If a URL looks wrong, test it with `curl -o /dev/null -w '%{http_code}'` before assuming the code is at fault.

## Mod Menu tabs (PvP group)
- The Mod Menu's top-level nav is Modules / HUD Editor / Settings. The **PvP tab is a filter + section**, not a fourth nav entry: `ModuleFilter.PVP` with a `filter_pvp` chip, plus `ModIds.GROUP_PVP`.
- `ModIds.isPvpModule(id)` is the single source of truth for what counts as PvP (`aim_settings`, `cps_display`, `snaplook`). The filter predicate and the provider's grouping both call it so they cannot drift.
- **PvP modules must stay contiguous in the provider's list.** `ModMenuAdapter` emits one group header per contiguous run of `groupId`, so `InbuiltModuleProvider.groupPvpLast(...)` moves them to the end. Reordering them apart renders the PvP header more than once. `PvpModuleGroupingTest` pins this.
- PvP modules remain `Source.INBUILT`, so the Inbuilt filter/grouping still finds them — do not move them into a separate store.

## JNI/native packaging (org.levimc vs org.chimeramc)
- **Prebuilt** `libgxcore.so` and `libinbuiltmods.so` (in `app/src/main/jniLibs/arm64-v8a/`) still export symbols under the **upstream** `org.levimc.*` package names. Do NOT move their Java-bound classes too `org.chimeramc.*` or you get `UnsatisfiedLinkError: No implementation found.`:
  - `org.levimc.launcher.util.NativeBridgeHelper` (+ colocated `NativeImageGuard`) binds `Java_org_levimc_launcher_util_NativeBridgeHelper_*`
  - `org.levimc.launcher.core.mods.inbuilt.nativemod.*` (AutoSprint/Fps/Gyro/HotbarSlot/MoreButtons/PojavControls/Snaplook/Zoom + InbuiltModsNative) binds `Java_org_levimc_launcher_core_mods_inbuilt_nativemod_*`
- The preloader submodule source (`app/src/main/cpp/preloader`, not checked out in dev) is already branded `org.chimeramc.*` — so `ModManager`, `ExternalModBridge`, `PreloaderInput`, `MoreButtonsSvgBridge`, `MinecraftRuntimePreparer` natives stay in `org.chimeramc` packages.

- If the native libs ever get rebuilt against the chimeramc package, move these classes back and regenerate the binaries together.

## Personalization / theming (org.chimeramc.client.util.PersonalizationManager + org.chimeramc.client.launcher.ui.animation.DynamicAnim)
- `PersonalizationManager` (launcher/util) drives: accent color (`getAccentColor`/`setAccentColor`), animation speed, UI transparency, card rounding (`getCardRoundingPx` — note non-Px name), icon size, blur intensity, compact mode, dark-mode detection (`isDarkMode(Context)`), and the three accessibility toggles:
  - `isShowAnimations()` / `setShowAnimations(boolean)` — global animations on/off. Must gate ALL new motion: `DynamicAnim.disableAnimations()`/`enableAnimations()` only cover DynamicAnim land; custom ViewPropertyAnimators/ValueAnimators (e.g. hero-card pulse, progress tween in MainActivity) must check this flag themselves.
  - `isEnableGlowEffects()` / `setEnableGlowEffects(boolean)` — gates card elevation + gradient strokes (MainActivity.applyGlowEffects). "Reduced motion" flows through `isShowAnimations`. DO NOT hard-code new animations outside this gate.
- `DynamicAnim.applyPressScale(view)` — press-scale + elevation micro-interaction; respects `animationsEnabled` (skips entirely when disabled). `setGlobalSpeedMultiplier(float)` scales spring durations.
  - Per-view press state (two springs + caller's listener) is cached in a view tag keyed by `R.id.dynamic_anim_press_state` (`res/values/ids.xml`). Attaching is idempotent per view, so recycler rebinds must not clear it. Springs are re-targeted, never rebuilt, so a touch allocates nothing.
  - `applyPressScale(view, delegate)` — use this when the view needs its own touch listener. A bare `setOnTouchListener` **silently replaces** an existing listener; a drag handle that starts an `ItemTouchHelper` drag on `ACTION_DOWN` stops working with no error (see `ModsAdapter`). The delegate runs after the press effect and its return value is honoured.
  - Press state cached on a recycled view carries a delegate closed over the *previous* holder. Today the delegate resolves the holder at call time from `itemTouchHelper`, so it is safe — re-verify that if a rebound delegate ever captures the holder itself.
  - Presses that turn into a scroll snap back to full size once past touch slop, so rows do not stay shrunk while flinging.
- Layout rule: text-bearing buttons/TextViews must not pin `android:layout_height` to a dp value — use `wrap_content` + `android:minHeight`. A fixed height does not grow with font scale and clips the label. Fixed dp is fine for icon-only touch targets.
- **Accent color reaches views through theme attributes, not `@color/primary`.** Dynamic color rewrites theme attributes at runtime, so every drawable whose fill should follow the wallpaper's palette references `?attr/colorPrimary` / `?attr/colorPrimaryContainer` (a `@color/primary` reference would stay behind the wallpaper). The theme declares `colorPrimaryContainer`/`colorOnPrimaryContainer`. Consequently the accent-override comparison in `applyAccentColorRecursive` must resolve the *theme's* current primary (`resolveThemeColor`), not the static resource — comparing against `R.color.primary` silently stops matching under dynamic color and a custom accent would no longer override tinted views. `colorPrimary` comes from AppCompat, the container/secondary/tertiary attrs from Material.
- Glass tokens (`glass_background`, `glass_background_elevated`, `glass_border`) have a `values-night` override; light-mode white translucency reads as fog on a black backdrop. `MainActivity.applyBlurIntensity` only paints its dim scrim when a wallpaper background image is actually set — with none there is nothing to blur and the scrim just darkened the dashboard.
- **Fixed gradients need fixed text tokens, never `on_primary`.** `gradient_header` runs `primary_dark → accent_purple` in *both* themes and `card_hero_gradient` flips dark-teal↔bright-teal, so text on them uses `on_header_gradient` / `on_header_gradient_secondary` (always light, header) and `on_hero_card` / `on_hero_card_secondary` (per-theme, hero). `on_primary` flips to black in night mode and vanishes on the dark header.
- Don't paint the branding title with the raw accent via `applySolidAccentText` — the header is dark regardless of wallpaper, so a dark preset (indigo/green) reads as near-black on it. Accent-overriding is for views on the app surface, not on the fixed header gradient.

## Ids for view tags
- `res/values/ids.xml` holds `<item type="id">` keys used with `View.setTag(int, Object)`. Use these rather than inventing tag keys, so cached state cannot collide with resource ids.
- Gradient accent palette lives in `colors.xml` + `values-night/colors.xml` (`grad_hero_start/end`, `grad_launch_*`, `grad_versions_*`, `grad_mods_*`, `grad_content_*`, `grad_pulse_glow`) with per-section drawables `card_hero_gradient.xml`, `card_launch_gradient.xml`, `card_versions_gradient.xml`, `card_mods_gradient.xml`, `card_content_gradient.xml`, and `section_accent_bar.xml` (3dp vertical accent bars used as section headers). Keep dark/light variants in sync and respect `on_primary`/`on_surface` for text legibility on gradients.

## Game session lifecycle & playtime (org.chimeramc.client.launcher.core.minecraft.PlaytimeManager)
- `PlaytimeManager` (in the launcher.core.minecraft package, not `util`) is the per-instance playtime tracker: `init(context)`, `startSession(profileId)`, `heartbeat()`, `stopSession()`, `getTotalMs(profileId)`, `formatPlaytime(ms)`. Persistence is SharedPreferences `"playtime_tracker"` — `total_ms_<profileId>` accumulates, `active_profile`/`active_start_elapsed` hold the running session; `HEARTBEAT_INTERVAL_MS = 15s` (public). Hardware monitors use more deeply-nested SharedPreferences keys.
- On-device watchdog: `MinecraftActivity.kt` starts the session from intent extra `MinecraftLauncher.EXTRA_STORAGE_PROFILE_ID` (from `MinecraftLauncher.getStorageProfileId(version)`), sends a heartbeat via Handler every 15s, and calls `stopSession()` in `onDestroy`. `Application.kt` calls `PlaytimeManager.init()` to clean up an interrupted session (crash/kill).
- Playtime is shown on the main hero card (`last_played_time_stat`) and per-instance in `InstancesActivity` + `item_instance_card.xml`; hero stats animate via `DynamicAnim` counters, but the playtime value itself uses a plain `setText`.

## Low-latency networking (org.chimeramc.client.launcher.settings.LowLatencyNetworkManager)
- Settings → Basic "Reduce Network Latency" toggle (`FeatureSettings.isReduceNetworkLatencyEnabled()` / `setReduceNetworkLatencyEnabled`). **Label and string must stay honest**: a launcher cannot move a game server closer, change ISP routing, or promise any ping figure (never "10ms"). It only removes latency the device itself adds. What it implements:
  - `createSocketFactory()` wraps the platform default and sets `TCP_NODELAY` on launcher-owned sockets (NewsRepository + GithubReleaseUpdater OkHttp builders apply it when the flag is on). Deliberately delegates to `SocketFactory.getDefault()` — calling the inherited abstract `super.createSocket(...)` does NOT compile.
  - `SOCKET_BUFFER_SIZE = 64 KiB`. Keep this small: oversized buffers let the stack hold back small writes, which is backwards for interactive request/response traffic.
  - `configure(builder)` also installs a warm `ConnectionPool(8, 5min)` so TCP+TLS handshakes are already done. It deliberately does **not** set connect/read timeouts — each caller sets its own (`CurseForgeClient` 30s, `PreloaderSignatureRulesManager` 10s).
  - `prefetchDnsOnBackground()` warms `PREFETCH_HOSTS` (raw.githubusercontent, api.github.com, api.curseforge.com, www.googleapis.com, plus popular Bedrock servers) on a single background executor; re-triggered when the toggle is turned on.
  - Game-session quiet zone: `setGameSessionActive(true)` (MinecraftActivity session start) makes `isGameSessionActive()` true; NewsRepository.refreshIfStale / GithubReleaseUpdater / LauncherNewsMessagingService then serve cached data instead of polling while a session runs. Airtime contention is real added latency on mobile.
- Java-only sockets: `features` flag lives in FeatureSettings (`isReduceNetworkLatencyEnabled`) — see `app/src/main/java/org/chimeramc/client/launcher/settings/FeatureSettings.java`.

## Performance preset (org.chimeramc.client.launcher.settings.PerformancePresetManager)
- Settings → Basic offers one Battery/Balanced/Performance choice that coordinates four previously separate switches. It is a **thin coordinator, not a new source of truth**: every value is written through the existing `FeatureSettings` setters so the per-toggle Basic screen stays in sync. Applying the same preset twice is idempotent.
- **`PerformancePresetManager.wantsHighRefreshMode`/`displayModeFor` must stay on the launch path.** They were written but never called, so `MinecraftActivity.applyHighRefreshRateMode()` requested a high-refresh mode on *every* launch — including under Battery and Balanced, which the class docs promise will leave the panel alone. The gate is now `PerformancePresetManager.shouldRequestHighRefresh(this)` (never throws; an unreadable pref reads as Balanced). `PerformancePresetManagerTest` pins it. The selection itself only ever picks a **same-resolution** fast mode, because on many panels the fast modes are lower resolution and "unlocking 120Hz" would silently trade sharpness for frames.
- `ThermalGovernor` is deliberately **not** a preset-controlled value — it is a reactive hardware reading (`severity()`) that gates speculative/background work, reached indirectly via `setReduceNetworkLatencyEnabled` → `prefetchDnsOnBackground` → `shouldPauseSpeculativeWork()`. Do not add a "set thermal level" API; there is nothing to set.
- The FPS overlay is an inbuilt mod (`FpsDisplayOverlay` via `ModIds.FPS_DISPLAY` + `InbuiltOverlayManager`), not a second readout the preset owns. Don't add a duplicate.

## 32-bit vs 64-bit instances (single arm64 build; dual-ABI versions run on it)
- The APK ships `arm64-v8a` only (`ndk abiFilters`), so Android fixes the process to 64-bit at install time. `GamePackageManager.abiCompatibility()` reports `INCOMPATIBLE` only when *none* of the ABIs a version's APK ships match the process, and `MinecraftRuntimePreparer` then fails the launch preflight with `abiMismatchMessage()`.
- **Most Bedrock versions run fine on this one build.** Their APK ships both `arm64-v8a` and `armeabi-v7a`, and the process picks the arm64 half. A version is only genuinely unlaunchable if its APK contains no arm64 libraries at all.
- **Read the APK, never the cached label.** `GameVersion.abiList` is inferred from whichever `.so` sits in the extraction folder (`VersionManager.inferAbiFromNativeLibDir`), so it records what was extracted *last*, not what the version offers. Judging compatibility from it once meant a version extracted as `arm` libraries stayed labelled `armeabi-v7a` forever and was refused on this 64-bit build with "the process is 64 bit cannot run 32bit" — even though its APK also contained `arm64-v8a`. Both `abiCompatibility` (via `shippedAbisOf`) and the label inference now inspect `base.apk.chimera` + `splits/*.apk.chimera` for `lib/<abi>/libminecraftpe.so` first. Keep it that way: the APK is the authority, the label is a last-resort fallback.
- **`android:use32bitAbi` cannot help.** It only tells the installer to prefer the 32-bit native libs an app already ships; it is a packaging choice, not a runtime one. There is no API to run part of an app 32-bit and part 64-bit. A version whose APK ships *only* 32-bit libs genuinely cannot be loaded by this build, because `MinecraftActivity` runs in-process; supporting those needs a second APK carrying armeabi-v7a natives (including the prebuilt `libgxcore.so`, arm64-only by design).
- Do not add a setting that claims to switch bitness; it cannot work. The honest deliverable is the preflight message, which now names the ABI the version actually ships and fires only when nothing can load.
- `AbiBitness` holds the decision logic (`selectLaunchAbi`, `hasLoadableAbi`, `abisInApks`) and is covered by `AbiBitnessTest`, including reading a real dual-ABI APK off disk. Reverting `hasLoadableAbi` to judge only the first shipped ABI makes those tests fail.

## Brand palette (res/values/colors.xml + values-night)
- **One violet/magenta identity, not the inherited Bedrock green/cyan.** The palette replaced `primary #1B5E20`, `glow_green`/`glow_cyan`, `accent_purple`/`accent_blue`. `primary` is `#6236E8` in day mode and `#A88CFF` at night; `secondary` is `#A82E9E`/`#E070C0`; the glow pair is now `glow_violet`/`glow_magenta`. If something still reads green, it is a hardcoded literal, not a token.
- **Do not embed raw brand ARGB.** Android colour resources cannot express "primary at 10% alpha", so the translucent brand washes are named tokens: `brand_primary_0/6/10/13/20/24/33/67/87` and `brand_indigo_67`. A new chip/badge/toggle wash adds a token rather than resurrecting `#1A6236E8` inline.
- **The launcher icon is the GlowberryClient art: a golden glowberry on a dark-purple gradient tile.** The adaptive icon's `<background>` is the `drawable/ic_launcher_background` gradient shape (`#221958 → #7E32AA`); `ic_launcher_foreground` carries the same gradient full-bleed with the emblem pre-scaled inside the 33dp safe circle, so `mipmap-anydpi-v26/ic_launcher.xml` applies **no** `<inset>` — a 26% inset on top of pre-scaled art would shrink the mark twice and leave it stranded mid-tile. `ic_launcher_mono` is the white silhouette for themed icons. The per-density PNGs are rasterised from `App Icon.png`; regenerate the set rather than hand-editing one density.
- **The brand mark is `drawable/ic_chimera_ant.xml`** (an original geometric ant vector), and it is tinted white so callers apply `android:tint` / `setImageTintList(accent)`. The old `ic_splash_logo` / `ic_header_logo` / `ic_loading_logo` bitmaps were deleted. **Density folders shadow `drawable/`**, so a leftover `drawable-*dpi/ic_*` PNG silently wins over the vector — check for that first if a logo looks stale.

## Shared card style (Widget.Chimera.Card + res/values/dimens.xml)
- Every card surface reads geometry from `dimens.xml` (`chimera_card_radius` 14dp, `chimera_card_radius_small` 10dp, `chimera_card_border_width`, `chimera_card_elevation`, `chimera_card_padding`), never its own literal radius.
- `Widget.Chimera.Card` / `.Compact` set `android:background` to `@drawable/chimera_card` / `chimera_card_small` and are for **plain containers** (`LinearLayout`, `FrameLayout`, `TextView`). `Widget.Chimera.CardView` / `.MaterialCardView` are the variants for actual `CardView` subclasses — a CardView must get its radius from `cardCornerRadius`, not an `android:background`.
- `Widget.Chimera` is an empty root style that must exist: the dotted names infer their parent from it.
- The old one-off card drawables (`bg_rounded_card`, `header_background`, `card_background`, `card_background_small`, `bg_third_level_item`) still resolve, but they are now thin aliases pointing at the shared card tokens — do not add new references to them.
- The section gradient cards (`card_hero_gradient`, `card_versions/mod/content/launch_gradient`) are `layer-list`s that reuse the same radius tokens and add the shared `card_border` stroke, so gradient and flat cards are geometrically identical.
- **Do not add a recursive "apply card rounding to every GradientDrawable" walk.** `MainActivity.applyCardRounding` deliberately touches only the launch/select buttons; a full-tree radius rewrite also flattens pills (`bg_filter_chip` 20dp), tags (4dp) and accent bars (1.5dp).

## Top-level navigation (org.chimeramc.client.ui.navigation.LauncherTab + BaseActivity)
- **The nav bar is the original horizontal top bar.** `BaseActivity.wrapWithNavBar` inflates `layout/nav_bar.xml` into a **vertical** wrapper (bar above content, content weighted 1). `nav_bar.xml` is a `LinearLayout` root holding a single 48dp `FrameLayout` row — brand/back on the start, six tabs in a `HorizontalScrollView` in the centre, news/account controls on the end — above a 1dp `nav_divider`. Do NOT add a second bar or a fragment-based tab host.
- **A vertical side rail was tried and reverted. Do not reintroduce it.** The rail put the tabs in a column beside the content; because the app is landscape-only (`sensorLandscape` on ~29 activities) the column gets only ~360dp of height, and the fixed chrome (brand header, dividers, stacked footer) left room for barely two of six rows. Since `nav_item_launch` was first, Launch was the only reachable tab, which reads as "the other tabs are gone". A top bar has the whole screen *width* for its tabs, and `HorizontalScrollView` already handles six labelled entries, so the height pressure never arises. Restoring the bar also means restoring the wrapper orientation (`VERTICAL`), the content params (`MATCH_PARENT, 0, 1f`) and the enter animation axis (`springTranslationYTo`) — flipping only some of these leaves the content mis-measured.
- **Each tab is one `TextView` with an icon, not icon+label pair.** The original bar puts the icon on the `TextView` itself via `android:drawableStart` and tints it with `TextViewCompat.setCompoundDrawableTintList`. Only `nav_tab_*` exists — there are no `nav_item_*`, `nav_label_*` or `nav_indicator_*` ids; an earlier rail had four parallel arrays and removing the rail means those ids are gone from the layout, so any leftover `findViewById(R.id.nav_item_*)` is a compile error. `R.id.nav_tab_*` is the click target.
- Navigation is **activity-based**: each tab starts an Activity (`switchNavTab` → `startActivity` + `nav_tab_in/out` fade-through), and `setActiveNavTab(R.id.nav_tab_*)` re-tints the tab text and its compound icon. `LauncherTab` is the single source of truth mapping tab → activity, and its declaration order *is* the rail order and the bumper cycle order.
- `BaseActivity.dispatchKeyEvent` cycles tabs on L1/R1, L2/R2 and D-pad left/right (wrapping). Two guards matter:
  - **`shouldHandleNavKeys()`** (default true) — override to `false` on any screen that needs raw button presses. `CustomizeActivity` does, so `ControllerSettingsFragment`'s illustration can highlight the pressed button.
  - **`LauncherTab.shouldHandleKey(code, navBarPresent, gameSessionActive)`** — tab switching is suppressed while `LowLatencyNetworkManager.isGameSessionActive()`. Note `MinecraftActivity` extends the *game's* `com.mojang.minecraftpe.MainActivity`, NOT the launcher's, so gameplay never had the nav bar or this handler — but the session guard is kept as defence in depth.
- `BaseActivity.NAV_TAB_IDS` is the single id array shared by `setupBaseNavBar()` and `setActiveNavTab()`; adding a tab means updating the layout, that array, the click handler, and `LauncherTab`. `LauncherTabTest` covers ordering, activity mapping, wraparound, the key map and both guards.
- **There are six tabs.** `LAUNCH, VERSIONS, INSTALLATIONS, MODS, CUSTOMIZE, SETTINGS`. About is *not* a tab — it is reached from Settings (`SettingsActivity` `openAbout`). Controller and Skins are *not* tabs either; both live inside `CustomizeActivity` as `ControllerSettingsFragment` / `SkinsSettingsFragment` swapped behind `customize_tab_controller` / `customize_tab_skins`. Do not re-promote About/Controller/Skins to top-level tabs; add a new destination inside an existing tab instead.
- **VOICE is not a launcher nav tab; it is a Mod Menu section.** `VoiceChatActivity` exists and is reachable (the Mod Menu's Voice entry and the mod's own screen), but the launcher's top bar is navigation, and a communication feature does not belong in that row — `LauncherTab` documents this and `nav_bar.xml` has six tabs. The in-game home is the Mod Menu's Voice section (`nav_voice`), which is the first entry ahead of Modules
- **Screens that tint the rail themselves must touch all three pieces.** `SettingsActivity` re-applies a freshly chosen accent to the Settings entry directly (indicator + icon + label); the old text-view+compound-drawable path no longer applies. `MainActivity`/`InstancesActivity` still swallow a click on their own row via `nav_item_*`.
- `ControllerSettingsFragment` is the live home of the controller illustration and profile editor. It exposes `wantsRawKeyEvents()`, `handleHardwareKey(keyCode, down)` and `handleHardwareMotion(event)`, which `CustomizeActivity.dispatchKeyEvent` / `dispatchGenericMotionEvent` forward. The old standalone `ControllerActivity` was deleted — do not reintroduce it.
- `focus_ring` was prototyped and removed — no focus system consumes it; do not re-add a token without a consumer. Nav rows get touch feedback from `DynamicAnim.applyPressScale(row)`.
- **Dead code to ignore:** `VersionManager.importGameFile` only accepts `.apk`/`.xapk` and is unused — `ApkImportManager` is the live import gate. `unsupported_version_msg` ("older than 1.21.80") has no consumer in code or layout; the real preflight is the ABI one.

## Splash screen (ui.activities.SplashActivity + drawable/ic_chimera_ant)
- The splash mark is the **vector** `ic_chimera_ant`, tinted to the user's accent in `applySplashTheme` (`setImageTintList`). Orbit ring, orbit dot and the halo are `GradientDrawable`s rebuilt per accent, with the static `bg_orbit_ring`/`bg_orbit_dot`/`bg_logo_glow` drawables only as the pre-tint fallback — those fallbacks carry brand tokens now, not the old green/cyan.
- The sequence already animates: glow scale-in, mark scale-in, orbiting dot (`startOrbitAnimation`, 3s loop), halo pulse (`startGlowPulse`), progress tween → `navigateToMain`. Keep new motion on these ValueAnimators rather than swapping in a static PNG.
- All three tints (`orbitRing`, `orbitDot`, `logoGlow`) are accent-derived; if a colour looks off it is the accent resolver, not the drawable.
- **The ore loader has no depth buffer, so its geometry is pinned in a pure class.** `OreCrackSprites` draws a faceted gem block and an iron pickaxe as 16x16 char grids; `OreLoaderLayout` owns the two-cell placement and the "pickaxe sits outside the block" invariant. Anchoring the pickaxe at the block's centre (the original code) laid the sprite over the gem, which is the bug the redesign fixed. Draw offsets must stay inside the parentheses exactly as the controller illustration's do. `SplashLoaderTest` pins the grid size, the pickaxe's head/lit-edge/handle, the gem's lit and shaded bevelled cells, and that the two sprites stay inside the view across widths.

## Mod Hub (org.chimeramc.client.ui.activities.ModHubActivity + core.modrinth + core.downloads)
- `ModHubActivity` is the single browse screen, reached from three places: the MODS tab via `mod_hub_fullscreen_button` in `activity_mods_fullscreen.xml`, the home "More" list via `miscModHubRow`, and its own manifest entry. Its two tabs are Modrinth search and a local Downloads scanner; `EXTRA_INITIAL_TAB` picks which one opens.
- **Modrinth content is overwhelmingly Java Edition.** Bedrock-usable hits are a rounding error (a resourcepack search returns ~35k results while Bedrock-relevant mods return ~11). The UI must keep saying so: `modrinth_java_warning` is shown on the hub and again on the detail screen. Never relabel Java content as installable.
- `ModrinthClient` owns URL/facet construction (`buildSearchUrl`, `buildVersionsUrl`) so the query shape is unit-testable without network. `ModrinthClientTest` covers it. The version facet uses `game_versions=["<v>"]`; a malformed facet silently returns `total 0` rather than an error, so treat an empty result as unproven.
- `ModrinthDetailActivity` filters versions by the selected instance's `versionCode`, then **falls back to the unfiltered list** when that yields nothing — Bedrock and Java version strings (`26.51` vs `1.21.x`) rarely line up, and an empty screen told the user nothing.
- Downloads: it downloads the file to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` **first**, then imports from disk. A failed import therefore leaves a retryable file rather than a lost stream; the Downloads tab rescans after import.
- `DownloadsScanner.scan` walks public `Downloads`; keep it off the UI thread (the hub wraps it in a `Thread` + main-`Handler` post). `DownloadsScannerTest` covers extension classification and importability.
- Both `ModHubActivity` and `ModrinthDetailActivity` resolve pack directories through the same `content_management`/`LauncherStorage.normalizeContentStorageType` path as `ContentDetailsActivity` — copy that, do not invent a second resolution scheme.

## Controller illustrations (ui.views.ControllerIllustrationView)
- `ControllerIllustrationView` draws regions from **normalised** coordinates: `px = cx + (r.x - 0.5f) * 2f * scale`. The offset must stay inside the parentheses — `(r.x - 0.5f * 2f * scale)` collapses every button to `cx - scale`, piling the whole pad into a narrow band. If the illustration suddenly looks like cramped blobs, check that first.
- `handleMotionEvent` must set each axis glow **both ways** (`setRegionGlow("ls", active)`), because motion events stream continuously. Setting only `true` latched sticks and the d-pad on permanently after a single touch.

## Mod load diagnostics & crash-loop safe mode (core.mods.ModLoadDiagnostics + ModSafeMode)
- `ModLoadDiagnostics` records *why* a mod failed — `KIND_DLOPEN`, `KIND_INCOMPATIBLE` etc. — and `Classifier.describe(error)` / `kindOf(error)` turn a native loader error into a user-readable reason and a kind. `MinecraftRuntimePreparer.kt` writes the records at launch; `ModsFullscreenActivity` reads them and badges the failing mod inline instead of the process just dying.
- `ModSafeMode.beginLaunch(context, enabledModIds)` / `markModLoaded` / `completeLaunch` track which mods were loaded when a session started. If the previous launch never reached `completeLaunch`, `hasCrashLoop(context)` is true and the Mods tab offers to disable the mods loaded before that crash. `CrashReporter` is the separate crash-log surface; this is the mod-disable response.
- Tests live in `ModLoadDiagnosticsTest` (no mocks).

## Per-instance mod sets & the hero "N active" stat (core.mods.ModManager + MainActivity)
- Mods are already per-instance: `ModManager.setCurrentVersion(version)` points `modsDir` at `version.modsDir` and each instance keeps its own `mods/` + `mods_config.json`. Switching the selected version is what swaps the set — there is no shared global list.
- The hero-card count (`stat_mods_count` = "%1$d active") is derived from the same `modsLiveData` list the Mods tab renders, via `Mod.countEnabled(list)` (a pure, null-safe helper). Do **not** compute it with a separate `ModManager` read at bind time: discovery runs off the UI thread and the version is bound after the observer is attached, so the bind-time read is the stale pre-load value (zero). Recompute in `updateModsUI` so it always reflects the instance actually selected.
- `updateModsUI` must refresh the stat *before* any early return on `binding`/`modsListContainer`; a plain "0 active" on first paint is the symptom of gating the refresh behind a null view.
- `ModEnabledCountTest` covers the counting (empty/null/all-disabled → 0, toggling, null entries).

## Preloader signature rules (preloader.PreloaderSignatureRulesManager)
- Rule sources resolve in a fixed order: the host's **own** copy is authoritative, Levi's upstream repo is the fallback, and the bundled asset (`resources/preloader/preloader_signature_rules_source.json`) is the last resort for offline launches. Keep the bundled snapshot refreshed — it must contain every rule the live source has (e.g. the 1.26.50 rule), or an offline launch will reject a version the online source would accept.

## Java Edition support — NOT implemented (removed, do not re-add)
- The `core/javaedition` package and `JavaEditionModManager` were **deleted**, along with `core/mods/DualEngineModBrowser` and the `SkinsActivity`/`ControllerActivity` activities that only existed to host stubs. There is no Java Edition runtime, no stub, and no string advertising it. The README and `FDROID_README.md` both say Java Edition is not supported and explain why.
- Do not re-add a class that returns `isJavaEditionSupported() == true`, or a "Hybrid Minecraft Engine" subtitle. Any launcher-side claim that Java Edition works must be backed by a real runtime.
- **Android's built-in ART cannot run desktop Java Edition.** It is not a desktop JVM: no full class library, no AWT/Java2D, no desktop OpenGL, and no JNI ABI match for LWJGL. A real implementation needs a bundled per-arch JRE, a custom LWJGL (stock LWJGL has no Android backend), and a GL→GLES/Vulkan translation layer (GL4ES / Zink / virglrenderer), plus Mojang auth, version-manifest and asset download, classpath assembly and Forge/Fabric installers. This is a multi-month project measured in thousands of commits — the reference implementations (PojavLauncher/Amethyst) are ~7,500 commits each.
- **Licensing blocks naive reuse.** This project is Apache-2.0. PojavLauncher is GPL-3.0, Amethyst-Android is LGPL-3.0, ZalithLauncher2 is GPL-3.0. Copying or linking their app code makes the combined work GPL. Permissive pieces do exist (LWJGL3 BSD-3, GL4ES MIT, Mesa MIT) and OpenJDK is GPLv2-with-Classpath-Exception, so a from-scratch runtime using only permissive components is viable — but it is the expensive path. Get a licensing decision *before* writing code against any of these.
- Both editions cannot run concurrently (Bedrock needs the Android framework/`MinecraftActivity`; Java needs a JRE subprocess). A single unified app is a shell that dispatches per instance — the value is the shell (one instance list, one input/controller stack), not a merged game.
- If Java Edition launch is ever added: `jniLibs.useLegacyPackaging = true` (already set, app/build.gradle) is what makes extracted `.so` files available at real filesystem paths, which a JRE needs. Newer devices also enforce 16 KB page alignment for native libs.

## minSdk 28 and new-API types — class-init crash risk
- **Never declare a static/instance field (or a lambda's target type) of a platform class that only exists above `minSdk`.** A static field's type must be resolved while the class is initialized, so on API 28 referencing e.g. `PowerManager.OnThermalStatusChangedListener` (API 29) or `VibratorManager` (API 31) in a field throws `NoClassDefFoundError` wrapped in `ExceptionInInitializerError`. Touching the class at all — including from `Application.onCreate` — kills the process before any UI.
- **An `SDK_INT` check inside a method cannot prevent it.** Class initialization runs before any method body, so a guard inside `registerListener()`/`vibe()` is too late. Keep the new API's types inside a `@RequiresApi(Q)` nested holder and reach it only behind the guard. `ThermalGovernor`'s nested `Api29` is the worked example; `ThermalGovernorTest` asserts the outer class declares no `PowerManager` type.
- Constants are safe, types are not: `static final int X = VibrationEffect.EFFECT_TICK` is inlined by javac, so it does not create a class reference.
- Verify a fix without a device by scanning the compiled output (and the APK's dex) against the API 28 platform jar for `CONSTANT_Class` entries, or run `./gradlew :app:lintDebug` and read the `NewApi` section. Note `CONSTANT_String` entries such as `"android.intent.action.VIEW"` are data, not type references — counting them produces false positives.
- Known pre-existing (on `main`, not from the crash fix) `NewApi` findings: `StateListDrawable#getStateCount/getStateDrawable` (API 29) in `PersonalizationManager`, `URLEncoder#encode` (API 33) in `MsftLoginActivity`. These are in-method calls guarded by nothing, so they are real API 28 crash paths worth a separate fix.

## Runtime verification (no KVM/emulator in dev)
- No Android emulator/KVM here — verify via `./gradlew :app:compileDebugJavaWithJavac` (fast), `:app:compileDebugKotlin`, and a full `nohup ./gradlew :app:assembleDebug > /tmp/build_apk.log 2>&1 &` then grep for `BUILD SUCCESSFUL`/`FAILED`. To test on a device later (adb, ARM64 Android), install `app/build/outputs/apk/debug/app-debug.apk`, and:
  - Playtime: launch a version, wait ~20s, close; repeat — SharedPreferences `playtime_tracker` (total_ms_<profileId>) should accumulate ≈ real elapsed wall time (± a heartbeat interval).
  - Reduced-motion: enable Settings → Personalize → "show animations" OFF, confirm hero-card pulse stops (no translationZ/alpha oscillation) and press feedback is instant/no spring.
  - Glow: toggle "enable glow" OFF, confirm card elevation returns to 0 and gradient strokes disappear.
  - Latency: with the toggle ON, use `tcpdump`/`strace` or bpf to confirm TCP_NODELAY on launcher sockets, no DNS re-resolve during an active session, and that NewsRepository/GithubReleaseUpdater skip the network while a game is running.
  - Controller response: `./gradlew :app:testDebugUnitTest` covers the math. On device, with Low Input Delay ON the stick should reach full look output with less physical travel, and a resting drifting stick must still read as centred (no slow camera creep).
  - Font scale: set display font size to largest; no button label may be clipped in any activity (fixed `layout_height` on a text view is the cause).
  - Touch feedback: in ModsFullscreen, drag-to-reorder via the handle must still work — `applyPressScale` replacing that handle's `OnTouchListener` is the regression to watch for. Scrolling a list must not leave rows stuck at the pressed scale.

## Installations tab — pluggable package sources (core.installer + InstallationsActivity)
- The Installations tab (`LAUNCH, VERSIONS, INSTALLATIONS, MODS, CUSTOMIZE, SETTINGS`) lists the Bedrock versions a package source publishes and installs a chosen one as an isolated instance. The flow is fixed: resolve the package link → save into the app's private `Downloaded_APKs` folder (`DownloadedApksStore`, `getFilesDir()` with a `getCacheDir()` fallback) → run the existing `ApkImportManager` pipeline → **delete the file only after the import reports success**. A failed import deliberately keeps the package so "Retry Import" re-runs the pipeline without re-downloading hundreds of megabytes; deleting on failure would make that impossible.
- **The screen names no source.** `BedrockSource` is the abstraction (listing URL, version-page parsing, resolved-URL parsing) and `SourceRegistry` is the single source of truth for which mirrors ship. `InstallationsActivity` reads the active source and every URL/host from it, so adding a mirror is: implement `BedrockSource`, add it to `SourceRegistry.all()`. Nothing in the UI, download client or import pipeline changes.
- **Active source is `mcpedl.org`; `monster-mcpe.com` was removed.** mcpedl.org is plain HTTP with **no Cloudflare challenge** (`GET /downloading/` → 200), which is why the default path never needs a browser. The `core.monster` package is deleted — do not re-add it.
- **The mcpedl.org flow is three steps and the third one is the trap.** (1) `GET /downloading/` lists ~15 version cards linking to `/minecraft-pe-26-60-24-apk/`. (2) That page holds one `<form method="post" action="/show_file.php">` per build, each with hidden `post_title`/`file_id`/`post_url` fields. (3) Posting the form returns **HTTP 200 with an HTML body, not a 302**; the package URL is inside it as `window.location.href='https://file.mcpedl.org/...'`. `McpedlSource.parseResolvedUrl` reads that script (and falls back to a direct package anchor). `PackageSourceClient.submitForm` handles *both* shapes: a 302's `Location` header and a 200 body.
- **Form posts use a redirect-disabled client.** If the server ever answers a submission with a 302 to the package, a client that followed redirects would stream a several-hundred-megabyte file into memory before the caller could name it. `PackageSourceClient.postClient` (`client.newBuilder().followRedirects(false)`) reads the `Location` header instead and the file is then fetched deliberately as a second request — which is also what makes real progress reporting and `Content-Disposition` naming possible.
- **Version list parsing must exclude the index's own category links.** The index links `/downloading/minecraft-pe-26/` alongside the version cards; `VERSION_PATH` anchors at the site root (`^/(minecraft-pe-...-apk)/?$`) so a category can never be offered as a version. The **slug is authoritative** for the version (`versionFromSlug`), and the card's `MCPE 26.60.24` label is only trusted when it agrees with the slug — the window needed to reach a label can contain the *previous* card's, and adopting it would silently rename a row after a different release. The `release` flag is read from the marker beside that agreeing label ("Latest Release" vs "Latest Beta").
- **Build selection must prefer the arm64 build, and the variant labels are not trustworthy.** The page offers universal / +music / an ABI-labelled build. The APK ships arm64 only, so `BedrockSource.selectDownload` prefers an explicitly 64-bit label (`is64BitLabel`: `arm64`/`aarch64`), then falls back to the first build that is not `is32BitOnly()` (`armv7`/`armeabi`/`v7a` — deliberately not the `arm` prefix, or `arm64` would match). If *every* build is 32-bit it still returns one, so the caller can explain the ABI problem rather than claiming the version does not exist.
  - **The label does not reliably predict the contents — verified by reading the real APK central directories.** The convention flipped between releases, and both label styles occur: on `26-50-apk` the labelled builds are `arm64-v8a` and the unlabelled default is `armeabi-v7a` (label-based selection is required, which is why `selectDownload` does not simply take the first non-32-bit form), while on `26-51-apk`/`26-60-28-apk` the labelled builds say `armeabi-v7a` yet all four packages contain `arm64-v8a`. `26-60-24-apk` labels only the +music variant. So never claim a package's ABI from its label: the launcher's own `containsArm64NativeLibs` scan of `lib/arm64-v8a/` is the authority, and that check is what the user sees as "32-bit" when it fires.
  - The selection itself is unit-testable (`selectDownload` is static and works on parsed forms) even though the APK contents are not; the label logic and the fallback ordering are pinned by `McpedlSourceTest`.
- **A failed import must not leave an instance behind.** `ApkInstaller.install` writes the version directory and `base.apk.chimera` before the ABI check, and `VersionManager.loadAllVersions` treats *any* folder holding `base.apk.chimera` as an installed instance. On failure the method must therefore roll the version directory and the runtime lib directory back (it tracks a `committed` flag and cleans up in a `finally`), or a rejected 32-bit package appears in Instances and the user has to delete it by hand.
- **Listing pagination is a shared, unit-tested concern.** The index is dozens of pages deep (`rel="next"`; the live site uses `class="next"`), so `BedrockSource.nextListingUrl` reports the following page and the screen fetches one page at a time. The page/walk bookkeeping lives in `ListingPager` — a stack whose top is the current page, so the page number is the stack depth and the boundaries are `hasPrevious`/`hasNext`. Reading the count *before* pushing the new page left the label on "Page 1" and Back disabled after Next, which is why that arithmetic is a pure class with its own test rather than inline in the Activity. The walk is saved in `onSaveInstanceState` so recreating the screen does not silently drop the reader back on the newest releases, while Refresh deliberately calls `reset()`.
- **A listing page repeats the newest versions in its sidebar.** `McpedlSource.parseListing` must skip the `g-tagmenu-item` links (they repeat the newest handful on every page), or paging into the archive re-offers the newest releases forever.
- **File naming falls back to the URL.** `file.mcpedl.org` serves `application/octet-stream` with **no `Content-Disposition`**, so `PackageSourceClient.resolveFileName` uses the URL's last path segment when the header is absent; without that the package would land as `package.apk` and the derived instance name would be meaningless. `DestinationResolver` exists because the real name is only known once the response headers arrive, so the destination is chosen mid-download rather than before the request.
- **The browser is now a fallback, not the path.** `BrowserFallback` is only reached when a plain request is refused (`ChallengeException`, i.e. HTTP 403 or a Cloudflare interstitial). It runs the challenge in a real `WebView` (JavaScript + DOM storage + third-party cookies), polls `document.documentElement.outerHTML` until `BedrockSource.looksLikeChallenge` is false, and replays the cleared session's cookies/User-Agent on the package fetch. `looksLikeChallenge` exists so detection is testable and the UI can say "complete the browser check" instead of "no versions found".
- The challenge needs a visible window, so the WebView lives in `activity_installations.xml` and is only shown when `onChallenge()` fires. **The browser overlay must be a sibling of the content in a `FrameLayout` root, not a child of the vertical content `LinearLayout`** — as a child it is squeezed into leftover space instead of covering the screen. The session keeps polling after `onChallenge()`, so clearing the check resumes the same in-flight request; the Cancel button releases the session and settles on an explanatory message rather than reloading (which would immediately re-open the challenge).
- `ApkInstaller` now opens packages through `openInput`, which handles `file://` explicitly. Staged downloads are ordinary files in the private folder, and passing them through `ContentResolver.openInputStream` works on some OEM builds and throws on others. `getFileName` mirrors that for `file://`.
- `ApkImportManager.importUri(uri, versionName)` imports without prompting for a name, and `OnImportFailedListener` reports the failure (with the version name) so the caller can offer a retry. When that listener is set the manager must **not** also toast the error — the caller owns the failure UI and a toast would duplicate the message.
- `uniqueVersionName` picks a name no existing instance uses, because the import pipeline deletes the target directory before extracting: importing onto an existing name silently destroys that instance. This is also what makes "Reinstall" create a second copy instead of overwriting the first.
- **`McpedlSourceTest` pins the extraction rules against markup shaped like the live pages** (index cards, the three-row variant table, the scripted form response), and `installations_source_warning` is a format string taking the active source's host. The parser was also verified against the real captured HTML: 15 versions, correct release/beta flags, `armv7a` flagged 32-bit, and the resolved `file.mcpedl.org` URL. Fixtures must keep mirroring the real shapes — a hand-written fixture that only resembles them can pass while the live selectors are wrong.
- Lint: the project has **no lint baseline and `lintDebug` already fails** on ~355 pre-existing `MissingTranslation` errors (e.g. `mod_load_*`) because the `values-*` locales are not kept in sync. Adding strings inherits that; it is not a regression from this feature. Do not "fix" it by translating — that is a separate, much larger task.

## Recovering the build toolchain in a fresh container
A recreated dev container can come up with the source tree and previous build outputs intact but the JDK, Android SDK and `~/.gradle` cache all gone (`/opt/android-sdk` missing, no `java` on `PATH`). The repo's `local.properties` is gitignored, so it points at a path that no longer exists. Nothing in the project is broken in that state; the toolchain just needs reinstalling. `apt-get` will not work (no root), so install under a writable prefix and point `local.properties` at it:

```
# JDK 21 (Temurin, matches the toolchain named above)
mkdir -p /workspace/tools && cd /workspace/tools
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && export JAVA_HOME=/workspace/tools/jdk-21.0.12.1+1

# Android cmdline-tools, then the packages the build actually needs
mkdir -p /workspace/android-sdk/cmdline-tools && cd /workspace/android-sdk/cmdline-tools
curl -sL -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
python3 -c "import zipfile;zipfile.ZipFile('/tmp/cmdtools.zip').extractall('.')" && mv cmdline-tools latest
chmod -R +x /workspace/android-sdk/cmdline-tools/latest/bin
yes | latest/bin/sdkmanager --sdk_root=/workspace/android-sdk --licenses
nohup latest/bin/sdkmanager --sdk_root=/workspace/android-sdk \
  "platform-tools" "platforms;android-36" "platforms;android-35" \
  "build-tools;36.0.0" "build-tools;35.0.0" "ndk;28.2.13676358" "cmake;3.22.1" \
  > /tmp/sdk.log 2>&1 &

printf 'sdk.dir=/workspace/android-sdk\n' > /workspace/project/ChimeraClient/local.properties
```

Notes: `unzip` is not installed — extract with `python3 -c "import zipfile..."` instead. The zip contains a `cmdline-tools/` directory that must be renamed to `latest` or `sdkmanager` refuses to run. `sdkmanager` runs long; start it with `nohup ... &` and poll `/tmp/sdk.log` (a foreground command with a large timeout is rejected). Cmdline-tools land non-executable, hence the `chmod`. Budget ~3 GB for the SDK plus ~200 MB for the JDK.

## Controller illustrations (ControllerIllustrationView + ControllerLayout)
- **`setType()` must call `invalidate()`, not just `rebuild()`.** Clearing and repopulating the region list does not repaint, so the previous pad's shell stayed on screen and the manual "Next" button looked dead after the first press. That was the "only Xbox renders" bug.
- **The view owns no geometry.** `ControllerLayout` holds the shell Bezier segments and the region table, in two different but load-bearing conventions: the shell is centre-relative in *scale units*, regions are normalised 0..1 and converted through `regionDx`/`regionDy`. The view draws what the layout returns. Moving geometry back into the view puts it out of unit-test reach again.
- **`ControllerLayoutTest` is the regression gate, and it exists because these bugs are invisible on a headless build machine.** It asserts every region draws inside its shell, that no two controls overlap, that the Xbox map stays asymmetric while the PlayStation maps mirror, and that the DualSense alone gains the mute bar. It has already caught three real defects: an off-shell Xbox `x`/`menu` overlap, DS4 bumpers poking through the shoulder, and triggers that were never going to fit. Run `:app:testDebugUnitTest` after touching any coordinate.
- Rounded regions (bumper, trigger, touchpad, mute) are *not* circles — `Spec.halfWidth()`/`halfHeight()` carry their real draw extents. Testing them as circles reports false containment failures and hides genuine ones.
- **Triggers are deliberately clipped to the shell.** `drawTrigger` clips to `shellPath`, so the arc can extend past the silhouette and read as emerging from behind the body. On the Xbox, whose shoulders angle steeply, a fully-contained trigger cannot fit without shrinking past legibility. That is why the test exempts `TRIGGER` from containment and only checks it stays within reach.
- Overlap detection uses centre distance for round controls (diagonal ABXY neighbours trip an axis-aligned gap test) and skips a bumper against the trigger beside it, since those are stacked at different depths on the shoulder and are meant to touch.
- `handleMotionEvent` must report every axis lit *or* unlit on each event. Only ever setting the glow left sticks and the d-pad highlighted forever once touched.
- `drawRegion` offsets must stay inside the parentheses: `cx + (r.x - 0.5f) * 2f * scale`. Written as `cx + r.x - 0.5f * 2f * scale` every control lands at centre-minus-scale and the layout piles into the left half.
- Shell gradients are built in `buildShaders()` on size/theme change, never per frame.
## Combat modules: Armor HUD, Crystal Optimizer, Hit Registration, Hitboxes, Select Hit
Five modules in the Mod Menu's PvP tab, all under `core.mods.inbuilt.overlay`. Each is built as
**decision/logic + a game-data seam**, because this repo cannot read live game state (no
`libminecraftpe.so`, no entity structures — the shipped signature rules cover only five
menu/HUD addresses). The seam is the honest boundary; nothing fabricates a reading across it.

- **`ArmorHudMod` + `ArmorHudOverlay`.** Durability/enchant readout for self and, optionally,
  the current target. `ArmorHudMod.DataSource` is the seam: `read()` returns a `Snapshot`, and
  `Snapshot.absent()` means *no data*, which is deliberately distinct from *wearing nothing*.
  The overlay draws a dimmed `-` per cell when absent. Never render absent as full durability -
  that reads as a healthy armor set when nothing is known. A throwing or null-returning
  `DataSource` degrades to absent rather than crashing the overlay. `Piece.fraction()` returns 1
  for a zero max so an unknown piece never renders as empty.
- **`CrystalPlacementSolver` + `CrystalOptimizerMod` + `CrystalOptimizerOverlay`.** The solver is
  pure geometry and fully unit-tested: it ranks obsidian/bedrock candidates by `blastDamage`, a
  quadratic falloff to zero at `MAX_EFFECTIVE_DISTANCE`, expressed in **half-hearts**
  (`MAX_DAMAGE_HALF_HEARTS = 24`, above a full 20 health bar so a point-blank placement is
  correctly rejected as lethal to self). Candidates are rejected when out of `maxRange`, when
  self-damage exceeds target-damage, or when the blast would drop the player below `minSelfHp`.
  `CrystalOptimizerMod.WorldSource` is the seam; `evaluate()` returns null without one.
  - **Manual assist is the default and is listed first in the settings dialog.** It highlights
    the spot and never acts. Automatic placement is the explicit opt-in, because a client that
    places and breaks blocks for the player is what servers restrict or ban —
    `crystal_optimizer_fairness_note` says so in-app.
- **`HitRegistrationMod`.** Shapes the look input the device sends; it cannot make a miss hit,
  because Bedrock resolves hits server-side. `hitreg_scope_note` states that in-app. Shaping is
  sensitivity, EMA smoothing, and sub-frame prediction. **Micro deltas (below `MICRO_DELTA`)
  bypass smoothing and prediction entirely** — predicting a slow track would overshoot the target
  the player is settling onto, which makes aiming worse. Prediction only applies to a continuing
  burst (`hasLast` and within `BURST_GAP_MS`), so the first frame of a flick is never
  extrapolated from a stale one. Sensitivity is applied *before* the micro-delta test, so a micro
  delta is still scaled; the tests pin that ordering.
  - It is **mutually exclusive with `AimSettingsMod`'s shaping** at the send step: each carries
    its own smoothing, so running both compounds the damping.
    `MinecraftActivity.pojavSendLookDelta` picks Hit Registration when active, else Aim Settings.
    Aim Settings still draws its crosshair.
  - Wired into the controller hot path at `ControllerInputProcessor.shapePair` for the **right
    stick only** (the look stick), reusing the caller's buffer so the path still allocates
    nothing. `SystemClock` is not mocked in unit tests, so `TimeSource` is injectable
    (`setTimeSource`); `HitRegistrationModTest` installs a `FakeClock`.
- The `armor_hud` / `crystal_optimizer` / `hit_registration` / `hit_timing` / `hitbox` ids live
  in `ModIds`, with all five in `PVP_MODULES` so they land in the PvP tab.
  `ModIds.requiresGameData(id)` marks the three that need a native feed (armor, crystal, hitbox).
  All are registered in `InbuiltModuleProvider` (entries, configs, setters, and an explicit
  `createCombatConfigSchema` category layout) and in `InbuiltOverlayManager`
  (show/hide/tick/visibility/reset-position), and they set `customConfig = true`.
- Tests: `CrystalPlacementSolverTest`, `ArmorHudModTest`, `HitRegistrationModTest`,
  `HitTimingSolverTest`, `HitboxProjectorTest` (no mocks).
- **`createCombatConfigSchema` descriptions and scope notes.** `configNode` attaches the
  `_desc` string for a config key via `configDescriptionRes(key)`, and each combat module appends
  an `info` node carrying its scope note (`hitreg_scope_note`, `hit_timing_scope_note`,
  `hitbox_scope_note`, `crystal_optimizer_fairness_note`, `armor_hud_no_data`). Those notes
  already existed but had **no consumer in code or layout** — a module named after an outcome
  ("Hit Registration") that stops short of it reads as broken unless the dialog says why.
  `configDescriptionRes` is an explicit switch, not a name lookup, so a renamed key cannot
  silently bind to an unrelated string. The note node goes in the default category, so it is the
  first thing read.

## Hitboxes module (HitboxProjector + HitboxMod + HitboxOverlay)
Draws entity bounding boxes plus two combat guides, and is the one overlay that is **not**
draggable — boxes are world-positioned, so the whole screen is the canvas and it is created with
`FLAG_NOT_TOUCHABLE` so it can never eat a tap or a look gesture. `HitboxMod.EntitySource` is the
game-data seam; with no provider `readFrame()` returns null and **nothing is drawn**, because an
invented box is worse than no box — the player would aim at it.

- **`HitboxProjector` is pure** (no Android/game types) and holds all the arithmetic: the camera
  basis, the box projection, the crit line, the combo box, and the crosshair slab test. The
  overlay only paints what it returns. `HitboxProjectorTest` is the gate.
- **Yaw is clockwise from +Z (the Minecraft convention), so `Camera.forward()` negates its X
  component.** The naive spherical form `(sin yaw, …)` is counter-clockwise and mirrors every
  projected box horizontally — a mismatch that is invisible until a real feed supplies yaw.
  The test `turningTheCameraMovesTheBoxOffCentre` caught exactly this. `pitchingUpMoves…` and
  `aHigherEntityProjectsHigher…` pin the up vector, which nothing else covers.
- **The look line is a screen-space anchor, not a world ray.** A camera's own forward ray
  projects to a single point (the centre), so a world ray would have nothing to draw. The line
  runs from a point below centre up to the crosshair; an entity turns blue when the crosshair
  actually points at it, which is decided by `crosshairHits` (a slab test) so the highlight
  cannot disagree with the geometry drawn.
- **Colours are the contract, kept in one place**: entity boxes white, aimed-at box blue, crit
  line red (blue when aimed), combo box red (blue when aimed). Guides are players only —
  `onlyPlayersGetCombatGuides` pins that mobs/items/projectiles get a plain box.
- Far-to-near sort so a nearer box paints over one behind it. A box behind the camera produces
  no screen rect and is skipped.

## Select Hit module (HitTimingSolver + HitTimingMod + HitTimingOverlay)
A small green/red pill centred at the top of the screen: green while a hit will land, red while
the post-hit window is still open, with the combo count beside it and a slim progress bar. Sized
(`96x26dp`) so it clears the crosshair area and hotbar, and it is draggable in HUD-editor mode
only, like the other overlays.

- **`HitTimingSolver` is pure** — the timing rules are unit-testable without a Context or clock.
  `evaluate(nowMs)` returns `READY` / `HIT` / `WAIT` plus `remainingMs`, `progress` and `combo`.
- **A click inside the window must not advance the combo or push the window out.**
  `aDiscardedClickDoesNotAdvanceTheCombo` pins it: counting a discarded click would make the
  indicator lie about both the streak and when the next hit lands. A backwards clock (uptime
  wrap) is treated as a fresh engagement rather than producing a negative remaining time.
- The window is clamped (`50..2000ms`) and exposed as a config slider, because the real cooldown
  varies by version — the indicator must not be silently wrong on an unlisted build.
- Attack input is recorded from the paths that actually send an attack: `pojavSendMouseButton`
  (primary) and the `dispatchKeyEvent` mouse-button path (controller). Both call
  `InbuiltOverlayManager.notifyAttack()` → `HitTimingMod.onAttack(uptimeMillis())`.
  `dispatchKeyEvent` records **before** the preloader may consume the press — the player pressed
  attack either way, and the timing must be the real input timing.
- Honest scope: it reads your own attack input and never clicks for you; the server still decides
  whether a hit lands. `hit_timing_scope_note` says so in the dialog.

## In-game Mod Menu navigation & overlay visibility
- The Mod Menu nav is a **top bar** inside `overlay_mod_menu.xml`, not a side rail (landscape-only app; a rail only gets the short edge). Entries live in a `HorizontalScrollView` so compact mode's narrow window still fits every destination and the close button.
- **An unresolved native HUD hook must not hide a mod's UI.** `OverlayVisibility.showGameOverlays` treats `hudScreenOpen == false` as authoritative only once the hook has fired (`gameWorldSeen`); before that it falls back to `sessionActive`. Without the fallback, an overlay appeared only while the Mod Menu was open and vanished the instant it closed. The fallback signals are part of `tick()`'s state hash — a session starting must re-evaluate visibility even when every native flag is unchanged.
- **`gameWorldSeen` is never set false.** The HUD hook only *ever* reports true once installed; a false is indistinguishable from "not installed", so treating it as "left the world" would re-hide overlays after the first menu.
- Compact mode **narrows** the window (`applyCompactModeLayout`); it must never hide the navigation. The old sidebar/compact-icon swap was the bug.

## Controller remap layers & rumble (RemapLayer + RumbleCurve + ControllerRumble)
- **A layer is an alternate button map selected by a held modifier.** `RemapLayer` holds a
  modifier key code and its own `Map<Integer,Integer>`; `ControllerProfile.getRemapLayers()`
  carries them and `ControllerProfileCodec` persists them. `ControllerResponse` precomputes one
  flat `int[]` per layer so the hot path (`remapKey(keyCode, modifier)`) still allocates nothing.
- **A button the layer does not list falls through to the base map**, not to identity — otherwise
  holding a modifier would silently un-remap every button the layer does not mention.
- **The modifier is tracked by *raw* key code**, before any remap: a base remap that changes the
  modifier button's meaning must not stop it selecting. `ControllerInputProcessor.handleLayerModifier`
  returns true for the modifier press so `MinecraftActivity.dispatchKeyEvent` can swallow it — the
  modifier is a switch, not an action. `resetLayerState()` runs in `setActiveProfile`.
- **Layers only apply to a gamepad.** `ControllerResponse.isGamepadKeyCode` is an explicit set, not
  a range test: the lettered faces sit at 96..110 while `KEYCODE_BUTTON_1..16` sit at 188..203, so
  no min/max works. The d-pad codes are included. A keyboard key sharing a code must not swap a
  player's whole map while they type.
- **`ControllerRumble` reaches the pad through API 31+ `InputDevice.getVibrator()`, behind a nested
  holder** — `getVibrator` does not exist on minSdk 28 and referencing it from a reachable method
  would class-init crash. Below 31 it returns false and the caller falls back; `RumbleCurve` owns
  the strength→amplitude math (a 1% strength must stay above `MIN_AMPLITUDE`, not round to a value
  a vibrator cannot render). Pinned by `RemapLayerTest` / `RumbleCurveTest` (no mocks).

## Mod Menu live stats strip (ModStatsFormatter)
- **FPS / ping / battery in the top bar, refreshed on a one-second tick only while the menu is
  open** (`ModMenuOverlay.statsTick`, registered in `showInternal`, removed in `hide`). FPS reads
  the same inbuilt `FpsMod` the FPS overlay uses so the two cannot disagree; battery reads the
  sticky `ACTION_BATTERY_CHANGED` broadcast.
- **An unknown reading renders an em dash, never `0`.** A "0 FPS" or "0 ms" reads as a real
  measurement. `ModStatsFormatter` owns that rule and is pure, so `ModStatsFormatterTest` pins it.
  Ping has no source in this build and correctly shows the em dash.

## Controller input latency (StickDriftGate + ControllerInputProcessor)
- The anti-drift gate must release on **elapsed time** (`SUSTAIN_MS`), not only on event count. Many pads deliver a motion event only when an axis *changes*, so a stick held at a steady deflection across the threshold produces one crossing and no second event; an event-count-only rule held that deflection back indefinitely, which the player feels as input delay.
- `ControllerInputProcessor.transformMotionEvent` uses pooled scratch arrays so the hot path allocates nothing. Keep it that way — it runs per controller event on the input-to-photon path.
- Controller illustration realism comes from **hard-edged moulding**, not soft washes: a domed cap is two concentric circles of decreasing radius, a recess is a directional inner shadow arc, a highlight is one tight off-centre circle. A large translucent oval across the shell reads as a smeared "spilled water" highlight. Never allocate a `Paint` inside a draw method (the old stick/d-pad code did); the view caches one per role.

## Cosmetics preview scope
- `CosmeticsPanel` + `CapePreviewView` + `SkinModel` / `PlayerSkinProvider` / `CapeSimulator` (in `core/cosmetics`) render the player's character with their applied skin and the equipped cape, animated. The catalogue and selection are pure and unit-tested.
- **In-game capes work through a resource pack, not a native hook.** Bedrock has no third-party cape API, but the player client entity already binds the shortname `cape` to `textures/entity/cape_invisible` and ships `geometry.cape` in the vanilla model. A pack that overrides that texture makes the cape the player equipped in the dressing room render with the pack's art — this is how the community cape packs work. `CapeResourcePackBuilder` generates the pack, `CapeInGameInstaller` installs it via `SkinPackActivator` (the launcher's existing, proven path into `resource_packs/` + `minecraftpe/global_resource_packs.json`). Do not hand-write a second writer for those two files.
- **The pack is texture-only on purpose.** It must NOT override `player.entity.json`: that is the known way cape packs break, because it disables the Character Creator and can make capes vanish unless a `min_engine_version` workaround is used that has to be re-verified per release. Overriding a texture cannot break the model, so the worst case is an invisible cape rather than a broken avatar.
- **Animated capes are not possible this way.** Animated entity textures need a custom material with `USE_UV_ANIM` plus a render controller, and the wiki flags materials as unreliable under RenderDragon. Animation therefore stays in the launcher preview only (`CapeSimulator`); the in-game pack is static. `cosmetics_scope_note` says so rather than implying the in-game cape animates.
- **The preview has no depth buffer, so draw order *is* the depth order.** `CapePreviewView.onDraw` paints ground shadow → wings → cape → body → head accessories. Wings are back-worn, so they must be drawn in a `drawAccessoryBehind` pass before the cape and body; drawing all accessories last (the original code) put the wings flat across the torso and read as a sheet stuck to the chest. `CosmeticLayering` holds the three planes (−z is behind: body back −2, cape −2.4, wings −2.9) and `CosmeticCatalogTest.wingsAndCapeAreLayeredBehindTheBody` pins the ordering, so the planes can be tuned without silently reshuffling the layers.
- `CosmeticCatalog.Accessory` is only a colour + id; `drawAccessoryBehind`/`drawAccessoryFront` switch on the id. Headphones anchor to the head box's real bounds (y 24..32, x −4..4) with the band arcing over the crown and cups just proud of the ears — a band anchored to the model's centre floats above the head.
- `PngWriter` is a hand-rolled PNG encoder so cape textures are byte-inspectable in a JVM unit test (`CapeResourcePackBuilderTest` round-trips the IDAT through `Inflater`). `android.graphics.Bitmap` would be shorter but only exists on a device. Note the PNG chunk layout when testing: the 4-byte length sits 8 bytes before the chunk data (length, 4-byte type, data), and a wrong offset yields a multi-megabyte length and an OOM rather than a clear error.
- `CapeResourcePackBuilder.PACK_UUID` is a fixed literal, not generated per build: a random uuid would accumulate one pack per cape change in the instance instead of replacing the existing one in place.
- `WorldSource` / `DataSource` / `EntitySource` are still unimplemented (see the combat-module section); capes are the one cosmetic that reaches the game, and only because a resource pack is a supported mechanism.
- **The preview needs a recorded pack name to find the skin.** `PlayerSkinProvider.findAppliedSkinFile` reads `applied_name` from the `skins_state` prefs, so `SkinsSettingsFragment` must *write* it (via `PlayerSkinProvider.setAppliedSkinPackName`, cleared on removal) when it activates a pack. Both sides previously only read the key and nothing wrote it, so the lookup always returned null and the preview silently showed the placeholder while the real pack was active in the game. A prefs key that is read but never written is the failure mode to check first when a "real X" preview looks generic.
- **`normalise` must not stretch to the atlas.** The atlas is a 64x32 grid, so the source is divided by a whole factor of its width and drawn top-left (`atlasDrawSize`): a 64x32 legacy skin keeps its height and occupies the top half, a 128x128 HD skin halves to 64x64. Filling 64x64 instead doubled a legacy skin vertically and put the arm/leg regions where the hat/body overlay belongs, so the character rendered its own textures in the wrong places. The sizing rule is pure and pinned by `PlayerSkinProviderTest`.

## Combat module empty states
- A module that needs a native feed (Crystal Optimizer's `WorldSource`, Armor HUD's `DataSource`, Hitboxes' `EntitySource`) must distinguish **"no data source"** from **"data source says nothing is there"**. Crystal Optimizer's `isAwaitingGameData()` renders "waiting for game data"; without it the readout said "no safe spot", blaming the player's aim for a feed that does not exist. `HitboxOverlay` shows an equivalent "awaiting data" state.
- `TouchTapDetector` classifies a touch attack for the Select Hit metronome. A `POINTER_UP` for a *different* pointer must not end the gesture — ending there dropped the real tap the moment a jump/sneak button was released. Fixed and pinned by `TouchTapDetectorTest`.

## Native entity/camera feed — what is and is not reachable (verified against 1.26.50.04_RC3 and 1.26.60.28)
Verified by downloading the real `lib/arm64-v8a/libminecraftpe.so`. First against `26-50-arm64-v8a`
(363,050,262-byte APK; the `.so` is stored deflated at 98,404,093 bytes so only that range needs
fetching, then inflate with a raw zlib stream). Then re-verified independently against
`26-60-28` (382,360,914-byte APK from mcpedl.org, `file_id` 7572 -> `minecraft-26-60-28.apk`;
`libminecraftpe.so` is 368,150,880 bytes, deflate ratio ~0.30). Do not re-run this RE from scratch;
these are the conclusions.

- **Game classes are stripped of exported symbols.** The 90,665 defined dynamic symbols in
  1.26.60.28 are third-party only (v8/cohtml/webrtc/Xal/xbox/leveldb/`std`). There is no
  `_ZN...Actor...`, `_ZTV...Level...` or any Minecraft method symbol, so `dlsym`/`resolveSignature`-
  by-name cannot reach game state. The only `Java_*` exports are host plumbing (`MainActivity`,
  `BatteryMonitor`, `NetworkMonitor`, `JellyBeanDeviceManager`, Xbox/XAL interop) -- there is no
  export that returns a `Level*`/`ClientInstance*`/player position.
- **The `.text` is real AArch64, not encrypted.** A `ret` (`c0 03 5f d6`) appears ~22k times and
  section entropy is ~6.6 bits/byte, so byte-pattern signatures *can* in principle be derived from
  the shipped file. That does not make the feed reachable -- see the fragility note below.
- **RTTI *name strings* exist, and `resolveVtableFunction` does resolve them.** Standalone
  `11LocalPlayer`, `5Level`, `14ClientInstance`, `11BlockSource`, `5Actor`, `6Player` strings are
  present in `.rodata` (each exactly once) and each has exactly one RELATIVE relocation resolving
  its address, so the typeinfo and its vtable are found. Emulating `Vtable.cpp`'s algorithm against
  1.26.60.28 resolves `14ClientInstance` slot 151 to a `.text` address, so **the shipped
  `isShowingMenuVtableIndex` mechanism survives a version bump** and needs no new binary.
- **The vtables reached this way are the pure-virtual *interface* bases.** Confirmed again on
  1.26.60.28: `11LocalPlayer`'s primary vtable slots 0,1,4,5 are **identical** to `6Player`'s
  (`0x11232294`, `0x11a115b4`, `0x115d674c`, `0x11a2e908`), the shared-base pattern. Slot indices
  are also version-fragile (`isShowingMenuVtableIndex` is 151 on 1.26.50 but 150 on 1.26.40).
- **The shipped 1.26.50 byte patterns mostly do not match 1.26.60.28.** Scanning `.text` with the
  bundled rules as-is: `pauseMenuOpenSig` matches 2 places (ambiguous), `hudScreenOpenSig` and
  `pauseMenuDtorSig` match 0. `resolveSignatures` takes the first hit, so this is the concrete
  evidence that a byte-pattern feed needs a fresh pattern per build and cannot be inherited.
- **The RTTI relocations are the vtable structures, not calls to a named method.** Of 610,452
  unique `R_AARCH64_RELATIVE` addends, 110,370 point into `.rodata`, but **none** point at the
  method-name strings (`Actor::getFilteredNameTag`, `Player::setupCamera`, ...) even though those
  strings exist -- so they cannot be xref'd via the vtable structures. Finding the function that
  reads a player's position needs a disassembler pass over the 220 MB `.text`; `resolveVtableFunction`
  gives a vtable, but a static vtable address alone does not yield a live `Actor*`.
- **The *entity* feed a hitbox/crystal/armor/nametag module needs is therefore not derivable
  statically.** A `dynamic_cast` on a live `Actor*` would identify `LocalPlayer`, but there is no
  way to obtain the `Level*`/`ClientInstance*` to start from without either a byte-pattern signature
  per build or a `_ZTI...`-style mangled symbol, and this build exports neither for game classes.
  The in-repo `resolveSignature` is the right mechanism but needs patterns derived from the binary.
- **The *local-player* feed, however, is implemented and is a different problem.** The in-world
  Voice nametag icon needs only the listener's own view, and the local player is reachable without
  any signature: `ClientInstance`'s vtable **slot 31** returns the local player, and that slot is
  resolved **by RTTI name** through `resolveVtableFunction("14ClientInstance", 31, ...)` -- no
  per-build code address. The preloader hooks that slot and snapshots position (`Actor+0x230`) and
  view rotation (`Actor+0x238`); both field offsets are shared by 1.26.33.1 / 1.26.50.4 / 1.26.51.1
  (confirmed against the 1.26.50.4 and 1.26.60.28 binaries). Live pointer dereference happens **only
  inside the hook** (game thread, right after the game returned the pointer); Java reads a
  seqlock-protected copy with a staleness window, so a session ending cannot leave the UI thread
  reading freed memory. See `GameLocalPlayer.cpp` in the preloader. This does **not** unblock the
  combat modules -- they need *other* entities' positions, which is the unreachable problem above.
- **The game's Java layer is no help.** The dex contains 59 `com/mojang/minecraftpe/*` classes in
  1.26.60.28 (down from 68), all Android host plumbing (MainActivity, PlayIntegrity, FilePicker,
  Braze, WorldRecovery) plus PairIP-obfuscated stubs. There is no player/entity/position/camera API
  and no `native` method declaring one -- all game state lives in `libminecraftpe.so`.
- **The preloader exposes no ergonomic player/level accessor.** Its `PL_EXPORT` surface is memory
  primitives (`resolveSignature`/`resolveVtableFunction`/`hook`/`writeBytes`), input callbacks, and
  Mod Menu/HUD submission -- nothing that returns a world position. So even a native mod has to
  build the feed from the same signatures.
- **`WorldSource` / `DataSource` / `EntitySource` have no provider and are never installed**
  (`setWorldSource`/`setDataSource`/`setEntitySource` have zero callers). Crystal Optimizer, Armor
  HUD and Hitboxes therefore cannot produce output; they correctly show their "awaiting game data"
  state. Implement one only with a verified signature set, and keep the honest empty state.
  `TagSource` is the exception: the in-world Voice nametag icon *is* installed now (see the
  local-player feed bullet above), because the peer positions it needs arrive over the voice
  protocol and only the local view comes from the game.
- **A cape feature is not reachable through this.** The in-game cape is a resource pack (see the
  cosmetics section), a supported mechanism rather than a native hook.
- Static-analysis caution: RTTI pointers in this PIE binary live in `.data.rel.ro` as
  `R_AARCH64_RELATIVE` relocation addends, so a plain byte scan for the pointer finds **nothing**
  and produces a false "RTTI is absent" conclusion. Read `.rela.dyn` instead. Equally,
  `re.finditer` over a 328 MB `.so` is unusable -- extract `strings` once and work from that.

## Select Hit touch ordering
- **`dispatchTouchEvent` must classify the attack tap before the preloader can consume the event.**
  `PreloaderInput.onTouch` returning true means a registered callback claimed the touch, and the
  old code returned early -- so `TouchTapDetector.onUp` was never delivered for that pointer. The
  next DOWN re-armed tracking from a fresh timestamp, and a tap made right after a consumed
  gesture was timed from the wrong start, read as a hold, and was dropped. The detector is a
  passive observer of the gesture the game already acted on, so it sees every event regardless of
  who consumes it -- the same reason `dispatchKeyEvent` records the mouse-button press before the
  preloader may consume it. `overlayManager.handleTouchEvent` moved up with it (it also maintains
  gesture state). Pinned by `TouchTapDetectorTest.aSwallowedUpDoesNotPoisonTheNextTap`.

## .AntEgg mod packaging (core.antegg + preloader AntEggLoader)
- **`.AntEgg` is a ZIP archive with a custom extension.** The manifest `egg.json` sits at the
  archive root and carries `name`/`version`/`author`/`type` (`native`|`script`)/`entry_point` and
  an optional `dependencies` array. `AntEggManifest` (Java) and `pl::runtime::AntEggManifest`
  (C++) parse the same grammar; both reject a missing field, a non-semver version, a type that
  disagrees with the entry-point extension, or an `entry_point` that escapes the package.
- **Two runtimes, two seams.** `type: "native"` hands the extracted `.so`'s absolute path to
  `ModManager.initializeLoadedMod` -- the same preloader injection an ordinary native mod uses;
  `.AntEgg` adds packaging/validation, not a second injection mechanism. (The original spec calls
  this API "ApexAntLamina"; no class or symbol by that name exists in this tree -- it is the `pl`
  preloader bridge.) `type: "script"` runs the entry `.lua` on the embedded Lua VM (`luaj.jse`,
  Lua 5.2) through `AntEggScriptHost`, which populates a `mod` table (`name`, `version`, `author`,
  `id`, `dir`, `sandbox`, `dependencies`).
- **Script failures are returned, never thrown** -- a broken script shows an error in the Mods tab
  instead of taking the launcher down. `AntEggScriptLoadingTest` runs a real archive through
  extract-then-execute and pins both error paths.
- **Extraction is sandboxed and atomic.** `getExternalFilesDir()/AntEggs/<mod-id>/` (internal
  fallback); every ZIP entry is resolved and checked to stay inside the sandbox (entry names are
  attacker-controlled), and the archive is staged in a temp sibling and moved into place only when
  complete, so a failed import leaves no partial mod. `.antegg` is a recognized import extension
  in `FileHandler` and an intent-filter path suffix in the manifest.
- Native: `AntEggLoader.cpp`/`AntEggJni.cpp` use zlib raw inflate (no new third-party ZIP dep),
  bound to `AntEggBridge.nativeValidate`/`nativeLoadMod`/`nativeLooksLikeAntEgg`. The symbol
  prefix is `Java_org_chimeramc_client_core_antegg_*`; verify with `llvm-nm -D`.
- Template and format docs: `examples/antegg-template/`.

## Proximity voice chat (core.voice + VoiceChatOverlay)
- **A launcher-side peer link between Chimera users in the same world**, over LAN multicast UDP.
  Transport = `VoiceTransport` (MulticastSocket), framing = `VoiceProtocol` (raw 16 kHz mono PCM;
  no per-ABI codec), mixing = `VoiceRegistry` + `VoiceChannel`, device = `VoiceAudioEngine`.
  Nothing talks to the game.
- **Channels are the multi-channel contract.** A listener hears their own channel plus the open
  `world` channel in both directions (so switching to a private channel never cuts off someone
  standing in front of you); two different private channels do not hear each other. Gain falls
  from 1 to 0 across the range with a linear band (`VoiceChannel.gain`). Pinned by
  `VoiceChannelTest`/`VoiceProtocolTest`.
- **The position seam is installed, and "installed" is not "live".** `LocalPlayerFeed` (in
  `core.mods.inbuilt.overlay`) installs `VoiceChatModule.setPositionSource(...)` from the same
  `ClientInstance` vtable slot 31 the nametag mic icon uses, so distance falloff applies on both
  transports once a world is loaded. The native read is **fail-closed**: with no world loaded
  (before the first frame, on a loading screen, after leaving a session) it returns null, which is
  a different state from "no feed". `isChannelMode()` reports the persistent state
  (`positionSource == null`); `hasLivePosition()` reports whether a position is readable *this
  frame*, which is what actually decides whether distance is in effect. Calling `isChannelMode()`
  on the audio path was the bug: a null read collapsed to the origin, so every peer was graded
  against a phantom (0,0,0) and dropped. The gain rule (`VoiceChatModule.gainFor(...)`, pure and
  pinned by `VoicePositionGainTest`) now treats a missing position as channel mode — never as
  distance from the origin — and `isChannelMode` is only a status predicate.
- **`sanitizePosition` rejects a malformed feed value.** A NaN/infinite component from a torn
  native read would propagate into the distance math and silence audio with no diagnosable cause,
  so a value that is not three finite numbers reads as "no position" and falls back to the channel
  rule. The Voice tab renders which state is active (`voice_proximity_mode`), so a player in a
  loading screen is told why everyone is at full volume.
- **`VoiceAudioEngine.ensureCapture`/`stopCapture` exist because a mic toggled on after a
  listen-only start must open the recorder**; a bare `setMicEnabled` only gates an already-running
  capture loop. `applyConfig` calls them. The RECORD_AUDIO permission is requested when voice
  starts (`InbuiltOverlayManager.REQUEST_VOICE_MIC`); if denied the module still starts listen-only.
- The module is `ModIds.VOICE_CHAT` in `GROUP_VOICE` (the `Voice` section), a non-PvP module, so
  `groupPvpLast` keeps it a single contiguous run. `VoiceModuleGroupingTest` pins the grouping.
- **Protocol v2 adds a visibility byte and a human-readable channel name, and keeps v1 decodable.**
  `VoiceProtocol.VERSION = 2`; a v1 datagram still decodes as PUBLIC on channel `world` (what v1
  described, since v1 had no private room). An unknown visibility byte clamps to PUBLIC so a
  malformed packet cannot hide a channel. Pinned by `VoiceProtocolTest`.
- **A private channel's id _is_ its join code** (`VoiceChannel.generateCode` -> `CHIMERA-7F2Q`).
  There is no invite protocol: typing the code normalises to the same channel id and the existing
  `canHear` match does the rest, so sharing the string is the whole mechanism. The alphabet excludes
  `O/0/I/1/L` because a code is read aloud and typed by hand. `isJoinCode` distinguishes a code from
  a plain public room name.
- **`VoiceChannelDirectory` is built client-side from the registry snapshot** -- no server, no
  query. Private channels are filtered out of the listing (a browsable list is public by
  definition); the latest advertisement wins for name/visibility, so a rename or a switch to public
  shows immediately. Pure and pinned by `VoiceChannelDirectoryTest`.
- **The Voice tab (`VoiceChatActivity`) is a view onto existing state.** It drives `VoiceChatModule`
  directly -- the singleton is shared with the in-game overlay -- because `InbuiltOverlayManager`
  only exists during a game session. Channel selection writes the same preference the module
  beacons; there is no second source of truth. The module dialog's free-text channel field routes
  through the same `joinVoiceChannel(...)` path, so a typed room name is advertised rather than
  falling back to the id.
- **The in-game indicator is `MicIndicatorView`**, a blocky 12x12 pixel-art meter (anti-aliasing
  off) driven by the real smoothed microphone RMS (`VoiceAudioEngine.updateLevel`), with fast attack
  / slow release so it reacts on the first syllable and does not flicker. Use RMS, not peak (a meter
  tracks loudness); a muted mic drops the level to zero rather than freezing it. `rms`/`sprite` are
  package-visible so `VoiceAudioEngineLevelTest`/`MicIndicatorViewTest` can pin them.
- **Protocol v3 adds capacity, live level and self-mute to the beacon/audio packets, and keeps v2
  and v1 decodable.** `VoiceProtocol.VERSION = 3`; `decode` accepts `VERSION_LEGACY..VERSION`
  (`version < VERSION_LEGACY || version > VERSION` rejects), and a v2 datagram decodes with
  `capacity = CAPACITY_NONE`, `level = 0`, `muted = false` rather than being dropped -- a newer peer
  must not silence an older one. `capacity` clamps to `MAX_CAPACITY`, `level` is clamped by
  `clampLevel` (NaN reads as 0). Pinned by `VoiceProtocolV3Test`.
- **Capacity is advisory, never enforcement.** `VoiceChannelCapacity` holds the rule: a public room
  is full at/above its cap, `CAPACITY_NONE` (<= 0) never reads as full, and a **private channel
  always admits by code** (`canJoin(..., private=true)` ignores the cap) because the code already
  bounds who can join. There is no server, so a host cannot eject anyone; the cap is advertised in
  the directory and a would-be joiner declines a full room. `clampHostCapacity` bounds the settings
  stepper. Pinned by `VoiceChannelCapacityTest`.
- **Per-member mute is viewer-only.** `VoiceMutes` is a local id set on `VoiceChatModule`
  (`toggleMute`/`isMuted`/`mutes()`); muting a peer drops their AUDIO frames from the mix
  (`onDatagram` returns before mixing) and is **never transmitted**, so the peer is never told. The
  beacon already carries their self-mute, so no extra signalling is needed for that half. Pinned by
  `VoiceMutesTest`.
- **The module's config dialog is trimmed to mic-only scope.** `InbuiltModuleProvider`'s voice
  schema is now just the mic master switch plus the in-world icon's look and behaviour. Channel,
  visibility, capacity and per-member mute were removed from the dialog because the Voice tab owns
  them -- two places to set the same value is how they drift. The icon keys are
  `CFG_VOICE_ICON_STYLE`/`CFG_VOICE_ICON_ANIMATE`/`CFG_VOICE_ICON_NAMETAG`.
- **`VoiceUiKit` is the shared premium treatment** for both the in-game `VoicePanel` and
  `VoiceChatActivity`, so the two views of the same feature cannot look like different apps.
  `MicIconStyle` (classic/smooth) and `NametagMicState` (muted/speaking/idle) are the icon's
  pure state, kept Android-free for the JVM tests.
- **The in-world nametag icon is a seam, exactly like the Hitboxes module's boxes.**
  `VoiceNametagMod.TagSource` supplies where each audible player's nametag is; it has **no
  provider** (`setTagSource` has zero callers) because world/nametag positions live in
  `libminecraftpe.so`, so `readTags()` returns empty and nothing is drawn -- never an icon at a
  guessed position. The level and mute state are *not* seams: they come from `VoiceChatModule`
  (with `findPeer(id)` looking across audible channels, not just the local one). `NametagIconProjector`
  is pure and shares `HitboxProjector.Camera` on purpose -- a second camera convention is how one
  drifts. Icons sit right of the label, project far-to-near, and drop when the label is too small
  to read. Pinned by `NametagIconProjectorTest`/`NametagMicStateTest`/`VoiceNametagModTest`.

## Voice relay server (server/voice-relay, Go) — internet voice
- A standalone Go UDP relay so voice works between players **not on the same Wi-Fi**. LAN multicast
  stays the default path (`VoiceTransport`); the relay is a second `VoiceLink`
  (`VoiceRelayTransport`) the user opts into with a server address. Nothing in the launcher's
  `VoiceChatModule` knows which one it has beyond the `VoiceLink` interface.
- **`VoiceLink` is the transport seam.** `prefersOpus()` decides the codec: the LAN path returns
  false (raw PCM, no codec dependency on a shared network), the relay returns true. `VoiceCodec`
  wraps Concentus (pure-Java Opus, BSD-3) through **reflection**, so a missing jar degrades to PCM
  rather than making the voice package un-loadable. `VoiceCodecTest` asserts Concentus is on the
  test classpath — otherwise `isAvailable()` would be false and every relay session would silently
  fall back to raw PCM, which is the whole bandwidth saving lost with no error.
- **`VoiceJitterBuffer` is per peer, and its drain semantics are a priming problem, not a
  per-call threshold.** It releases only after the buffer holds the adaptive target depth *or* the
  head has waited past `MAX_WAIT_MS`; re-requiring the depth on every `poll` stalls the stream the
  moment it dips below the cushion. It also anchors `nextSequence` at the **lowest buffered
  sequence**, not the first arrival — an early frame that overtook the one that arrived first is
  not late. `offer` only rejects `position < nextSequence` once actually primed. Sequence
  wraparound and non-zero start values are handled by `extend` (reconstructs the full 64-bit
  position from the 32-bit wire value relative to the last one).
- **`VoiceMixer` sums sources sample-by-sample and saturates.** Each source is applied at its own
  offset (so a talker that starts mid-frame does not shift the others) and a zero/short buffer
  contributes silence rather than dragging the frame to zero.
- **Wire format is pinned cross-language by golden vectors.** `VoiceProtocol` v4 adds the
  server-assigned `clientId` (8 bytes after the type) and the codec byte (just before the payload
  length), and carries **three** length-prefixed strings before the visibility byte: `peerId`,
  `name`, `channel`. The Go parser must consume all three or every field after is read from the
  wrong offset — a mistake that only surfaces when a real client meets a real server.
  `VoiceProtocolGoldenVectorTest` (Java) fixes the exact bytes; `server/voice-relay/vector_test.go`
  asserts the same literals parse to the same fields. Change the layout and one suite fails instead
  of the mismatch shipping. v1/v2/v3 remain decodable (`VERSION_LEGACY..VERSION`); an unknown codec
  byte reads as PCM.
- **The relay never decodes audio.** It carries opaque payloads and the codec byte through, patches
  the sender's assigned id into one copy, and fans it out — so a 1 vCPU / 1 GB VPS is enough.
  `CanHear` mirrors `VoiceChannel.canHear` exactly (same channel, or the open `world` channel in
  both directions) so a team-channel switch behaves the same over the relay as on the LAN.
- **Keepalive keeps phone NAT mappings open.** The client pings every `HeartbeatMs` (server tells
  it the interval in `HELLO_ACK`); the server answers PONG and evicts a session idle past
  `IdleTimeoutSec`. `RelayReconnectPolicy` backs off reconnects; a phone that re-HELLOs from the
  same socket replaces its session rather than accumulating one.
- **Abuse controls are per client and per IP**: token-bucket packet and byte rate limits, a
  per-channel member cap (a refused move is a no-op + NOTICE, not a disconnect), `max_channels`,
  `max_clients`, `max_clients_per_ip`, and an optional shared password. `/healthz`, `/metrics`
  (Prometheus) and `/status` are the monitoring surface. See `server/voice-relay/README.md` for
  deploy (systemd unit in `deploy/`), firewall and NAT notes.
- **Token auth replaces the replayable password.** Set `TokenSecret` (`-token-secret` /
  `VOICE_TOKEN_SECRET`) and the relay requires a signed join token instead: the launcher holds the
  secret, never sends it, and mints `VoiceToken.issue(secret, deviceId, ttl, now)` per HELLO —
  re-minted on every reconnect, so an expiry mid-session cannot lock a client out. The token is
  `v1.<base64url(deviceID|expiryUnix)>.<base64url(hmacSHA256(secret,"v1|"+payload))>`; the HMAC
  covers the version prefix, the base64 is **URL-safe without padding**, and `MAX_TTL_SECONDS`
  caps a hand-minted far-future expiry. A device id is sanitized on both sides (control bytes and
  `|` stripped, 64 chars max) because it becomes part of a signed payload. **The format is pinned
  across languages**: `VoiceTokenTest.GOLDEN` (Java) and
  `token_test.go:TestTokenGoldenVectorMatchesTheLauncher` (Go) assert the same literal — a drift
  here is not a compile error but a relay that refuses every connection.
- **Bans: auto, static and live.** Repeated bad credentials auto-ban an IP for `BanMinutes`
  (`AuthFailuresBeforeBan`, 0 disables); `BannedIPs` (IP or CIDR) and `BannedDevices` are permanent
  and the device ban survives an IP change. The admin endpoint (`AdminListen`, localhost/private
  only, `AdminToken` bearer) exposes `GET /admin/bans`, `POST /admin/ban` and `POST /admin/unban`
  (`{"ip"|"device", "minutes"}`); a ban takes effect immediately and `DisconnectBanned` drops any
  matching live session. The device id rides the v4 wire as its own field (`Packet.DeviceID`, after
  `name`), and every relayed frame is stamped with it so a ban can be bound to a device.
- **The device id is generated on first use, never a hardware id.** `InbuiltModManager
  .getVoiceDeviceId()` returns a random `UUID` kept in prefs — enough to bind a token and a ban to
  an install without being a cross-app tracking identifier. Clearing the pref is the intended
  "start over" escape hatch.
- **A default relay address can be baked in at build time.** `local.properties` →
  `voice.relay.address` → `BuildConfig.DEFAULT_VOICE_RELAY_ADDRESS` (empty by default). The pref
  wins when set; `getVoiceRelayAddress()` falls back to the build value so a release ships a
  working server with no first-run typing. The value is not a package path and holds no secret.
- **Honest scope:** the password is a plain shared secret over UDP, not authenticated identity, and
  audio is **not end-to-end encrypted** — the server carries it. `voice_relay_scope_note` says so in
  the Voice tab. The relay only swaps transports; distance falloff applies on both paths via the
  installed `PositionSource` (see the proximity-voice section — a missing live read falls back to
  channel mode).

## In-game pack changer (core.content.InGamePackChanger + PackChangerPanel)
- Per-instance opt-in toggle in Instance Settings (`GameVersion.inGamePackChangerEnabled`,
  persisted in `VersionProfileMetadata`). When on, the in-game Mod Menu's **Packs** section lists
  the instance's resource packs with a per-pack pill toggle.
- It rewrites `minecraftpe/global_resource_packs.json`, the file the running game reads, and
  writes to **every candidate game-data root** (`LauncherStorage.getCandidateGameDataDirs`) -- the
  same defence the cape installer uses, because the game picks its storage from isolation and
  internal/external, and a write to only the wrong guess is silently ignored.
- **The pack-list `version` must be a three-number array (`[1,0,0]`), never a string.** The game
  matches the entry against the manifest's own array version, so `"version": "1.0.0"` is not
  comparable and the pack is dropped silently -- it applies to the in-game changer, skin packs and
  cape packs alike because they all share this list. Every writer (`InGamePackChanger`,
  `SkinPackActivator`) routes through a `versionArray` helper, and existing string entries are
  normalised on the next rewrite. `InGamePackChangerTest.theVersionIsWrittenAsAnArrayNotAString`
  and `CapeInGameInstallerTest.theGlobalEntryVersionIsAnArraySoTheGameMatchesThePack` pin it.
- **A live in-place reload is not possible without a native hook.** The game caches its pack stack
  when the world loads, and `PreloaderInput.nativeReloadResourcePacks()` is only a *seam* -- the
  preloader submodule exports no implementation, so it returns false. The honest behaviour is
  therefore: write both the global list and the running world's own `world_resource_packs.json`
  (which the running world does read), then report `pack_changer_reload_on_next_load` rather than
  claiming the change is live. `/reload all` exists in-game but the launcher has no supported way
  to drive it, so do not promise an in-place reload.
- The `InGamePackChanger.Reloader` is installed from `InbuiltOverlayManager.showEnabledOverlays()`,
  **not** as a side effect of starting voice chat (which is how it was first wired, leaving the
  pack changer with no reloader whenever the voice module happened to be off).


## Mod Menu focus highlight (the "50% white overlay")
- **The white wash is the platform's default focus highlight.** A clickable View is implicitly
  focusable, so the d-pad/analogue stick moved focus onto the overlay root and the framework
  painted a full-screen translucent white highlight. Fix: `overlay_mod_menu.xml` root is
  `focusable="false"` and `defaultFocusHighlightEnabled="false"`, and `ModMenuOverlay.
  disableFocusHighlight` sets `setFocusable(false)` on the root plus clears the highlight flag
  recursively (buttons stay focusable for controller nav). Only the highlight flag is touched,
  never a background.
- **Clearing the flag is not enough on its own — the held focus must also be released.**
  `disableFocusHighlight` also calls `clearFocus()`. A highlight can be painted for the view that
  already holds focus, and a stick nudge can hand focus to the root *after* the helper first ran;
  clearing the flag does not retroactively remove the highlight from the currently-focused view,
  so without `clearFocus()` the wash still appears mid-gesture for some builds. All three parts
  are needed: unfocusable root, cleared flag, released focus.

## Mod Menu keybinds (keyboard + controller) and the in-game capture
- The Mod Menu open bind is two independent prefs in `InbuiltModManager`:
  `getModMenuKeybind()`/`setModMenuKeybind` (keyboard/mouse, captured via a dialog `OnKeyListener`)
  and `getModMenuControllerBind()`/`setModMenuControllerBind` (controller). `matchesModMenuBind(
  menuBind, controllerBind, keyCode, rawKeyCode)` is the **pure** match: an unbound (0) side never
  matches, the sides do not cross-match, and a key matches on either its remapped or its **raw**
  code so a profile that remaps the bound button still opens the menu. `ModMenuBindMatchTest` pins
  all of it.
- **The in-game picker must see the press before the preloader can consume it.** The preloader
  dispatches keys first, so a captured button the preloader claims would never reach the picker.
  `ModMenuOverlay.deliverBindKey(keyCode)` is a static sink the picker registers into
  (`sBindCapture`), and `MinecraftActivity.dispatchKeyEvent` offers every raw ACTION_DOWN there
  before anything else may swallow it. The dialog's own key listener still covers launcher-side
  receivers. Register on open, clear on dismiss.
- The controller bind row shows a **2D map of the connected pad with the bound control lit green**
  (`refreshControllerBindIllustration` -> `ControllerIllustrationView.setRegionConfirmed`). A bare
  key name ("BUTTON L1") does not say which physical button it is. The illustration is hidden
  while no bind exists.

## Per-button CPS limit and hold-to-repeat (`CpsLimiter`, `ControllerProfile`)
- Two separate behaviours, deliberately not merged: **limit** drops presses above a per-button CPS
  cap (safe; stops worn-button double-fire), **repeat** injects down/up at a fixed rate while held
  (opt-in; on the attack button it is an autoclicker, so it is off by default, warns once, and is
  labelled "may be against server rules"). `CpsLimiter.isAutoclickerLike(keyCode)` decides the
  warning only -- the launcher cannot know a server's policy.
- `CpsLimiter` is **pure**: it takes `nowMs` as a parameter, never reads a clock, so the rate rules
  are unit-testable (`CpsLimiterTest`). The rolling one-second window counts from the **window
  start** (not the last press) so a burst cannot exceed the cap; the window sentinel is **-1**, not
  0, because event times legitimately start near zero at boot.
- Limits/rates live in `ControllerProfile` (sparse maps, absent = off) so they travel with the
  profile; `copy()` carries them. The **run state** (window, repeat schedule) lives in
  `ControllerInputProcessor.cpsLimiter`, never persisted.
- Wiring: `ControllerInputProcessor.onKeyDown` (called from `MinecraftActivity.dispatchKeyEvent`,
  `repeatCount == 0` only so a held hardware key does not re-arm) applies the cap and arms repeat;
  `onKeyUp` ends it. `tickCpsRepeats(SystemClock.uptimeMillis())` runs from
  `InbuiltOverlayManager.tick()` (the game's own frame tick), so repeats inject at a steady rate.
  A late tick fires once and re-aligns rather than bursting the backlog. Injected events go through
  `PreloaderInput.onKeyEvent` -- the same path a real press uses.
- `setActiveProfile` calls `resetCpsState()` so a profile change cannot carry a partial window over.

## Controller illustration: triggers and shoulders
- **`ControllerLayout` triggers were raised** (`lt`/`rt` y ~ 0.096 Xbox, 0.112 PlayStation) and
  `Spec.halfHeight()` now returns the trigger's real extent (`r * 0.95f`, not the circle default).
  At the old height the lever sat entirely behind the body and only an invisible sliver poked out --
  the "back triggers don't show" report.
- `drawTrigger` is a knee-raised lever (sides rise via knee segments to a lifted crown) with a lit
  crown band and a **recessed piston face**, so a front-on trigger reads as a moving part rather
  than a flat pill. It is intentionally not clipped to the shell; the crown is meant to poke above
  the body edge. `ControllerLayoutTest.triggersStayWithinReachOfTheShell` bounds it.

## Servers / content storage (the "add server always fails" bug)
- **`ContentManager.setStorageDirectories` is what points `ServerManager` at a game-data root.**
  `ContentListActivity.onCreate` now calls its own `updateStorageDirectories()` before `loadContent()`,
  because the screen can be the first in the process (deep link / restore / opened from another app)
  and then nobody had wired `ContentManager`, leaving `ServerManager.minecraftPeDir` null -- every
  add/delete server failed while worlds and packs happened to work. The resolution matches the worlds
  and packs path (`getGameDataDirForType`), so servers cannot drift onto a different root.
- **`QuickLaunchActivity` writes `external_servers.txt` directly** (`ServerManager.writeServerToFile`)
  rather than sending the game a `minecraft://` deep link. When a session was already running the
  URI was forwarded as an extra the game never read, so "Add server" failed with nothing to show.
  Format is the one the game reads: `id:name:ip:port:timestamp`, id one past the current maximum;
  an existing name+ip+port is refused so the row cannot duplicate.

## Blue firework touch layer (FireworkTouchLayer + BaseActivity)
- One shared `FireworkTouchLayer` is attached by `BaseActivity.wrapWithNavBar` as a sibling drawn
  over the whole screen (a `FrameLayout` holds the nav wrapper and the layer). It is only attached
  where the nav bar is attached, so it never appears over Minecraft. `onTouchEvent` always returns
  false — it observes the gesture and never consumes it, so a tap still reaches the screen below.
- The effect is **pooled and single-`onDraw`**: a fixed `Spark[256]` array, no per-particle Views,
  no allocation during a burst, additive blending (`BlendMode.PLUS` on Q+), capped at
  `MAX_BURSTS = 6`, and the driver `ValueAnimator` cancels itself when nothing is alive so an idle
  screen burns no frames. Intensity Low/Med/High sets the spark count (14/17/20).
- It auto-disables under Battery Saver (`PowerManager.isPowerSaveMode`), Android's "remove
  animations" (`ANIMATOR_DURATION_SCALE == 0`), and the personalization `isShowAnimations` gate.
  `refresh(context)` re-reads all of them; `BaseActivity.onResume` calls it so a toggle applied
  while backgrounded takes effect. Settings changes call `refreshFireworkLayer()` (a walk for the
  layer in the view tree) so no recreate is needed.
- Prefs live in `PersonalizationManager`: `isFireworkTouchEnabled` (default **off** — a flourish,
  not chrome), `getFireworkIntensity`, `isFireworkFollowAccent` (off = blue palette). `resetAllCustomizations`
  clears all three.

## Cosmetics status diagnostic (CosmeticsDiagnostics + CosmeticsStatusActivity)
- Capes ship as a texture-override resource pack (`CapeResourcePackBuilder`), whose own note says
  the worst case is a *silent* invisible cape — so `CosmeticsDiagnostics` turns the silence into
  named checks. Reached from the Skins screen ("Cosmetics status" button beside the cape note).
- `run(gameDataDirs, stagingRoot, gameVersion)` is `File`-based and Android-light so it is
  unit-testable (`CosmeticsDiagnosticsTest`): it checks the pack is written under a candidate root,
  that its uuid is listed in `minecraftpe/global_resource_packs.json`, and that the manifest's
  `min_engine_version` is <= the installed version. The two links only the game can answer —
  whether a cape is equipped, and whether RenderDragon honours the override — are reported as
  `MANUAL`, never invented. A missing `min_engine_version` reads as MANUAL, not FAIL.
- The activity offers a **magenta test cape** so "did anything appear" is unambiguous, and a
  copy-report button. Do not promise cosmetics in release notes until a device check confirms
  which link fails.
- `parseVersion` compares component-wise with missing components as zero, so `1.20` == `1.20.0.0`
  and `26.51` > `1.20.0`.

## 3D controller illustrations (Controller3DProjection + Controller3DView)
- The flat `ControllerIllustrationView` read as a squashed top-down sketch and hid the shoulder
  triggers behind the shell. `Controller3DView` is the replacement, used by the controller settings
  screen, the mod-menu bind picker and the bind summary badge.
- **The view owns no geometry.** `Controller3DProjection` (Android-free, in the controller package)
  projects the same `ControllerLayout.regionTable` the 2D view uses through a small camera pitch and
  a perspective divide, returning plain `x,y,radius,z`; the view only paints. That keeps the
  projection testable — `Controller3DProjectionTest` pins the recede, the perspective sign, the
  shoulder lift and the far-to-near sort, because a projection defect looks plausible on screen.
- **Shoulder controls are lifted toward the camera (`SHOULDER_Z = -0.55f`), not pushed away.**
  Pushing them "behind" the body under this pitch would draw them smaller and further out; the
  lift toward the camera (so the tilt cannot bury them) plus the body painting over their lower
  edge is what makes bumpers/triggers read as attached to the top. They draw in a shoulder pass
  **before** the body.
- `Controller3DView.animateConfirm(id)` is the green "got it": a `ValueAnimator` on two floats with
  a single overshoot, invalidating only. Short and allocation-free per frame, so the bind dialog
  never touches the game's frame budget.
- Package split to remember: the view is `org.chimeramc.client.launcher.ui.views.Controller3DView`
  while `ControllerLayout` is `org.chimeramc.client.ui.views`; XML refs must use the `launcher`
  path or inflation fails.


## FPS optimizer (org.chimeramc.client.core.minecraft.FpsOptimizer + FpsOptimizationService)
- Settings -> Basic "FPS Optimization" toggle (`FeatureSettings.isFpsOptimizerEnabled`). It is a
  **thin coordinator over existing signals**, exactly like PerformancePresetManager: it never
  touches Minecraft's own graphics or framerate, and the class docs / string say so. What it
  does is keep the *launcher* quiet while a session runs (serve cached news/update data rather
  than polling - the same path `LowLatencyNetworkManager.isGameSessionActive()` already gates)
  and shed speculative work under thermal pressure (`ThermalGovernor.severity()`), battery saver,
  or critical memory.
- `FpsOptimizer.decide(...)` is pure and unit-tested (`FpsOptimizerTest`); it returns
  NONE / QUIET_HOST / SHED_BACKGROUND. `describe()` must never contain a digit followed by "fps"
  - the launcher cannot measure the game's frame rate, and a number there would be the same
  dishonesty as promising a ping. The test pins that.
- Re-evaluated from the existing session lifecycle, not a second timer: `PlaytimeManager
  .startSession` (apply + clear low-memory) and `heartbeat()` (15s, picks up thermal/battery
  changes). `Application.onTrimMemory` feeds `setLowMemory`. `Application.onCreate` calls
  `FpsOptimizationService.init`.
- The switch re-applies immediately (`apply()`), so toggling it mid-session works without a
  restart; `syncPerformanceDependentSwitches` also re-applies after a preset change.

## Pure-rule extraction for JVM tests (VersionCodeNormalizer)
- A rule that lives inside a class whose **static initializer loads a native library** cannot be
  unit-tested: initialising `LibBindings` in a plain JVM test throws `ExceptionInInitializerError`
  (its `System.loadLibrary` + `android.util.Log` fail off-device). The fix is to extract the pure
  part (here version parsing) into a standalone class - `VersionCodeNormalizer` - and have the JNI
  class delegate to it. `VersionCodeNormalizerTest` then covers the rule with no device and no
  mocks. Apply the same shape to any other pure rule otherwise trapped behind a native-loading
  class.

## Text input path (Enter-in-chat crash)
- A committed control code point (Enter arrives as `'\n'` from several IMEs) must not be replayed
  as a synthetic character key - that is what crashed the instance. `TextInputSanitizer`
  (`sanitizeUnicodeChar` for the hardware path, `isKeyFallbackCodePoint` for the synthetic-key
  fallback) encodes the rule; the preloader's `nativeOnTextInput` skips non-printable code points
  too, and the dedicated key code still delivers the Enter press. The preloader also wraps every
  registered input callback in a try/catch so a throwing mod reads as "not consumed" instead of
  unwinding through the game's dispatch and killing the session.
