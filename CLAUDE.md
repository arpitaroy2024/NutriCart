# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NutriCart is a native Android app ("Smart grocery, healthier you"), built with Kotlin and Jetpack Compose. The project is in its early stages — currently a splash screen and a login screen UI with no backend or persistence wired up yet.

- Package / namespace: `com.example.nutricart`
- Single Gradle module: `app`
- Min SDK 24, target/compile SDK 37
- Kotlin 2.2.10, AGP 9.3.1, Compose BOM 2026.02.01

## Commands

All commands are run from the repo root using the Gradle wrapper.

```
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on a connected device/emulator
./gradlew test                   # run local JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # run instrumented tests on a device/emulator (app/src/androidTest)
./gradlew test --tests "com.example.nutricart.ExampleUnitTest"   # run a single unit test class
./gradlew lint                   # run Android Lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

- The app is being built in phases against `project_docs/implementation_plan.md`, which summarises the two requirement PDFs in `project_docs/`. Read the plan before adding screens; the PDFs are the source of truth for UI, dimensions and navigation. Phases 0 (foundations), 1 (design system, see `project_docs/phase_1_design_system.md`) and 2 (local storage and auth logic, see `project_docs/phase_2_local_storage.md`) are done.
- `MainActivity` hosts `NutriCartTheme { NutriCartNavHost() }`. Navigation uses Navigation Compose: route strings are in `navigation/Routes.kt` and destinations are registered in `navigation/NutriCartNavHost.kt`. Only `splash` and `login` are registered so far; add a destination when its screen is built.
- Screens live in `ui/screens/<feature>/` (`splash/SplashScreen.kt`, `auth/LoginScreen.kt`). Both are still the original pre-spec versions and get restyled in Phase 3.
- Reusable components (C-01 to C-13 from the PDF, plus top bar, logo, progress ring, empty state) are in `ui/components/`; build screens from these rather than raw Material components. `ComponentPreviews.kt` shows them all.
- Design tokens are in `ui/theme/`: read colours from `NutriCartTheme.colors` and text styles from `NutriCartTheme.typography`, and use `Spacing`, `Sizes`, `NutriCartShapes`, `Elevation` and `Motion` from `Dimens.kt`, rather than hard-coding values. Dynamic colour is off. The old brand colors (`DarkGreen`, `MidGreen`, `LightGreen`) remain in `Color.kt` only for the un-restyled Splash and Login.
- The app is local-only: no backend, Firebase or network calls. Data lives in Room (`data/local/`) and a Preferences DataStore (`data/SettingsStore.kt`, holding the onboarding flag and the logged-in account id). Screens should go through the repositories in `data/repository/`, obtained from `(application as NutriCartApp).container` (`AppContainer`, manual DI, no Hilt). Repositories for user-owned data read the account from the session and never take an account id from the caller.
- Passwords are stored only as salted PBKDF2 hashes (`data/auth/PasswordHasher.kt`). Catalog items and prices are demo seed data (`data/local/SeedData.kt`); a price with `updatedAt == null` is a demo price and must not be shown as a real or recently updated one.
- No ViewModels or UI wiring exist yet — `LoginScreen` still holds `email`/`password` in `remember { mutableStateOf(...) }` and its button's `onClick` is a no-op placeholder (`/* login logic later */`).
- Unit tests in `app/src/test` use Robolectric (pinned to SDK 35 in `src/test/resources/robolectric.properties`) with a real in-memory Room database; see `data/TestEnvironment.kt`.
- Test source sets (`app/src/test`, `app/src/androidTest`) only contain the default Android Studio template tests (`ExampleUnitTest`, `ExampleInstrumentedTest`) and have not been built out for this app's actual functionality.
