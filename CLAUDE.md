# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NutriCart is a native Android app ("Smart grocery, healthier you"), built with Kotlin and Jetpack Compose. The entry flow (splash, onboarding, login, create account) and local storage are built; the main app screens are not yet.

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

- The app is being built in phases against `project_docs/implementation_plan.md`, which summarises the two requirement PDFs in `project_docs/`. Read the plan before adding screens; the PDFs are the source of truth for UI, dimensions and navigation. Phases 0 to 3 are done (see the `phase_*.md` files in `project_docs/`); Phase 4 (profile setup, Home, bottom nav, Profile, log out) is next.
- `MainActivity` hosts `NutriCartTheme { NutriCartNavHost() }`. Navigation uses Navigation Compose: route strings are in `navigation/Routes.kt` and destinations are registered in `navigation/NutriCartNavHost.kt`. The entry flow is built: splash, welcome (onboarding), login, register. `profile/setup` and `home` are registered as `PlaceholderScreen` only; replace them when the real screens are built.
- `navigation/EntryRouter.kt` holds the routing decisions (onboarding flag, session, stale session, profile). The splash and both auth screens use it; do not duplicate that logic in screens.
- Screens live in `ui/screens/<feature>/` as a `…Screen.kt` (a stateful wrapper plus a private stateless `…Content` with a preview) and a `…ViewModel.kt` exposing one `StateFlow` of UI state. ViewModels are created with `viewModel(factory = AppViewModelFactory)` (`ui/AppViewModelFactory.kt`); add an initializer there for each new ViewModel.
- User-visible text goes in `res/values/strings.xml`. Onboarding panes 2 and 3 are provisional copy awaiting approval. Login and Create account are project requirements that are not in the PDFs.
- Reusable components (C-01 to C-13 from the PDF, plus top bar, logo, progress ring, empty state) are in `ui/components/`; build screens from these rather than raw Material components. `ComponentPreviews.kt` shows them all.
- Design tokens are in `ui/theme/`: read colours from `NutriCartTheme.colors` and text styles from `NutriCartTheme.typography`, and use `Spacing`, `Sizes`, `NutriCartShapes`, `Elevation` and `Motion` from `Dimens.kt`, rather than hard-coding values. Dynamic colour is off.
- The app is local-only: no backend, Firebase or network calls. Data lives in Room (`data/local/`) and a Preferences DataStore (`data/SettingsStore.kt`, holding the onboarding flag and the logged-in account id). Screens should go through the repositories in `data/repository/`, obtained from `(application as NutriCartApp).container` (`AppContainer`, manual DI, no Hilt). Repositories for user-owned data read the account from the session and never take an account id from the caller.
- Passwords are stored only as salted PBKDF2 hashes (`data/auth/PasswordHasher.kt`). Catalog items and prices are demo seed data (`data/local/SeedData.kt`); a price with `updatedAt == null` is a demo price and must not be shown as a real or recently updated one.
- Unit tests in `app/src/test` use Robolectric (pinned to SDK 35 in `src/test/resources/robolectric.properties`) with a real in-memory Room database; see `data/TestEnvironment.kt`. `app/src/androidTest` still holds only the template instrumented test.
