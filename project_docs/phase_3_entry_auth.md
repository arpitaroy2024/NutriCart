# NutriCart — Phase 3: Entry Flow & Authentication UI

**Date:** 2026-10-04
**Phase:** Phase 3 — Splash, onboarding, login and create account
**Status:** Implemented; build, 72 unit tests and lint pass. **Not yet seen on a device** (see Verification).

Everything is on-device. No Firebase, backend or network code was added.

## 1. Screens implemented

| ID | Screen | Source | Files |
|---|---|---|---|
| SCR-01 | Splash | PDF | `ui/screens/splash/SplashScreen.kt`, `SplashViewModel.kt` |
| SCR-02 | Welcome / Onboarding | PDF | `ui/screens/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt` |
| AUTH-1 | Login | Project requirement | `ui/screens/auth/LoginScreen.kt`, `LoginViewModel.kt` |
| AUTH-2 | Create account | Project requirement | `ui/screens/auth/CreateAccountScreen.kt`, `CreateAccountViewModel.kt` |
| — | Placeholder for Profile setup and Home | Stand-in only | `ui/screens/placeholder/PlaceholderScreen.kt` |

The placeholder is a temporary testing screen with no Phase 4 functionality; see "Temporary testing placeholder" below.

## 2. Splash routing logic

`EntryRouter.startDestination()` (in `navigation/EntryRouter.kt`) decides, in this order:

| Stored state | Destination |
|---|---|
| Onboarding not completed | Welcome / Onboarding |
| Onboarding completed, no session | Login |
| Session names an account that is not in the database (stale) | Session is cleared, then Login |
| Session, no profile | Profile setup (placeholder) |
| Session and profile | Home (placeholder) |

Splash behaviour, from SCR-01:

