# NutriCart — Implementation Plan

Written 2026-10-04. Status: **approved. Phase 0 complete; Phase 1 not started.**

Sources:

- `project_docs/406_NutriCart_Screen_Details.pdf` (18 pages) — screen specifications, design tokens, components.
- `project_docs/406_NutriCart_UI_Flow.pdf` (24 pages) — 21 transitions, routes, back-stack rules.
- Project owner decisions on Login and local accounts (not documented in the PDFs).

The PDFs are the source of truth for the NutriCart UI, screens, dimensions, navigation and functionality. Login and Create account are additional project requirements; everything about them is marked as an addition or an assumption.

---

## 0. Current project state

| Area | Finding |
|---|---|
| Language / build | Kotlin 2.2.10, AGP 9.3.1, Gradle 9.5.0, Java 11, single `app` module |
| SDK | min 24, target/compile 37 |
| UI toolkit | Jetpack Compose only (BOM 2026.02.01, Material 3); no XML layouts |
| Package | `com.example.nutricart`; all app code is in `MainActivity.kt` |
| Navigation | Manual: `AppNavigator` flips a `showSplash` boolean after a fixed 2000 ms, then shows `LoginScreen` |
| Architecture | None: no ViewModel, repository, model, database or networking |
| Existing screens | `SplashScreen`, `LoginScreen` (email/password fields, no validation, no-op button). There is no onboarding screen yet. |
| Theme | `ui/theme/` is the untouched purple Android Studio template with dynamic colour on; brand greens are top-level vals in `MainActivity.kt` |
| Dependencies | core-ktx 1.10.1, lifecycle-runtime-ktx 2.6.1, activity-compose 1.8.0, Compose ui/graphics/tooling/material3, test libraries |
| Resources | Default launcher icons, `app_name` = "NutriCart", template `colors.xml`; no illustrations, icons or fonts |
| Tests | Template `ExampleUnitTest` and `ExampleInstrumentedTest` only |

The build has not been run as part of this analysis.

---

## 1. Design system (from Screen Details, slides 3–5)

### Colour tokens

| Token | Light | Dark | Used for |
|---|---|---|---|
| surface | #F5F8F6 | #0E1A15 | Screen background |
| surface.card | #FFFFFF | #16241E | Cards, list rows, sheets |
| surface.sunken | #E7EEEA | #0A1310 | Progress rails, stepper buttons, input wells |
| on.surface | #122019 | #E9F2EC | Item names, headings, values |
| on.surface.muted | #566B60 | #9CB3A7 | Labels, units, helper text |
| on.surface.faint | #90A398 | #6D857A | Placeholders, unselected nav |
| primary | #0F7A55 | #3BD495 | Filled buttons, checked states, on-target bars |
| on.primary | #FFFFFF | #04231A | Text and icons on primary |
| primary.container | #D3EFE1 | #0E3B2B | Nutrition tags, chips, promo banner |
| on.primary.container | #0A5A3E | #A5E9C7 | Text on primary.container |
| warning | #C2740A | #F0A83C | Deficiency alerts, health-condition conflicts |
| warning.container | #FDF1DC | #33260E | Warning card background |
| danger | #D14343 | #F08585 | Allergy conflicts, delete, log out |
| danger.container | #FCECEC | #331E1E | Allergy card background |
| outline | #E2EAE6 | #24352E | Card hairlines and dividers |

The splash is always dark (`on.surface` background in both themes). Every other screen follows the theme.

### Type roles (Roboto, the Compose default)

| Role | Size | Weight | Line | Used for |
|---|---|---|---|---|
| display | 32sp | Bold | 38sp | Splash wordmark, user name |
| headline | 26sp | Bold | 32sp | App bar titles, screen titles |
| title.lg | 22sp | Bold | 28sp | Card headings, section titles |
| title | 20sp | Bold | 26sp | Item names, button labels |
| stat | 32sp | Bold | 36sp | Budget figures, nutrition score, totals |
| body | 16sp | Regular | 22sp | Descriptions, warning text |
| caption | 14sp | Regular | 19sp | Units, quantities, helper lines |
| label | 12sp | Bold | 16sp | ALL-CAPS section labels, +1.5sp tracking |
| micro | 11sp | Bold | 14sp | Nutrition tags, alert badges, nav labels |

