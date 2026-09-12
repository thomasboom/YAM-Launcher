# AGENTS.md - YAM Launcher

Guidelines for agentic coding agents operating in this repository.

## Project Overview

YAM Launcher is a minimalist text-based Android launcher with weather integration, built with Kotlin and Jetpack Compose.

- **Package**: `eu.ottop.yamlauncher`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 37
- **Language**: Kotlin 2.4.0
- **JVM Target**: Java 17
- **UI**: Jetpack Compose (BOM) + Material3, Compose compiler plugin, `buildFeatures.compose = true`

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug on connected device
./gradlew installDebug

# Clean build
./gradlew clean

# Build all variants
./gradlew build
```

## Lint Commands

```bash
# Run lint on default variant
./gradlew lint

# Run lint on debug build
./gradlew lintDebug

# Run lint on release build
./gradlew lintRelease

# Run lint with auto-fix for safe suggestions
./gradlew lintFix
```

## Test Commands

```bash
# Run unit tests for all variants
./gradlew test

# Run unit tests for debug build
./gradlew testDebugUnitTest

# Run instrumentation tests (requires connected device)
./gradlew connectedAndroidTest

# Run instrumentation tests for debug build
./gradlew connectedDebugAndroidTest
```

Note: The project has unit tests. Tests should be placed in:
- Unit tests: `app/src/test/java/eu/ottop/yamlauncher/`
- Instrumentation tests: `app/src/androidTest/java/eu/ottop/yamlauncher/`

## Code Style Guidelines

### Import Order

Organize imports in this order:
1. Android framework imports (`android.*`)
2. AndroidX imports (`androidx.*`)
3. Third-party libraries (`com.google.*`, `kotlinx.*`, `java.*`)
4. Project-specific imports (`eu.ottop.yamlauncher.*`)

Within each group, organize alphabetically.

### Naming Conventions

- **Classes**: PascalCase (e.g., `MainActivity`, `SharedPreferenceManager`)
- **Variables/Methods**: camelCase (e.g., `weatherSystem`, `getInstalledApps`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_LOG_FILE_SIZE`, `LOG_FILE_NAME`)
- **String resources**: snake_case (e.g., `settings_title`, `launch_error`)
- **Activities**: Suffix with `Activity` (e.g., `SettingsActivity`)
- **Composables**: PascalCase screen/section names (e.g., `HomeScreen`, `AppDrawer`, `SettingsNav`)
- **ViewModels**: Suffix with `ViewModel` (e.g., `LauncherViewModel`, `SettingsViewModel`)
- **Utility classes**: Suffix with `Utils` (e.g., `AppUtils`)
- **Managers**: Suffix with `Manager` (e.g., `SharedPreferenceManager`)
- **Listeners/Receivers**: Suffix with `Listener` or `Receiver` (e.g., `NotificationListener`, `BatteryReceiver`)

### Kotlin Style

- Use `lateinit` for properties initialized in `onCreate` or similar lifecycle methods
- Use `private` modifier for class members by default
- Use `val` over `var` when possible
- Prefer string templates over concatenation: `"Text $variable"` instead of `"Text " + variable`
- Use scope functions (`apply`, `let`, `also`, `run`, `with`) appropriately
- Use coroutines for async operations with appropriate dispatchers:
  - `Dispatchers.Main` for UI operations
  - `Dispatchers.Default` for CPU-intensive work
  - `Dispatchers.IO` for I/O operations

### Error Handling

- Use try-catch blocks for operations that may fail
- Use underscore (`_`) for unused exception variables: `catch (_: Exception)`
- Use the centralized `Logger` utility for logging errors
- Provide graceful fallbacks for failed operations

Example:
```kotlin
try {
    launcherApps.startMainActivity(componentName, userHandle, null, null)
    logger.i("Tag", "Launched app: ${componentName.packageName}")
} catch (e: Exception) {
    logger.e("Tag", "Failed to launch app", e)
    throw e
}
```

### Logging

Use the centralized `Logger` singleton:
```kotlin
private val logger = Logger.getInstance(context)

logger.d("Tag", "Debug message")
logger.i("Tag", "Info message")
logger.w("Tag", "Warning message")
logger.e("Tag", "Error message", throwable)
```