- **Timing:** on screen for at least 600ms. The storage read is given at most 3000ms. There is no fixed delay; navigation happens as soon as the decision is ready and the minimum has passed.
- **Failure path:** if storage throws or does not answer within 3000ms, Welcome opens and shows a snackbar ("Couldn't read your saved data.") with a Retry action that runs the splash again. The splash never blocks.
- **Back stack:** the splash is popped on exit and cannot be returned to.
- **Visuals:**
  - Background is `on.surface` (#122019) in both themes; the splash is always dark.
  - Logo is 200dp, scaling from 0.92 to 1.0 over 220ms.
  - "NutriCart AI" in display/32sp Bold, `on.primary`.
  - "Smarter groceries on your budget" in caption/14sp, primary at 70% alpha.
  - An 80 x 4dp indeterminate bar on a 20% track, with "Preparing your plan" below it.
- **Indicator:** shown only if the decision is not ready after 400ms.

## 3. Onboarding behaviour

- Three panes in a `HorizontalPager`, swipeable both ways.
- **Get started** advances to the next pane; on the last pane it finishes onboarding.
- **Skip** finishes onboarding from any pane.
- Finishing (either way) writes `onboardingComplete = true` to DataStore, then opens Login and removes Welcome from the back stack. Onboarding is not shown again, including after a later log out.
- **Back:** on panes 2 and 3 it returns to the previous pane; on pane 1 it exits the app.
- **Layout, from SCR-02:**
  - Skip text button: caption/14sp Bold, `on.surface.muted`, 48dp target.
  - Illustration banner: 216dp tall, 20dp radius, `primary.container`, hidden from accessibility.
  - Heading: headline/26sp Bold, 2 lines.
  - Description: body/16sp with 24sp line height, `on.surface.muted`, 3 lines.
  - Pager dots: 8dp, active 28 x 8dp pill in primary, 10dp gap.
  - 56dp "Get started" button and centred footnote "Takes about a minute".

## 4. Onboarding copy

Pane 1 is taken from the PDF. **Panes 2 and 3 are provisional. The PDFs give no content for them, and this text needs product-owner approval before release.** The strings are in `res/values/strings.xml`, marked `PROVISIONAL`.

| Pane | Status | Heading | Description | Banner chips |
|---|---|---|---|---|
| 1 | From the PDF | Eat well without overspending. | Set a monthly budget and NutriCart builds a grocery list that fits it — checked against your allergies. | Tk · kcal |
| 2 | **Provisional** | Balanced for your household. | NutriCart scores each list against what your household needs and points out the biggest nutrition gap. | g · mg |
| 3 | **Provisional** | Flagged, never hidden. | Items that conflict with your allergies or health condition are flagged with safer swaps. The choice stays yours. | OK · ! |

The provisional text is based on SCR-07 (nutrition score and single largest gap) and SCR-08 / FLOW-14 (conflicts are flagged, not removed). The banner on panes 2 and 3 reuses pane 1's layout with different chip labels; no new illustration was designed.

## 5. Login behaviour

- **Layout:** the original structure is kept (logo, "Welcome Back", "Login to continue", one card holding the fields and the button), restyled with the Phase 1 tokens and components: `surface` background, `AppLogo`, headline title, caption subtitle, `NutriCard`, `SectionLabel` + `NutriTextField` for each field, `NutriFilledButton`. A text link "New here? Create account" sits under the card.
- **Password:** masked, with a password keyboard.
- **Button:** disabled until the email has a valid format and the password is not empty; shows a spinner and locks the fields while logging in.
- **Inline validation:** "Enter a valid email address" appears under the email field once the user leaves it with an invalid value.
- **Failure:** "Email or password is incorrect" above the button, the same for an unknown email and a wrong password. It clears when either field is edited. An unexpected storage error shows "Something went wrong. Please try again."
- **Success:** the Phase 2 repository sets `loggedInAccountId`; the app goes to Profile setup if the account has no profile, otherwise Home.
- **Keyboard:** the screen scrolls and stays clear of the keyboard; Next moves from email to password and Done submits.
- Not added: forgot password, email verification, show-password toggle.

## 6. Create account behaviour

- **Layout:** the same as Login, titled "Create account" with the subtitle "Your account stays on this device". Fields: Name, Email, Password. A link "Already have an account? Login" returns to Login.
- **Password:** masked.
- **Button:** disabled until the name is not blank, the email is valid and the password has at least 6 characters; shows a spinner while working.
- **Inline validation**, shown on a field once the user leaves it:
  - Name: "Enter your name"
  - Email: "Enter a valid email address"
  - Password: "Use at least 6 characters"
- **Duplicate email:** "An account with this email already exists" under the email field. The button stays disabled until the email is changed.
- **Success:** the account is created with a salted hash (never the password), logged in automatically, and the app goes to Profile setup.

## 7. Navigation and back-stack behaviour

```
splash ─┬─> welcome ──Skip / Get started (last pane)──> login
        ├─> login
        ├─> profile/setup   (placeholder)
        └─> home            (placeholder)

login ──"Create account"──> register ──success──> profile/setup
login ──success──┬─> profile/setup
                 └─> home
register ──"Login" link or back──> login
```

| Transition | Back stack |
|---|---|
| Splash → anything | Splash removed |
| Welcome → Login | Welcome removed; back on Login exits the app |
| Login → Create account | Pushed; back returns to Login |
| Login or Create account → Profile setup / Home | Login and Create account both removed; back exits the app, so a signed-in user cannot return to Login |
| Welcome → Splash (Retry) | Welcome removed |

All transitions fade over 200ms (FLOW-01's splash transition). The slides the UI Flow specifies for forward pushes are left for the polish phase.

## 8. Session behaviour

| Scenario | Result | Covered by |
|---|---|---|
| A. Fresh install | Splash → Onboarding → Skip / Get started → Login | `EntryRouterTest`, `EntryViewModelsTest` |
| B. Onboarded, logged out | Splash → Login | `EntryRouterTest`, `EntryViewModelsTest` |
| C. New account | Create account → logged in → Profile setup | `AuthViewModelsTest` |
| D. Existing account, no profile | Login → Profile setup | `AuthViewModelsTest` |
| E. Existing account with profile | Login → Home | `AuthViewModelsTest` |
| F. Restart while logged in | Splash → Home with a profile, Profile setup without | `EntryRouterTest`, `EntryViewModelsTest`; persistence across restarts by `SettingsStoreTest` |
| G. Logout | Not implemented in this phase; no logout UI | — |
| H. Stale session | Splash → session cleared → Login | `EntryRouterTest`, `EntryViewModelsTest` |

These are verified in unit tests against the real repositories. They have not been walked through on a device.

## 9. Validation rules

| Field | Rule | Where |
|---|---|---|
| Name (create account) | Not blank | `AccountValidator` |
| Email (both screens) | Valid format; trimmed and lower-cased before use | `AccountValidator` |
| Email (create account) | Not already registered | `AccountRepository` |
| Password (create account) | At least 6 characters | `AccountValidator` |
| Password (login) | Not empty | `LoginUiState` |

## 10. Tests

72 unit tests in total, all passing; 30 are new in this phase. All Phase 2 tests are kept.

| Test class | Tests | Covers |
|---|---|---|
| `EntryRouterTest` (new) | 9 | Every routing decision: fresh install, onboarding incomplete with a session, logged out, session without profile, session with profile, logged out after having a profile, stale session cleared, post-login destination, route strings |
| `EntryViewModelsTest` (new) | 6 | Onboarding completion is stored; splash stops opening onboarding afterwards; splash results for no profile, profile and stale session; storage failure opens Welcome with a retry |
| `AuthViewModelsTest` (new) | 15 | Login: button state, email error timing, invalid input does nothing, success to Profile setup and to Home, wrong password, unknown email, failure clears on edit. Create account: button state, error timing, invalid input creates nothing, success logs in and stores only a hash, duplicate email, duplicate error clears, new account can log in again |
| Phase 2 classes | 41 | Unchanged |
| `ExampleUnitTest` | 1 | Unchanged |

`TestEnvironment` gained the entry router and a helper that runs the main looper, for ViewModel tests.

## 11. Files created

Main (`app/src/main/java/com/example/nutricart/`):

- `navigation/EntryRouter.kt`
- `ui/AppViewModelFactory.kt`
- `ui/screens/splash/SplashViewModel.kt`
- `ui/screens/onboarding/OnboardingScreen.kt`
- `ui/screens/onboarding/OnboardingViewModel.kt`
- `ui/screens/auth/AuthLayout.kt`
- `ui/screens/auth/LoginViewModel.kt`
- `ui/screens/auth/CreateAccountScreen.kt`
- `ui/screens/auth/CreateAccountViewModel.kt`
- `ui/screens/placeholder/PlaceholderScreen.kt`

Tests (`app/src/test/java/com/example/nutricart/`):

- `navigation/EntryRouterTest.kt`
- `ui/EntryViewModelsTest.kt`
- `ui/AuthViewModelsTest.kt`

Docs:

- `project_docs/phase_3_entry_auth.md`

## 12. Files modified

- `navigation/NutriCartNavHost.kt` — the entry graph, replacing the fixed 2-second splash navigation.
- `navigation/Routes.kt` — `welcome` takes a `storageError` argument; route builders added.
- `ui/screens/splash/SplashScreen.kt` — rebuilt to SCR-01.
- `ui/screens/auth/LoginScreen.kt` — restyled and connected to the repository.
- `AppContainer.kt` — provides the `EntryRouter`.
- `ui/theme/Color.kt` — the three old brand greens removed; nothing uses them now.
- `ui/theme/Type.kt` — Material's typography now maps to the type roles.
- `res/values/strings.xml` — all text for the four screens.
- `app/src/test/.../data/TestEnvironment.kt` — router and looper helper.
- `project_docs/implementation_plan.md`, `CLAUDE.md` — status and notes.

## 13. Dependencies

None added or changed. `viewModel()` and `HorizontalPager` come from libraries already on the classpath through Navigation Compose and Material 3.

## 14. Deviations from the PDFs and the plan

| Item | PDF / plan | Implemented | Why |
|---|---|---|---|
| Onboarding panes 2 and 3 | No content given | Provisional copy (section 4) | Instructed not to wait for approval |
| Get started on the last pane | Opens SCR-03 | Opens Login | Approved flow with Login |
| Skip | SCR-04 (Screen Details) or SCR-03 (UI Flow) | Opens Login | Approved flow |
| Splash decision | Profile exists or not | Onboarding, session, stale session, profile | Approved flow |
| Splash logo | "4dp primary ring"; mockup shows a solid disc | Primary disc with a primary ring | Both honoured; the ring is not visible against the same fill |
| Splash loader caption | "Preparing your plan" drawn, no style given | caption/14sp, primary at 70% | Matches the tagline |
| Inactive pager dot colour | Not given | `on.surface.faint` at 50% | Closest to the mockup |
| Banner glyph | Basket with a leaf | The app logo's cart and leaf on a white disc, 88dp | Reuses `AppLogo`; no separate basket was drawn |
| Banner chips | 2 small circles | 48dp circles | Size not written |
| Forward transitions | Slide from the right, 250ms | Fade, 200ms | Slides are scheduled for the polish phase |
| Storage-failure retry | "Surfaces a retry" | Snackbar with Retry that reruns the splash | The PDF does not say what retry does |
| Field errors | Shown on the field | Shown after the user leaves the field | Avoids an error while the first character is typed |
| ViewModel state | Plan mentions `SavedStateHandle` for Home's budget | Login and Create account keep fields in the ViewModel only | Survives rotation; a password must not be written to saved state |
| Status-bar icons on the splash | — | Follow the system theme | In light mode they are dark on the dark splash for up to 3 seconds |

## 15. Left for Phase 4

- Profile setup (SCR-03) and Home (SCR-04) — placeholders only for now.
- Bottom navigation shell, Profile screen (SCR-09) and Log out.
- Everything after that: generation, list, editing, nutrition, alerts.

## Temporary testing placeholder

Added after on-device testing, because the first placeholder had no way forward or back and looked as if the app had frozen. It is used for both Phase 4 destinations (`profile/setup` and `home`) and must be replaced by the real screens in Phase 4.

It shows:

- A title: "Profile setup is not built yet" (or "Home is not built yet").
- A message: "This is a temporary placeholder. The real screen belongs to Phase 4. You are signed in and the app has not frozen."
- An outlined button: **Back to Login (Testing)**.
- A note under the button: "Testing only. This does not log you out."

What the button does:

- Opens Login and clears the back stack, so back on Login exits the app as usual.
- **It does not log out.** `loggedInAccountId` is left as it is; no authentication or storage code was changed. Logging in or creating an account from there replaces the session in the normal way.
- Because the session is kept, closing and reopening the app returns to the placeholder (Scenario F).

Nothing else about routing or the back stack changed: after a successful login or registration, Login and Create account are still removed from the back stack.

No unit test was added. The button is a single navigation call with no logic of its own, and testing it would need a Compose UI test library that the unit-test setup does not have. The 72 existing tests are unchanged and pass.

Files touched by this change: `ui/screens/placeholder/PlaceholderScreen.kt`, `navigation/NutriCartNavHost.kt`, `res/values/strings.xml`.

## Known limitations

1. **No on-device visual check was done.** The phone was attached but locked, so the screens were not walked through. Splash, onboarding, login and create account need to be looked at on the phone, along with the Phase 1 components they now use for the first time.
2. **Login speed is still unmeasured on a phone.** Hashing uses 300,000 PBKDF2 iterations; the spinner covers the wait, but the duration on a low-end device is unknown.
3. **No log out yet.** The placeholder's "Back to Login (Testing)" button returns to Login but keeps the session, so restarting the app goes back to the placeholder. Clearing the app's data is the only way to get a fresh, logged-out install.
4. **Lint:** one new warning, `PluralsCandidate`, on "Use at least %1$d characters". The number is always 6, so a plural form is not needed.
5. **Android backup** still includes the account database (unchanged from Phase 2).

## Verification

Command (run from the project root):

```
gradlew.bat clean assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 1m 16s`, 58 actionable tasks: 57 executed, 1 up-to-date.
- **Unit tests:** 72 passed, 0 failed, 0 skipped.
- **Lint:** 0 errors, 17 warnings: the 16 from earlier phases plus the `PluralsCandidate` above.
- **Compiler and KSP warnings:** none.
- **Other output:** the "Unable to strip libraries" message (harmless for debug builds) and JDK 26 warnings from Robolectric during tests.
- **Installed:** the debug build was installed on the attached phone; it was not launched or driven.
- **Not run:** the instrumented test.