### Layout rules

- Design viewport 393 x 832 dp.
- Screen gutter 20dp left and right.
- Spacing scale 4 / 8 / 12 / 16 / 20 / 24 / 32 dp.
- Corner radius: input 10dp, card 14dp, sheet 22dp, button and chip pill.
- Elevation: cards 0dp with a 1dp outline; docked total bar 6dp; dialog 12dp.
- Touch target 48 x 48dp minimum.
- Icon sizes: 20dp inline, 24dp nav and app bar, 40dp alert and empty state.
- Motion: bars and rings 400ms, sheet 250ms, nav fade 150ms.

### Component library

| ID | Component | Composable | Specification |
|---|---|---|---|
| C-01 | Filled button | `Button` | 56dp tall, full width minus gutters, pill, primary fill, title/20sp |
| C-02 | Outlined button | `OutlinedButton` | 52dp, pill, 2dp border, label in the border colour; danger variant for log out |
| C-03 | Card | `Card` | 14dp radius, surface.card, 1dp outline, 0dp elevation, 16dp inner padding |
| C-04 | Input field | `OutlinedTextField` | 50dp, 10dp radius, 1dp outline, 2dp primary when focused, title/20sp |
| C-05 | Dropdown | `ExposedDropdownMenuBox` | 50dp, 10dp radius, trailing 20dp chevron, searchable, required |
| C-06 | Stepper | `Row` + 2 `IconButton` | 36dp circular buttons on surface.sunken, value between them |
| C-07 | Checkbox | `Checkbox` | 22dp box, 6dp radius, primary when checked, 48dp hit area |
| C-08 | Radio | `RadioButton` | 22dp, primary ring when selected, single-select |
| C-09 | Tag chip | `AssistChip` | 32dp, pill; primary.container for nutrients, danger for allergies, warning for conditions |
| C-10 | Section label | `Text` | label/12sp caps, +1.5sp tracking, on.surface.muted |
| C-11 | Progress rail | `LinearProgressIndicator` | 8dp tall, pill, primary on surface.sunken; warning below 60% of target |
| C-12 | Bottom nav | `NavigationBar` | 72dp, 4 items (Home, List, Nutrition, Profile), primary when selected |
| C-13 | Alert card | `Card` + `Row` | 14dp radius, 6dp leading colour bar, 28dp icon circle, tinted container background |

---

## 2. Final screen list

14 screens: 12 from the PDFs plus 2 auth screens. "AUTH-1" and "AUTH-2" are labels chosen for this plan; the PDFs reserve the SCR numbers.

| ID | Screen | Source | Status |
|---|---|---|---|
| SCR-01 | Splash | PDF | Exists, rework to spec |
| SCR-02 | Welcome / Onboarding | PDF | New |
| AUTH-1 | Login | Project requirement | Exists, restyle and wire up |
| AUTH-2 | Create account | Project requirement | New |
| SCR-03 | Profile setup (setup and edit modes) | PDF | New |
| SCR-04 | Home dashboard | PDF | New |
| SCR-10 | AI processing | PDF (mockup only) | New |
| SCR-05 | Generated grocery list | PDF | New |
| SCR-11 | Grocery item details | PDF (mockup only) | New |
| SCR-06 | Edit grocery list | PDF | New |
| SCR-07 | Nutrition analysis | PDF | New |
| SCR-08 | Allergy and health alerts | PDF | New |
| SCR-12 | Alert details | PDF (mockup only) | New |
| SCR-09 | Profile | PDF | New |

### Screen requirements