### Compose UI

All UI is Jetpack Compose. State lives in ViewModels as `StateFlow`, collected with
`collectAsState()` / `collectAsStateWithLifecycle()`:

```kotlin
@Composable
fun HomeScreen(vm: LauncherViewModel, onOpenSettings: () -> Unit) {
    val p by vm.uiPrefs.collectAsState()
    Text(text = timeText, style = launcherTextStyle(p, p.clockSizeSp.sp))
}
```

- Launcher UI: `compose/` (`LauncherViewModel`, `YamTheme`, `LauncherRoot`, `HomeScreen`, `AppDrawer`)
- Settings UI: `compose/settings/` (`SettingsViewModel`, `SettingsNav`, `SettingsScreens`, `SettingsComponents`)
- Theme helpers (`launcherTextStyle`, `fontFamilyFor`, size tables) live in `compose/YamTheme.kt`
- Window-level styling (background, status bar) stays in `UIUtils`; everything else View-related was removed
- `MainActivity` extends `FragmentActivity` (required by `BiometricUtils`); `SettingsActivity` is a plain `ComponentActivity`

### SharedPreferences

Use `SharedPreferenceManager` for all preference access:
```kotlin
private val sharedPreferenceManager = SharedPreferenceManager(context)

// Getting values
val isClockEnabled = sharedPreferenceManager.isClockEnabled()
val bgColor = sharedPreferenceManager.getBgColor()

// Setting values (use kotlin extension)
preferences.edit {
    putString("key", "value")
}
```

### LazyColumn Lists

App/contact/settings lists are Compose `LazyColumn` with stable keys — no adapters, no DiffUtil:
```kotlin
LazyColumn(state = listState) {
    items(filtered, key = { it.componentString + "#" + it.profile }) { entry ->
        AppRow(vm, entry)
    }
}
```

### Settings Navigation

Settings use a Compose `NavHost` (`SettingsNav`), not fragments:
- Routes: `root`, `ui`, `home`, `appmenu`, `context`, `hidden`, `gesture/{direction}`, `location`, `about`
- Preference rows: `SwitchRow`, `ListRow`, `NavRow`, `EditRow`, `ActionRow` in `SettingsComponents.kt`
- Backups stay JSON (`app_id`, `schema_version = 2`) via `ActivityResultContracts.CreateDocument/OpenDocument`
```

### Dependency Management

Dependencies are managed via version catalog in `gradle/libs.versions.toml`:
```kotlin
// In build.gradle.kts
implementation(libs.core.ktx)
implementation(libs.appcompat)

// In libs.versions.toml
[versions]
core-ktx = "1.16.0"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
```

### Build Variants

- **Debug**: `.dev` applicationId suffix, `-dev` versionName suffix
- **Release**: Minified with ProGuard, shrinkResources enabled

### Resources

- String resources in `res/values/strings.xml`
- Drawables (`R.drawable.*`) loaded via `painterResource` in Compose
- Settings entry labels/values still in `res/values/arrays.xml` + `arrays_common.xml`
- Support RTL layouts automatically via Compose `Alignment`/`TextAlign` mappings

### Biometric Authentication

Use `BiometricUtils` for authentication flows:
```kotlin
biometricUtils.startBiometricSettingsAuth(object : BiometricUtils.CallbackSettings {
    override fun onAuthenticationSucceeded() {
        // Handle success
    }
    override fun onAuthenticationFailed() {
        // Handle failure
    }
    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
        // Handle error
    }
})
```

## Key Architecture Patterns

1. **Singleton**: `Logger` uses companion object with double-checked locking
2. **Utility Classes**: Stateless utility classes with Context passed to constructor
3. **Manager Pattern**: `SharedPreferenceManager` encapsulates all preference logic
4. **Compose Navigation**: Settings use `NavHost`; launcher home/drawer switch via `AnimatedVisibility` + `LauncherViewModel.drawerOpen`
5. **Coroutines + Lifecycle**: Uses `lifecycleScope` and `repeatOnLifecycle` for lifecycle-aware coroutines
