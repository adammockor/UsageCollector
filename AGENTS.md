# Repository Guidelines

## Project Structure & Module Organization
- `app/` is the single Android application module.
- Source: `app/src/main/java/com/adammockor/usagecollector/` (core logic in `core/`, UI in `ui/`).
- Resources: `app/src/main/res/` (Compose themes in `values/`, app icons in `mipmap-*/`).
- Tests: unit tests in `app/src/test/`, instrumented tests in `app/src/androidTest/`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew installDebug` installs the debug build on a connected device/emulator.
- `./gradlew testDebugUnitTest` runs JVM unit tests under `app/src/test/`.
- `./gradlew connectedDebugAndroidTest` runs instrumented tests on a device.
- `./gradlew lint` runs Android Lint (useful before releases).

## Coding Style & Naming Conventions
- Kotlin + Jetpack Compose; use 4-space indentation and default Android Studio formatting.
- Classes/objects: `UpperCamelCase` (e.g., `UsageSessionProcessor`).
- Functions/vars: `lowerCamelCase` (e.g., `collectUsageEvents`).
- Test files end with `*Test.kt` and live next to their package path.
- No formatter/linter is configured; keep changes focused and consistent with nearby code.

## Testing Guidelines
- JUnit is used for unit tests (see `app/src/test/...`).
- Instrumented tests use AndroidX test runners (see `app/src/androidTest/...`).
- Prefer fast unit tests for core logic, and use device tests for Android framework interactions.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative, and sentence case (e.g., “Refactor the …”).
- PRs should include: a brief summary, test commands run, and screenshots for UI changes.
- Link relevant issues or context when behavior changes or data export formats shift.

## Configuration Tips
- Ensure Android SDK/Studio is installed; `local.properties` should point to the SDK path.
- WorkManager and usage access flows depend on device permissions; document any new permissions.