| ID | Key UI | Actions |
|---|---|---|
| SCR-01 | Circle logo with cart-and-leaf glyph (scales 0.92 to 1.0 over 220ms), "NutriCart AI" in display/32sp, tagline "Smarter groceries on your budget" in primary at 70% alpha, 80 x 4dp indeterminate bar, "Preparing your plan" | Automatic after 600–3000 ms; routes per section 3 |
| SCR-02 | Skip text button, 216dp illustration banner (20dp radius, primary.container), heading, description, pager dots (8dp, active 28 x 8dp pill), "Get started", footnote "Takes about a minute", 3-page `HorizontalPager` | Get started advances the pager, last pane opens Login; Skip opens Login; back on pane 1 exits the app |
| AUTH-1 | Logo, "Welcome Back", "Login to continue", card with Email and Password fields, "Login" button, link to Create account | Login validates against the local account, then routes per section 3 |
| AUTH-2 | Same layout as Login; card with Name, Email and Password fields, "Create account" button, link back to Login | Creates the local account, logs in, opens profile setup |
| SCR-03 | App bar with back chevron, step indicator ("Step 1 of 2"), region dropdown, household stepper (min 1, max 12, default 4), allergy checkboxes (Peanuts, Shellfish, Dairy, Eggs, Gluten), condition radios (None, Diabetes, Hypertension), docked "Save profile" | Save goes Home (setup) or pops to Profile (edit); back warns before discarding |
| SCR-04 | Greeting by time of day and name, avatar with initials, "Seasonal picks" banner, "MONTHLY BUDGET" input with Tk prefix, "Generate grocery list", last-month card (hidden on first run), "This month so far" card with items / nutri score / alerts, bottom nav | Generate opens SCR-10; avatar opens SCR-09; alerts cell opens SCR-08 (not tappable at zero); tabs |
| SCR-10 | "Building your list", "Usually takes a few seconds", percentage ring, 4-step checklist (Reading regional prices, Matching nutrition targets, Fitting to your budget, Checking allergies), budget and people chips, "Cancel" | Completes to SCR-05; Cancel or back returns Home with nothing written; failure stays put with retry |
| SCR-05 | App bar (back chevron when pushed, trailing 24dp chart icon), pill search bar, category chips (All / Protein / Grains / Veg), 76dp item cards with circular checkbox, quantity, price and nutrient tag, docked total card (estimated total, remaining budget, budget rail, "% of budget used"), "Edit list" | Card opens SCR-11; checkbox marks bought and dims the row; swipe left reveals delete, swipe right marks bought; Edit opens SCR-06; chart icon opens SCR-07 |
| SCR-11 | Item header with quantity, price and tag; "PER 100 G" table (calories, protein, carbohydrate, fat, iron); "WHY IT IS HERE" card; price basis line; "Adjust quantity"; "Remove from list" | Back pops to the list |
| SCR-06 | 94dp cards with name, price, stepper (min 1, max 99) and delete button; "Add item"; live summary card (total cost plus calories, protein, fat, carbohydrate rails); docked "Save changes" | Every press recomputes cost and macros (debounced 120ms); delete has a 5-second undo snackbar; Add opens an item picker sheet; Save writes one transaction and pops; back warns when unsaved |
| SCR-07 | Score ring (148dp, 17dp stroke, sweeps over 400ms), score 0–100 with "OUT OF 100", 2-column grid of four nutrient cards (protein, carbs, fat, weakest micronutrient), single deficiency alert, recommendation card | Back returns to the caller; as a tab root there is no chevron and the bottom nav shows; "all targets met" state replaces the deficiency card |
| SCR-08 | Alert banner with count, one 152dp card per conflict (severity bar, ALLERGY or HEALTH badge, description, "Replace item", "Keep anyway"), safe-items confirmation card, docked "Continue to list" | Card opens SCR-12; Replace opens a substitutes picker; Keep anyway records an override; Continue pops |
| SCR-12 | Flagged item header, "WHY IT WAS FLAGGED", "SAFE ALTERNATIVES" priced list ranked by price proximity, "Replace with …", "Keep anyway" | Replace pops with a result; back pops to SCR-08 |
| SCR-09 | "Profile" title (no back), initials avatar, name, "Member since", card with region and household size, allergy chips, condition chip, "Edit profile", "Log out" (danger outlined), bottom nav | Edit opens SCR-03 in edit mode; Log out shows a confirmation dialog, then opens Login |

### Validation rules stated in the PDFs

- **SCR-03:** region required, condition required ("None" is valid), household 1–12. Save is disabled until region and condition are set. Errors show on the field, not in a dialog.
- **SCR-04:** numeric only, at most 7 digits, error below 500 or above 999999. Generate is disabled while blank and shows a spinner while generating. A typed budget survives rotation and process death.

---

## 3. Final navigation flow

