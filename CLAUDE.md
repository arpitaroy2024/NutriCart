# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NutriCart is a native Android app ("Smart grocery, healthier you"), built with Kotlin and Jetpack Compose. The entry flow, local storage, profile setup, Home, Profile, the bottom-navigation shell, grocery generation, the generated list and list editing are built; nutrition analysis and alerts are not yet.

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

- The app is being built in phases against `project_docs/implementation_plan.md`, which summarises the two requirement PDFs in `project_docs/`. Read the plan before adding screens; the PDFs are the source of truth for UI, dimensions and navigation. Phases 0 to 6 are done (see the `phase_*.md` files in `project_docs/`); Phase 7 (nutrition analysis) is next, then Phase 8 (allergy and health alerts). Keep to the phase being built. From Phase 4 on the PDFs are the functional reference, not a pixel spec: improve the UI where justified and record each deviation (PDF / suggestion / decision) in the phase document.
- `MainActivity` hosts `NutriCartTheme { NutriCartNavHost() }`. Navigation uses Navigation Compose: route strings are in `navigation/Routes.kt` and destinations are registered in `navigation/NutriCartNavHost.kt`. Built destinations: splash, welcome (onboarding), login, register, profile/setup (also `?mode=edit`), home, generate, list (the List tab, latest list), list/{listId} (pushed after generation), list/{listId}/edit, list/{listId}/add and profile. `nutrition` is registered as `UpcomingScreen` ("Coming soon") only; replace it when the real screen is built. The bottom bar is drawn by a `Scaffold` around the `NavHost` and shown only on the four tab roots (`navigation/ShellTabs.kt`); tab screens therefore apply `statusBarsPadding()` only, while full-screen destinations handle all system bars themselves.
- `navigation/EntryRouter.kt` holds the routing decisions (onboarding flag, session, stale session, profile). The splash and both auth screens use it; do not duplicate that logic in screens.
- Screens live in `ui/screens/<feature>/` as a `…Screen.kt` (a stateful wrapper plus a private stateless `…Content` with a preview) and a `…ViewModel.kt` exposing one `StateFlow` of UI state. ViewModels are created with `viewModel(factory = AppViewModelFactory)` (`ui/AppViewModelFactory.kt`); add an initializer there for each new ViewModel.
- User-visible text goes in `res/values/strings.xml`. Onboarding panes 2 and 3 are provisional copy awaiting approval. Login and Create account are project requirements that are not in the PDFs.
- Reusable components (C-01 to C-13 from the PDF, plus top bar, logo, progress ring, empty state) are in `ui/components/`; build screens from these rather than raw Material components. `ComponentPreviews.kt` shows them all.
- Design tokens are in `ui/theme/`: read colours from `NutriCartTheme.colors` and text styles from `NutriCartTheme.typography`, and use `Spacing`, `Sizes`, `NutriCartShapes`, `Elevation` and `Motion` from `Dimens.kt`, rather than hard-coding values. Dynamic colour is off.
- The app is local-only: no backend, Firebase or network calls. Data lives in Room (`data/local/`) and a Preferences DataStore (`data/SettingsStore.kt`, holding the onboarding flag and the logged-in account id). Screens should go through the repositories in `data/repository/`, obtained from `(application as NutriCartApp).container` (`AppContainer`, manual DI, no Hilt). Repositories for user-owned data read the account from the session and never take an account id from the caller.
- The Room database is at version 3. Any schema change needs a new version and a `Migration` added to `ALL_MIGRATIONS` in `NutriCartDatabase.kt`, plus cases in `data/local/MigrationTest.kt`; never use a destructive fallback. A profile holds a set of listed allergies, a set of listed health conditions, and a list of custom (typed) values for each, stored as JSON arrays; no conditions of either kind means none. Custom values are cleaned by `ProfileRepository.cleanCustomEntries` (trimmed, no repeats ignoring case, 40 characters, 10 entries); the enum constant names in `data/model/Models.kt` are what is stored, so do not rename them.
- Passwords are stored only as salted PBKDF2 hashes (`data/auth/PasswordHasher.kt`). Catalog items and prices are demo seed data (`data/local/SeedData.kt`); a price with `updatedAt == null` is a demo price and must not be shown as a real or recently updated one.
- `domain/GroceryGenerator.kt` builds the list. It is a pure, deterministic, on-device rule set, not an AI model, and must stay free of Android, database and UI code; there is no network or LLM anywhere in the app. Its basket template is demo data beside the catalog seed. It leaves out items tagged with a listed allergy or condition (`domain/ProfileConflicts.kt`) but that is not a safety check: full conflict detection is Phase 8 and nutrition scoring is Phase 7.
- List edits are saved as they are made (no draft, no Save step) through the single-edit operations on `GroceryListRepository`; screens observe the list as a Flow. The editor and item picker always work on the list id in their route, never "the latest list". A list never holds the same catalog item twice (adding again raises the quantity), quantities stay within `domain/ListRules`, and going over budget is allowed and shown, never blocked or corrected. Use `domain/ListTotals` and `ui/screens/grocerylist/BudgetSummary` for budget figures.
- Unit tests in `app/src/test` use Robolectric (pinned to SDK 35 in `src/test/resources/robolectric.properties`) with a real in-memory Room database; see `data/TestEnvironment.kt`. `ui/AppShellNavigationTest.kt` drives the real nav graph and screens with the Compose test rule; its waits must pump the main looper (see `waitFor` there). `app/src/androidTest` still holds only the template instrumented test.