```
splash ─┬─ onboarding not done ────> welcome ──Get started (last pane) / Skip──> login
        ├─ onboarded, no session ──> login
        ├─ session, no profile ────> profile/setup
        └─ session + profile ──────> home

login ──"Create account"──> register ──success──> profile/setup
login ──success──┬─ no profile ──> profile/setup ──Save──> home
                 └─ profile ─────> home

home ──Generate──> generate?budget={amount} ──done──> list/{listId}
list/{listId} ──> item/{itemId} | list/{listId}/edit | nutrition/{listId}
home ──alerts cell──> alerts/{listId} ──card──> alerts/{listId}/item/{itemId}
profile ──Edit──> profile/setup?mode=edit ──Save──> pop to profile
profile ──Log out (confirm)──> login
Bottom tabs: home | list | nutrition | profile
```

### Routes

| Route | Screen | Arguments |
|---|---|---|
| `splash` | SCR-01 | none |
| `welcome` | SCR-02 | none |
| `login` | AUTH-1 | none (route name is this plan's choice) |
| `register` | AUTH-2 | none (route name is this plan's choice) |
| `profile/setup?mode={mode}` | SCR-03 | `mode` = edit when opened from Profile |
| `home` | SCR-04 | none |
| `generate?budget={amount}` | SCR-10 | `budget` (Int, Tk) |
| `list` | SCR-05 as tab root | none; latest list resolved inside the graph |
| `list/{listId}` | SCR-05 pushed | `listId` (Long) |
| `list/{listId}/edit` | SCR-06 | `listId` (Long) |
| `item/{itemId}` | SCR-11 | `itemId` (Long) |
| `nutrition` | SCR-07 as tab root | none |
| `nutrition/{listId}` | SCR-07 pushed | `listId` (Long) |
| `alerts/{listId}` | SCR-08 | `listId` (Long) |
| `alerts/{listId}/item/{itemId}` | SCR-12 | `listId`, `itemId` (Long) |
| `profile` | SCR-09 | none |

### Back-stack rules

- Splash is always popped with `popUpTo("splash") { inclusive = true }` (PDF).
- Welcome is popped when Login opens, so back on Login exits the app and onboarding is never shown again.
- Login and Create account are popped on success. Back from Create account returns to Login.
- Saving the profile in setup mode clears everything behind Home, so back on Home exits the app (PDF FLOW-03, adapted because Welcome is already gone).
- Log out clears the whole stack and leaves only Login.
- `generate` is popped when the list opens, so back from the list returns Home.
- All four tabs use `popUpTo(startDestination) { saveState = true }`, `launchSingleTop = true` and `restoreState = true`. Tabs never stack; back from any tab returns Home.
- SCR-05 and SCR-07 are each one composable with two configurations: pushed (back chevron, no bottom nav) or tab root (bottom nav, no chevron).
- A replacement returns `itemId` and `replacementId` through `SavedStateHandle`. The user stays on SCR-08 while other alerts remain.

### Transitions

| Transition | Motion |
|---|---|
| Splash to next screen | Fade, 200ms |
| Forward pushes (setup, edit, alerts, alert details) | Slide in from the right, 250ms |
| Home to processing | Fade through, 200ms |
| Processing to list | Crossfade, 250ms; item rows stagger in at 40ms intervals |
| List to item details | Shared element on the item name, 300ms |
| List to nutrition | Slide up, 300ms; reverse is slide down, 250ms |
| Tab switches | Fade, 150ms |
| Welcome to Login, Login to Create account | Slide in from the right, 250ms (by analogy; not in the PDFs) |

### Differences from the PDF flows caused by Login

| PDF flow | As specified | In this plan |
|---|---|---|
| FLOW-01 splash decision | Checks whether a profile exists | Also checks onboarding and session |
| FLOW-02 Get started | Goes to `profile/setup` | Goes to `login`, which leads to setup |
| SCR-02 Skip | Home with an unset profile (Screen Details) | Goes to `login`; Home is only reachable with a profile |
| FLOW-03 Save profile | `popUpTo("welcome")` | Clears everything behind Home |
| SCR-09 Log out | Exits to SCR-02 | Exits to Login; onboarding stays marked complete |

---

## 4. Authentication / local account flow

There is no backend, no Firebase and no network call. Accounts live on the device only.

- **Create account:** collects name, email and password. On success the account is saved, the user is logged in automatically, and they go to profile setup. Auto-login after registration is an assumption.
- **Login:** email and password are checked against the local account table. A wrong email or password shows one generic error on the form.
- **Session:** the logged-in account's ID is kept in local settings. It survives app restarts and is cleared only by Log out.
- **Password storage:** passwords are never stored. Each account stores a random salt and a PBKDF2 hash, using the platform's built-in crypto with no extra dependency. Because min SDK is 24, the algorithm is `PBKDF2WithHmacSHA1`; the SHA-256 variant needs API 26.
- **Validation (assumptions; the PDFs have none):**
  - Name must not be blank.
  - Email must be a valid format and not already registered.
  - Password must be at least 6 characters.
  - Errors show on the field in `danger`, and the button is disabled until all fields are filled, following the PDF's SCR-03 pattern.
- **Not included:** forgot password, confirm-password field, email verification, account deletion.

### Login screen: current state versus target

The existing layout structure (logo, heading, subtitle, card with two fields and a button), all five text strings, and the `email` / `password` state are preserved. The change is a restyle plus wiring, not a rewrite.

| Element | Now | Target |
|---|---|---|
| Background | Green vertical gradient | `surface` token, following light/dark theme |
| Logo | 80dp white circle with 🥦 emoji | The same cart-and-leaf logo as the splash |
| "Welcome Back" | 24sp Bold, white | headline 26sp Bold, `on.surface` |
| "Login to continue" | 14sp, white at 85% | caption 14sp, `on.surface.muted` |
| Form card | White, 20dp radius, 20dp padding, no outline | C-03: `surface.card`, 14dp radius, 1dp outline, 16dp padding |
| Fields | Floating label, 12dp radius, default height | C-04: 50dp, 10dp radius, 2dp `primary` border on focus, with a C-10 caps label above |
| Login button | 50dp, 12dp radius, `MidGreen`, 16sp | C-01: 56dp, pill, `primary`, 20sp Bold |
| Screen padding | 24dp | 20dp gutter |
| Colours | Hard-coded `Color.White` and brand greens | Theme tokens, so dark mode works |

Functional changes:

- Mask the password field and set keyboard types on both fields.
- Add validation, error and loading states.
- Replace the no-op `onClick` with the local login, and add a success callback for navigation.
- Keep `email` and `password` across rotation.
- Make the column scroll and pad for the keyboard.
- Add a text link to Create account.

The brand greens (`DarkGreen`, `MidGreen`, `LightGreen`) are no longer used once Login is on the tokens.

### Create account screen

Reuses the Login layout: logo, heading, subtitle, one card with three fields (Name, Email, Password), a filled "Create account" button, and a text link back to Login.

---

## 5. Profile flow

- **First time:** after login or registration with no profile, SCR-03 opens empty in setup mode. Save writes the profile and goes Home.
- **Returning:** login finds the account's profile and goes straight Home.
- **Edit:** Profile → "Edit profile" opens SCR-03 pre-filled with the step indicator hidden. Save pops back to Profile, and back warns before discarding. If region or household size changed, the save warns and offers to regenerate the list (FLOW-16).
- **Ownership:** each profile row is keyed to an account. With one account on the device this is exactly the PDF's single row; if a second account is created, it gets its own profile.
- **Name and "Member since":** both come from the account (name from registration, date from account creation). This closes the gap where the PDFs show a name but never collect one.
- **Persistence:** the write is atomic; no partial profile is ever stored (PDF).

---

## 6. Database / local storage structure

### Room database

| Table | Fields | Notes |
|---|---|---|
| `accounts` | id, name, email (unique), passwordHash, passwordSalt, createdAt | Addition for local auth |
| `profiles` | accountId (key), region, householdSize, allergies, condition | PDF `ProfileEntity` plus the account link |
| `budgets` | id, accountId, amount, month, listId | Feeds `BudgetDao.latest()` and the "last month" card |
| `grocery_lists` | id, accountId, budget, createdAt | |
| `list_items` | id, listId, catalogItemId, quantity, bought, alertOverridden | "Keep anyway" sets the override |
| `catalog_items` | id, name, unit, category, nutrient tag, per-100g nutrients, allergen tags, condition flags | Seeded |
| `region_prices` | catalogItemId, region, price, updatedAt | Seeded |

DAOs: `AccountDao`, `ProfileDao` (exists, observe, upsert), `BudgetDao` (latest), `GroceryListDao` (observe, latest), `ItemDao` (insert, update, delete), `CatalogDao`.

### DataStore (`SettingsStore`)

- `onboardingComplete`
- `loggedInAccountId`

### In code

- The RDA table.
- The region list.
- The allergy and health-condition options.

Lists and budgets are keyed to the account, so a second account would not see the first one's history. This is an assumption; the PDF only says log out keeps grocery history on the device, which this still does.

---

## 7. Files to create / modify

### Modified

- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` — add Navigation Compose, lifecycle-viewmodel-compose, Room with KSP, and DataStore. The older core-ktx, lifecycle and activity versions will probably need bumping.
- `app/src/main/AndroidManifest.xml` — register an `Application` class.
- `MainActivity.kt` — keeps `MainActivity`. `AppNavigator` becomes the NavHost host. `SplashScreen` and `LoginScreen` move to their own files.
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — the 15 tokens and nine type roles, with dynamic colour off.
- `app/src/main/res/values/strings.xml`.
- `CLAUDE.md` — its architecture section will be out of date.

### Created (under `com.example.nutricart`)

| Package | Files |
|---|---|
| root | `NutriCartApp.kt` (Application plus a small manual dependency container; no Hilt) |
| `navigation/` | `Routes.kt`, `NutriCartNavHost.kt` |
| `ui/components/` | `Buttons.kt`, `Cards.kt`, `Inputs.kt`, `Stepper.kt`, `Selection.kt`, `TagChip.kt`, `SectionLabel.kt`, `ProgressRail.kt`, `BottomNav.kt`, `AppLogo.kt` |
| `ui/screens/splash/` | `SplashScreen.kt` (moved) |
| `ui/screens/onboarding/` | `OnboardingScreen.kt` |
| `ui/screens/auth/` | `LoginScreen.kt` (moved), `CreateAccountScreen.kt`, `AuthViewModel.kt` |
| `ui/screens/profilesetup/` | `ProfileSetupScreen.kt`, `ProfileSetupViewModel.kt` |
| `ui/screens/home/` | `HomeScreen.kt`, `HomeViewModel.kt` |
| `ui/screens/generate/` | `GenerateScreen.kt`, `GenerateViewModel.kt` |
| `ui/screens/grocerylist/` | `GroceryListScreen.kt`, `GroceryListViewModel.kt` |
| `ui/screens/editlist/` | `EditListScreen.kt`, `EditListViewModel.kt` |
| `ui/screens/itemdetails/` | `ItemDetailsScreen.kt`, `ItemDetailsViewModel.kt` |
| `ui/screens/nutrition/` | `NutritionScreen.kt`, `NutritionViewModel.kt` |
| `ui/screens/alerts/` | `AlertsScreen.kt`, `AlertsViewModel.kt` |
| `ui/screens/alertdetails/` | `AlertDetailsScreen.kt`, `AlertDetailsViewModel.kt` |
| `ui/screens/profile/` | `ProfileScreen.kt`, `ProfileViewModel.kt` |
| `data/local/` | `NutriCartDatabase.kt`, `Entities.kt`, `Daos.kt`, `Converters.kt`, `SeedData.kt` |
| `data/` | `SettingsStore.kt` |
| `data/auth/` | `PasswordHasher.kt`, `AuthRepository.kt` |
| `domain/` | `GroceryGenerator.kt`, `NutritionCalculator.kt`, `AllergyMatcher.kt`, `RdaTable.kt` |
| `res/drawable/` | Icon vectors (cart-and-leaf logo, nav icons, chevron, search, plus, and others as needed) |
| `app/src/test/` | Tests for the hasher, validation, generator, calculator and matcher |

ViewModels use the DAOs directly, as the PDFs describe; the only repository is the auth one.

---

## 8. Implementation phases

Work stops after each phase so the app can be run and checked before continuing.

| # | Phase | Scope | Result |
|---|---|---|---|
| 0 | Foundations | Dependencies and a verified build (KSP with AGP 9.3.1 checked first), Application class, theme, routes skeleton, Splash and Login moved to their own files unchanged | App builds and behaves as today |
| 1 | Components | C-01 to C-13 and the logo, with previews | Reusable library |
| 2 | Storage | Room schema, DataStore, seed data, password hasher, auth repository, unit tests | Persistence and auth logic working |
| 3 | Entry and auth | Splash to spec with the 4-way decision, Onboarding, Login restyle and wiring, Create account | First run through to a logged-in session |
| 4 | Profile and shell | SCR-03 (both modes), bottom nav, Home, Profile, Log out | Full loop: register, set up, Home, log out, log in |
| 5 | Generation | `GroceryGenerator`, SCR-10, SCR-05 | Budget in, list out |
| 6 | Editing | SCR-06 with live summary and undo, SCR-11 | List editable |
| 7 | Nutrition | `NutritionCalculator`, SCR-07 in both entry modes | Scoring works |
| 8 | Alerts | `AllergyMatcher`, SCR-08, SCR-12, replace and override | Alerts work |
| 9 | Polish | Transitions, dark theme, empty and error states, rotation restore, remaining tests | Spec-complete |

---

## 9. Decisions already made

1. Login is required and stays in the app.
2. Accounts are local to the device; no Firebase or backend. Registration collects name, email and password.
3. First run shows Welcome / Onboarding, then Login. A logged-in user is not sent through onboarding or login again.
4. Log out goes to Login; onboarding stays marked complete.
5. Skip on onboarding goes to Login.
6. After login: no profile opens profile setup; an existing profile opens Home.
7. One profile per device as in the PDF, linked to the local account so it is restored after login.

---

## 10. Remaining ambiguities and conflicts

Each has the default this plan will use. The defaults were approved on 2026-10-04 with these conditions:

- They apply unless they conflict with the PDFs; no additional features are to be invented.
- Row 6 (onboarding panes 2 and 3): do not implement content. Pause in Phase 3 and get the proposed content and design approved first.
- Row 1 (data): seed/demo data only, structured so it can be replaced, and never presented as real or current market prices.

| # | Issue | Default |
|---|---|---|
| 1 | No item, price, nutrition, RDA or allergen data is supplied | A small seed set (about 40 items) for Bangladesh's eight divisions; values are placeholders, not real prices |
| 2 | The "AI" generator's algorithm is undefined and no network is specified | An on-device rule-based generator |
| 3 | Written sizes disagree with the mockups (splash logo 200dp, home avatar 88dp, profile picture 168dp all look much smaller in the drawings) | Follow the written values |
| 4 | Stepper value "stat/24sp" conflicts with stat being 32sp; some radii (16, 18, 20dp) break the 14dp card rule | Follow the per-element value |
| 5 | SCR-03 shows "Step 1 of 2" but step 2 is never specified | Show the indicator as drawn; build no second step |
| 6 | Onboarding panes 2 and 3 have no content | Draft two short panes (nutrition; allergy checks) for approval in phase 3 |
| 7 | SCR-09 says the name is "editable through SCR-03", but SCR-03 has no name field | Name is read-only from the account; no name field added |
| 8 | SCR-08's Continue and Replace "return to the list", but FLOW-11 enters from Home | Pop to wherever the user came from |
| 9 | No trigger is drawn for SCR-05 → SCR-08 | None added; alerts are reached from Home only |
| 10 | The Add-item and replacement pickers have no spec | A simple searchable bottom sheet built from existing components |
| 11 | "Seasonal picks" has no destination | Banner shown but not tappable |
| 12 | List tab before any list exists is unspecified (the Nutrition tab is: disabled with a one-line explanation) | Treat the List tab the same way |
| 13 | "Last month spent / saved" is undefined | Spent is the last list's total; saved is budget minus spent |
| 14 | UI Flow mentions four STATE CHANGE transitions and "preference writes" on Profile, but none are drawn | Ignored |
| 15 | The log-out dialog wording is not given | "You'll be logged out. Your account, profile and grocery history stay on this device." |
| 16 | App label is "NutriCart" but the PDFs say "NutriCart AI" | Use "NutriCart AI" on the splash only; leave the launcher label alone |
| 17 | Password rules are not specified anywhere | Minimum 6 characters |
| 18 | Whether a second account on the same device shares grocery history | Each account sees only its own lists and budgets |
| 19 | Whether registration logs the user in automatically | Yes |
| 20 | The PDFs mention a "theme setting" but show no settings UI | Follow the system light/dark setting |
| 21 | SCR-10, SCR-11 and SCR-12 have mockups but no element tables | Build from the mockups using the existing components and tokens |
