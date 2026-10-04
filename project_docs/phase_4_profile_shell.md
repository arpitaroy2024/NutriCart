# NutriCart — Phase 4: Profile Setup & Main App Shell

**Date:** 2026-10-04
**Phase:** Phase 4 — Profile setup, Home, Profile, bottom navigation, logout
**Status:** Implemented and revised twice (see "Phase 4 Revision" and "Final Revision" below); build, 170 unit tests and lint pass. Only partly seen on a device (see the Final Revision).

Everything is on-device. No grocery generation, list, nutrition, alert, AI, network or backend code was added.

## 1. Screens implemented

| ID | Screen | Files |
|---|---|---|
| SCR-03 | Profile setup (first-time setup and edit) | `ui/screens/profilesetup/ProfileSetupScreen.kt`, `ProfileSetupViewModel.kt` |
| SCR-04 | Home dashboard | `ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt` |
| SCR-09 | Profile | `ui/screens/profile/ProfileScreen.kt`, `ProfileViewModel.kt` |
| — | Bottom-navigation shell | `navigation/NutriCartNavHost.kt`, `navigation/ShellTabs.kt` |
| — | "Coming soon" tab for Grocery list and Nutrition | `ui/screens/upcoming/UpcomingScreen.kt` |

The Phase 3 testing placeholder and its "Back to Login (Testing)" button are removed.

## 2. Profile setup behaviour

> The allergy, health-condition and household rules in this section were changed by the Phase 4 Revision at the end of this document. Where they differ, the revision is current.

**Fields.** The profile holds the four values the PDFs define. Age, gender, height, weight and activity level were listed in the Phase 4 brief but are not in the PDFs or the plan; the project owner chose "PDF fields only" when asked.

| Field | Control | Rule |
|---|---|---|
| Region | Dropdown of the eight divisions | Required, no default |
| Household size | Stepper | 1 to 12, default 4; buttons disable at the limits |
| Allergies | Five checkboxes (Peanuts, Shellfish, Dairy, Eggs, Gluten) | Optional, any number |
| Health condition | Three radio rows (None, Diabetes, Hypertension) | Required, no default; None is a valid answer |

The account's name is shown at the top ("Signed in as …"). It comes from the account and is not editable here.

**Validation.**

- "Save profile" is disabled until a region and a health condition are chosen. A line above the button says which is missing.
- Household size cannot leave 1 to 12, in the screen and again in the repository.
- A failed save shows "Couldn't save your profile. Please try again." above the button.
- Nothing is written until Save is pressed, and the profile is written in one statement, so a partial profile is never stored.

**Behaviour.**

- The screen scrolls; the Save button is docked at the bottom above the system bar. No field on this screen opens the keyboard.
- Entered values live in the ViewModel and survive rotation.
- An existing profile is always loaded into the form, so edit mode opens pre-filled.
- Leaving with unsaved values (back chevron or system back) asks "Discard changes?" first.

**Two modes.**

| | First-time setup | Edit (from Profile) |
|---|---|---|
| Route | `profile/setup` | `profile/setup?mode=edit` |
| Title | "Set up your profile" | "Edit profile" |
| Back chevron | None; there is no screen behind it | Yes, returns to Profile |
| Top-bar action | "Log out" | None |
| After saving | Home, with setup removed from the back stack | Back to Profile |

## 3. Profile data model and repository

> Superseded by the Phase 4 Revision: the profile table changed and the database is now version 2, with a migration.

**No changes.** The Phase 2 `profiles` table, `ProfileDao` and `ProfileRepository` are used as they were. There is no schema change and no migration; the database is still version 1.

The profile is keyed to the logged-in account, and the repository reads that account from the session, so one account's profile cannot be shown to another.

One new non-storage class: `domain/BudgetRules.kt`, the budget limits used by Home.

## 4. Home behaviour

| Element | What it does in Phase 4 |
|---|---|
| Greeting | "Good morning / afternoon / evening" by the clock, over the account's first name |
| Avatar | Initials; tapping it opens the Profile tab |
| Monthly budget card | A numeric field with a "Tk" prefix and thousands separators; a line showing who the list is for ("For 4 people · Rangpur Division") |
| Budget validation | Digits only, at most 7 digits; error below Tk 500 or above Tk 999,999, shown after leaving the field or tapping Generate |
| Generate grocery list | Disabled while the field is blank. With a valid budget it only shows "List generation isn't available yet. It is built in Phase 5." Nothing is generated or stored. A permanent note under the button says the same. |
| List summary | Replaced by a "No grocery list yet" card, because no list can exist yet |
| Typed budget | Kept in saved state, so it survives rotation, process death and tab switches |

Not shown in Phase 4: the Seasonal picks banner, the "last month" card and the three-cell summary (see sections 10 and 17).

## 5. Bottom navigation

- Four tabs: Home, List, Nutrition, Profile. Home and Profile are real screens. List and Nutrition open a "Coming soon" screen that names the feature and the phase it is built in.
- The bar is drawn once, outside the navigation host, so it stays in place while tabs change. It is shown only on the four tab roots and hidden on splash, onboarding, login, create account and profile setup.
- The selected tab is primary green; the others are faint.
- Switching tabs never stacks them: Home stays underneath, each tab keeps its own state, and re-selecting a tab does not create a second copy.
- System back from List, Nutrition or Profile returns to Home. Back on Home exits the app.

## 6. Profile screen

Shows, read live from local storage:

- Initials avatar, name, and "Member since" (month and year the account was created).
- A card with Email, Region and Household size.
- Allergies as chips, or "None recorded".
- Health condition as a chip; "None" is shown rather than hidden.
- "Edit profile" (opens profile setup in edit mode) and "Log out".

The screen scrolls, so both buttons are always reachable. It never receives the password, hash or salt: the account model given to the UI does not contain them.

Edits appear without a refresh, because the screen observes the profile.

## 7. Logout behaviour

1. "Log out" opens a confirmation: "You'll be logged out. Your account, profile and grocery history stay on this device."
2. Confirming clears `loggedInAccountId` in DataStore. Nothing else is touched.
3. The app opens Login and clears the whole back stack, so Back cannot return to Home or Profile.
4. Logging in again restores the same account and profile and goes straight to Home.

First-time profile setup also has a "Log out" action in its top bar, which does the same without a confirmation.

## 8. Session and routing

The Phase 3 routing is unchanged; its destinations are now real screens.

| Stored state | Destination |
|---|---|
| Onboarding not completed | Onboarding |
| Onboarding completed, no session | Login |
| Session names a missing account | Session cleared, then Login |
| Session, no profile | Profile setup |
| Session and profile | Home |

`EntryRouter` was not modified.

## 9. Back-stack behaviour

| Situation | Back stack afterwards |
|---|---|
| Login or Create account succeeds | Login and Create account removed |
| Profile saved in first-time setup | Only Home; Back exits the app |
| Tab switch | Home, plus the selected tab if it is not Home |
| Profile → Edit profile | Home, Profile, Profile setup |
| Edit saved or discarded | Home, Profile |
| Logout | Only Login; Back exits the app |
| Restart while logged in | Splash is removed; Home or Profile setup is the only entry |

## 10. UI/UX review against the PDF

Each row gives what the PDF shows, what was suggested, and what was implemented.

### Profile setup

| # | PDF | Suggestion | Decision |
|---|---|---|---|
| 1 | "Step 1 of 2" bar at 50%; no step 2 exists anywhere | A progress bar that can never complete misleads. Remove it. | **Implemented:** no step indicator. |
| 2 | Region dropdown is "searchable" | Typing to search eight options raises the keyboard for no benefit. Use a plain picker. | **Implemented:** tap opens the list; no keyboard. The component keeps its searchable mode for longer lists. |
| 3 | Save is disabled until region and condition are set, with no explanation; the PDF also describes an error "on submit", which a disabled button can never trigger | Say what is missing next to the button. | **Implemented:** a hint line above the button names the missing choice. |
| 4 | Back chevron on first-time setup | Nothing is behind the screen, so the chevron would close the app. | **Implemented:** no chevron in first-time setup; system back still asks before discarding. |
| 5 | No way out of setup for a user who registered the wrong account | Offer Log out. | **Implemented:** "Log out" in the top bar during first-time setup. |
| 6 | Checkboxes and radios sit loose on the background | Group each section in a card so the form scans as four blocks. | **Implemented.** |
| 7 | Labels do not say what is optional | Mark it. | **Implemented:** "Allergies · optional" with "Select all that apply"; "Choose one. None is a valid answer." under Health condition. |
| 8 | Name is not shown, although Home and Profile display it | Confirm which account is being set up. | **Implemented:** "Signed in as …". |
| 9 | Allergies as a checkbox list | Chips would be more compact. | **Not implemented:** rows have a full-width 48dp target and read more clearly; kept as the PDF. |

### Home

| # | PDF | Suggestion | Decision |
|---|---|---|---|
| 10 | Avatar written as 88dp (drawn about 40dp) | 88dp beside a 32sp name crowds the header. Use the 48dp touch-target size. | **Implemented:** 48dp. |
| 11 | "Seasonal picks" banner; no offers data or destination exists | A promotion that does nothing is clutter and looks broken. Hide it until offers exist (the PDF itself hides it when a region has no offers). | **Implemented:** hidden. |
| 12 | Generate button 50dp, unlike every other filled button (56dp) | Use the standard height. | **Implemented:** 56dp. |
| 13 | The budget card does not say what the budget covers | Show the household it will be sized for. | **Implemented:** "For 4 people · Rangpur Division". |
| 14 | Budget drawn as "12,000" | Format while typing. | **Implemented:** thousands separators; the stored value stays digits. |
| 15 | Error below 500, with no timing given | An error while the first digits are typed is noise. | **Implemented:** shown after leaving the field or tapping Generate. |
| 16 | Summary card with three figures; no state for "no list" | Dashes or zeros would look like data. Show an empty state. | **Implemented:** "No grocery list yet" card. |

### Profile

| # | PDF | Suggestion | Decision |
|---|---|---|---|
| 17 | Avatar written as 168dp; in the mockup "Log out" is cut off by the navigation bar | Shrink the avatar and let the screen scroll so both actions are reachable. | **Implemented:** 96dp, scrolling. |
| 18 | Card shows region and household only | Show which account this is. | **Implemented:** an Email row. |
| 19 | Card values in title/20sp Bold | An email or "Chattogram Division" would not fit beside its label. | **Implemented:** body/16sp Bold, ellipsised. |

### Shell

| # | PDF | Suggestion | Decision |
|---|---|---|---|
| 20 | Nutrition tab "disabled with a one-line explanation" until a list exists | — | **Implemented differently, as instructed for this phase:** both unfinished tabs open a "Coming soon" screen. The PDF's rule should be revisited when those tabs are built. |

## 11. Suggestions implemented

Rows 1 to 8 and 10 to 19 above. Row 9 was considered and not implemented. Row 20 follows the Phase 4 brief.

## 12. Tests

108 unit tests in total, all passing; 36 are new. No earlier test was removed or changed.

| Test class | Tests | Covers |
|---|---|---|
| `ProfileViewModelsTest` (new) | 17 | Setup: empty start, both required fields, household limits, allergy toggles, save creates the profile for the logged-in account, edit loads and overwrites, a second account sees an empty form, logout from setup. Profile: shows account and profile with no password data, updates after an edit, logout clears the session and keeps the account and profile. Home: name and household, digits only, blank disables Generate, out-of-range errors, valid budget only raises the notice and stores nothing, budget restored from saved state |
| `AppShellNavigationTest` (new) | 7 | Runs the real navigation graph and screens: fresh install → onboarding; logged out → login; no profile → fill in setup → Home with setup off the stack; tabs do not stack and back returns Home; Generate is only a notice; Profile shows stored values and editing returns to Profile; logout leaves only Login on the stack and keeps the account and profile |
| `FormattingTest` (new) | 8 | Initials, first name, greeting period, thousands formatting and cursor mapping, tab-to-route mapping, bar shown only on tab roots, edit-mode route |
| `BudgetRulesTest` (new) | 4 | Sanitising, limits, out-of-range, blank |
| Phase 1–3 classes | 72 | Unchanged |

Routing for no profile, profile, logged out and stale session is covered by the Phase 3 `EntryRouterTest` and `EntryViewModelsTest`, and again end to end by `AppShellNavigationTest`.

## 13. Files created

Main (`app/src/main/java/com/example/nutricart/`):

- `domain/BudgetRules.kt`
- `navigation/ShellTabs.kt`
- `ui/Formatting.kt`
- `ui/components/Dialogs.kt`
- `ui/components/ThousandsVisualTransformation.kt`
- `ui/screens/profilesetup/ProfileSetupScreen.kt`, `ProfileSetupViewModel.kt`
- `ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt`
- `ui/screens/profile/ProfileScreen.kt`, `ProfileViewModel.kt`
- `ui/screens/upcoming/UpcomingScreen.kt`

Tests (`app/src/test/java/com/example/nutricart/`):

- `domain/BudgetRulesTest.kt`
- `ui/FormattingTest.kt`
- `ui/ProfileViewModelsTest.kt`
- `ui/AppShellNavigationTest.kt`

Docs:

- `project_docs/phase_4_profile_shell.md`

## 14. Files modified

- `navigation/NutriCartNavHost.kt` — the shell, the real destinations, logout and tab navigation.
- `navigation/Routes.kt` — edit-mode route for profile setup.
- `ui/AppViewModelFactory.kt` — three new ViewModels.
- `ui/components/Inputs.kt` — `NutriTextField` gained `readOnly`; `NutriDropdown` gained `searchable`.
- `res/values/strings.xml` — text for the new screens; placeholder strings removed.
- `app/build.gradle.kts` — Compose UI test library for unit tests.
- `project_docs/implementation_plan.md`, `project_docs/phase_3_entry_auth.md`, `CLAUDE.md` — status and notes.

Deleted: `ui/screens/placeholder/PlaceholderScreen.kt`.

## 15. Dependencies

No new library. `androidx.compose.ui:ui-test-junit4` (and the Compose BOM) were already used for instrumented tests and are now also available to unit tests (`testImplementation`), for `AppShellNavigationTest`.

## 16. Deviations from the PDFs

All of section 10, plus:

| Item | PDF / plan | Implemented | Why |
|---|---|---|---|
| Profile fields | Brief listed age, gender, height, weight, activity level | PDF fields only | Project owner's decision; the PDFs score nutrition per household |
| Name editing | "Name is editable through SCR-03" | Read-only, from the account | Approved default; SCR-03 has no name field |
| Region or household changed in edit mode | "Warns and offers to regenerate" | Saves without a warning | No list exists to regenerate until Phase 5 |
| Logout from first-time setup | Not in the PDFs | Added, no confirmation | Nothing unsaved is lost and there is no other way out |
| Previous-budget card | Shown once a budget exists | Not built | No budget can exist until Phase 5 |
| Forward transitions | Slides | 200ms fade, as in Phase 3 | Left for the polish phase |
| Alerts cell, tab disabling | Described for SCR-04 and FLOW-19 | Not built | Depend on lists |

## 17. Deferred to Phase 5 and later

- Grocery generation, the processing screen and the generated list (Phase 5).
- Home's three-cell summary, "last month" card and alerts cell, once lists and budgets exist (Phase 5 onward).
- The regenerate warning when region or household size changes (Phase 5).
- List editing and item details (Phase 6), nutrition analysis (Phase 7), allergy and health alerts (Phase 8).
- Seasonal picks, if offers data is ever supplied.
- Slide and shared-element transitions (Phase 9).

## 18. Known limitations

1. **No visual check was done.** The phone was locked and then disconnected, so nothing was looked at on a real screen. The navigation tests prove the screens open, respond and navigate correctly, but not how they look. To check on the phone: spacing and contrast in light and dark mode, the keyboard over the budget field, the bottom bar against the system navigation bar, the dropdown list position, and long names or emails.
2. **Avatar and separator choices are untested by eye:** the 48dp and 96dp avatars and the thousands separators in particular.
3. **Typed profile values are not kept across process death**, only across rotation. The typed budget on Home is kept across both.
4. **The Generate button does nothing useful yet.** It is labelled as such on the screen.
5. **Login speed on a phone is still unmeasured** (300,000 hash iterations).
6. **Android backup still includes the account database** (unchanged from Phase 2).
7. **Greeting period is read when Home opens** and does not change while the screen stays open.

## Phase 4 Revision — Profile Data Model & Selection Improvements

> The single "Other" value described in parts 4, 7, 9 and 10 of this section was replaced by the Final Revision below: each section now holds any number of custom values and the "Other" chip is gone.

Made after review, before Phase 4 was committed. It changes only the profile's allergies, health conditions and household size, and what is needed to store and show them. Home, the shell, routing, logout and the rest of Phase 4 are as described above.

### 1. Previous limitation

- Health condition was a single choice from three (None, Diabetes, Hypertension).
- Allergies were limited to five (Peanuts, Shellfish, Dairy, Eggs, Gluten).
- Neither could hold anything outside its list.
- Household size stopped at 12.

### 2. Health conditions: multi-select

- Any number of conditions can be selected, and each can be deselected on its own.
- **One answer is required before Save.** The answer can be one or more conditions, a typed "Other" condition, or **"None of these"**.
- "None of these" and a real condition cannot both be selected: choosing "None of these" clears the conditions and the "Other" text; choosing any condition or "Other" clears "None of these".
- Opening Edit profile restores every saved selection.

"None of these" is not in the list supplied for the revision. It was kept because the form requires an answer, and without it a person with no condition could not save a profile. It is stored as an empty set, not as a condition.

### 3. Health-condition options

Diabetes, Prediabetes, High blood pressure, High cholesterol, Heart disease, Kidney disease, Liver disease, PCOS, Thyroid condition, Anemia, Celiac disease, and Other.

These are inputs for dietary planning. NutriCart does not diagnose, treat or give medical advice, and nothing in this revision acts on the selections.

### 4. Custom "Other" condition

- Selecting "Other" shows a text field under the chips.
- The typed value is trimmed, limited to 40 characters, and stored with the profile.
- It is restored when editing, can be retyped, and is removed by deselecting "Other".
- "Other" with nothing typed blocks Save, and the line above the button says "Type the other condition, or deselect Other". It is never dropped silently.
- One custom entry is held per profile.

### 5. Allergy options

Milk / Dairy, Egg, Fish, Shellfish, Chicken / Poultry, Beef, Soy, Peanut, Tree nuts, Wheat / Gluten, Sesame, and Other.

### 6. Allergies: multi-select

- Optional: zero, one or many can be selected, and each can be removed on its own.
- There is no "None" chip. Leaving every chip unselected means no allergies, the helper line says so ("Select all that apply. Leave empty if none."), and the Profile screen then shows "None recorded". This avoids a second set of mutual-exclusion rules for an optional field.

### 7. Custom "Other" allergy

The same behaviour as the custom condition: a text field appears, the value is trimmed, limited to 40 characters, stored, restored on edit, changeable, and removed by deselecting "Other". An empty "Other" blocks Save with "Type the other allergy, or deselect Other".

### 8. Household size: 1 to 20

- The accepted range is 1 to 20 (was 1 to 12), in the stepper and in the repository.
- 1, 12 and 20 are accepted; 0, 21 and negative values are rejected.
- The stepper is unchanged. It cannot hold an empty or non-numeric value.
- Home shows the saved size, including 20 ("For 20 people · …").

### 9. Data model and storage

| Field | Before | Now |
|---|---|---|
| `allergies` | Set of 5 possible values | Set of 11 possible values |
| `customAllergy` | — | Optional text |
| `condition` | Exactly one of None / Diabetes / Hypertension | Removed |
| `conditions` | — | Set of 11 possible values; empty means none |
| `customCondition` | — | Optional text |
| `householdSize` | 1 to 12 | 1 to 20 |

- In code the selections are typed sets (`Set<Allergen>`, `Set<HealthCondition>`), not strings. They are stored through the Room type converter the project already used for allergies and for the catalog's allergen and condition flags. Typed text is kept in its own columns, so free text never shares a column with the fixed values.
- Nothing is sized for a fixed number of selections.
- This was the smallest clean change: a child table per selection would have added two tables, two DAOs and a join for data that is always read and written as a whole with its profile.
- Existing constant names (Peanuts, Shellfish, Dairy, Eggs, Gluten, Diabetes, Hypertension) were kept, so stored values still read correctly; only their on-screen wording changed.
- The profile is still one row keyed to the account, so all selections and custom values belong to that account.
- The demo catalog's allergen tags now also mark fish, poultry, beef and soy items. No condition flags were added.

### 10. Room migration

- The database version went from 1 to 2, with `MIGRATION_1_2` registered on the database builder. There is no destructive fallback.
- The migration rebuilds only the `profiles` table (older Android SQLite cannot rename or drop a column) and copies every row.
- Accounts and all other tables are untouched.
- Each profile keeps its region, household size and allergies. Its single condition becomes a one-element set; "None" becomes the empty set. The two custom columns start empty.
- `MigrationTest` builds a version 1 database with three accounts and profiles, upgrades it, and checks the result.

### 11. Tests

137 unit tests in total, all passing (108 before the revision; 29 added). None was removed.

| Test class | Change |
|---|---|
| `ProfileSelectionTest` (new, 25) | Conditions: answer required, one, two, all eleven together, deselecting one, "None of these", mutual exclusion, "Other" alone, empty "Other" blocks saving, custom value restored / changed / removed, editing adds and removes without touching other fields. Allergies: optional, one, all eleven together, removing one, empty "Other", custom value restored / changed / removed, length cap. Household: 1, 12 and 20 accepted; 0, 21 and negative rejected; stepper limits; 20 saved, reloaded and shown on Home. Persistence: logout and login restores everything; a second account sees nothing and cannot change the first; the Profile screen receives every selection |
| `MigrationTest` (new, 3) | Version 1 → 2 keeps accounts and profiles; the upgraded database stores the new shape and still deletes a profile with its account; an empty version 1 database upgrades |
| `AppShellNavigationTest` (+1, now 8) | On the real screens: several chips, both "Other" fields typed, empty "Other" disables Save with its hint, then the values appear on the Profile screen |
| Existing tests | Updated only where the API changed: a single condition became a set, "None" became the empty set, and the rejected household size moved from 13 to 21. Their assertions are otherwise the same |

### 12. UI/UX changes

- **Chips instead of rows.** Both sections are wrapping multi-select chips inside a card. Twelve allergy options and thirteen condition options as 48dp rows would be about 1,200dp of scrolling; as chips they take roughly a third of that.
- **Selection is obvious without relying on colour:** a selected chip is filled green and gains a check mark; an unselected chip is outlined.
- **Tapping a chip again removes it.**
- **"Other" is the last chip** in each card; selecting it reveals its text field directly underneath, with a placeholder ("Type the allergy" / "Type the condition").
- **Keyboard:** the form scrolls and stays clear of the keyboard; Done closes it.
- **Labels:** "Health conditions" (plural), with "Select all that apply, or None of these."
- **Profile screen:** allergies and conditions are wrapping chips, in the same order as on the form, with the typed value last. A long typed value is cut with an ellipsis instead of overflowing.
- Chips are 40dp tall with 8dp between rows.

### 13. PDF comparison

The PDF does not ask for any of this. It specifies a single health condition, five allergies and a household of 1 to 12. These are product decisions made in review.

| PDF | Suggestion | Decision | Reason |
|---|---|---|---|
| Health condition: one radio choice from None / Diabetes / Hypertension, required | Allow several, from a longer list, plus a typed entry | Multi-select chips, 11 options, "Other", and "None of these" | People often have more than one condition; three options exclude most users |
| Allergies: five checkboxes | Cover the common food allergens and allow a typed entry | 11 options plus "Other" | The original list omitted fish, soy, tree nuts, sesame and others |
| Checkbox and radio rows | Use chips once the lists are long | Wrapping multi-select chips | Rows would roughly triple the form's length |
| Household stepper, 1 to 12 | Raise the limit | 1 to 20 | Requirement from review; larger and joint households exist |
| Profile shows one condition chip | Show them all | Wrapping chips, ellipsised | Several values must fit |

This replaces row 9 of section 10, where chips were considered and rejected for a five-item list.

### 14. Why this is better in real use

- A household is described as it is: several conditions, any common allergen, and anything unusual typed in.
- Nobody is forced to pick a wrong answer, and "no conditions" stays an explicit, deliberate answer.
- A typed value cannot be lost by accident.
- Later phases receive typed sets they can match against, instead of one value or free text.

### 15. Remaining limitations

1. **Still not checked on a device.** The revised build was installed on the phone, but the phone was in use, so it was not opened. Things to look at: chip wrapping and contrast in light and dark mode, the "Other" field above the keyboard, long typed values, and the form's overall length.
2. **The migration has only run in tests.** The first launch of this build on a phone that already has version 1 data is the first real run.
3. **One typed "Other" value per section.** Several can be typed into it, but they are stored as one entry.
4. **Nothing acts on the selections yet.** The allergy and condition matching belongs to Phase 8. Typed values can only ever be displayed, since no rule can match free text, and the new conditions have no catalog flags.
5. **Reaching 20 with the stepper takes sixteen taps** from the default of 4.
6. **Chips are 40dp tall**, below the 48dp touch target, with 8dp between rows.
7. **Schema export is still off**, so the migration test recreates version 1 from its recorded SQL instead of an exported schema file.

### Revision verification

Command: `gradlew.bat clean assembleDebug testDebugUnitTest lintDebug`

- **Build:** `BUILD SUCCESSFUL in 1m 51s`, 58 actionable tasks: 57 executed, 1 up-to-date.
- **Unit tests:** 137 total, 137 passed, 0 failed, 0 skipped. Also run twice more with `--rerun`; all passed.
- **Lint:** 0 errors, 17 warnings, the same 17 as before the revision. No new warnings.
- **Compiler and KSP warnings:** none.
- **Database:** version 2, migration 1 → 2 implemented and tested.
- **No Phase 5+ functionality** was added.

Files created: `app/src/test/.../data/local/MigrationTest.kt`, `app/src/test/.../ui/ProfileSelectionTest.kt`.

Files modified: `data/model/Models.kt`, `data/local/Entities.kt`, `data/local/NutriCartDatabase.kt`, `data/local/SeedData.kt`, `data/repository/ProfileRepository.kt`, `ui/Formatting.kt`, `ui/components/TagChip.kt`, `ui/theme/Dimens.kt`, `ui/screens/profilesetup/ProfileSetupScreen.kt`, `ui/screens/profilesetup/ProfileSetupViewModel.kt`, `ui/screens/profile/ProfileScreen.kt`, `res/values/strings.xml`, seven existing test files, `CLAUDE.md`, and this document.

## Final Revision — Multiple Custom Profile Values

Made after the Phase 4 Revision, before Phase 4 was committed. It changes only how custom (typed) allergies and health conditions are entered and stored. The listed options, "None of these", household size, Home, the shell, routing and logout are unchanged.

### Requirement

Users can select multiple relevant health conditions and allergies, including ones the lists do not cover. The PDF itself has no custom values at all; this is a product requirement from review.

### Problem identified

The "Other" chip allowed one typed value per section. A user with several uncommon allergies or conditions had to cram them into one 40-character entry.

### Design suggestion

Use a field with an **Add** button instead of an "Other" chip that has to be selected first.

### Decision

| | Before | Now |
|---|---|---|
| Entry point | "Other" chip that reveals one text field | Always-visible "Custom allergies" / "Custom health conditions" field with an Add button; no "Other" chip |
| Number of values | One per section | Up to 10 per section |
| Shown as | Text in the field | Chips with a cross, below the field; tapping one removes it |
| Stored as | One optional text per section | An ordered list per section |

Behaviour, the same for allergies and conditions:

- Typing a value and tapping **Add** (or the keyboard's Done key) adds it as a chip and clears the field, so the next one can be typed straight away. The keyboard stays open.
- Values are trimmed. A blank value does nothing, and Add is disabled while the field is empty.
- A repeat is not added, ignoring case and outer spaces ("Mango" and " mango " are the same). The field shows "Already added".
- Each value is limited to 40 characters and each section to 10 values; at the limit the field is disabled and says "You can add up to 10".
- Tapping a chip removes it immediately.
- **Typed but not added:** if text is left in the field, Save is disabled and the line above the button says 'Tap Add to keep "…", or clear the field'. A typed value is never dropped silently.
- **"None of these":** selecting it clears the listed conditions, the custom conditions and any text in the condition field. Adding a custom condition (or selecting a listed one) clears "None of these". A custom condition on its own satisfies the required answer.
- Allergies remain optional; leaving everything empty still means none.
- The Profile screen shows listed values first, then custom values, as wrapping chips. Long values are cut with an ellipsis.

### Data model and migration

- `ProfileEntity.customAllergy` and `customCondition` (single optional text) became `customAllergies` and `customConditions` (`List<String>`, in the order added).
- The lists are stored as JSON arrays through a Room type converter, so a value may contain commas, quotes or any other character. No new library: the JSON classes are part of Android.
- The repository cleans what it is given (trim, drop blanks, drop repeats ignoring case, 40 characters, 10 entries), so the rules hold even if a caller skips the form.
- **Database version 3**, with `MIGRATION_2_3` registered alongside `MIGRATION_1_2`. No destructive fallback.
- The migration rebuilds only the `profiles` table and copies each row in code. An existing single value becomes a one-item list (trimmed); a missing or blank one becomes an empty list. Region, household size, listed allergies, listed conditions and the account link are copied unchanged. Accounts and every other table are untouched.
- Upgrades from version 1 run both steps.

### Reason

- The data model matches reality: people can have several uncommon allergies or conditions.
- Adding is one action per value, with no mode to switch on first.
- Later matching and alert logic receives separate values instead of one free-text blob.
- It avoids another profile schema change later.

### Tests

170 unit tests in total, all passing (137 before; 33 added). None was removed.

| Test class | Change |
|---|---|
| `ProfileSelectionTest` (25 → 52) | For custom conditions and, separately, custom allergies: add one, two, several; remove one; remove all; exact duplicate; duplicate ignoring case and spaces; trimming; blank ignored; 40-character cap; 10-entry limit; typed-but-not-added blocks Save; persistence across save, reload, edit and logout/login. "None of these": clears listed and custom conditions and the typed text; is cleared by a listed condition and by an added custom one; stays when an add is rejected. Combinations: listed + custom conditions; listed + custom allergies; several of everything together across logout/login; values with commas, quotes and backslashes; repository cleaning. Second-account isolation with custom values. The earlier single-"Other" tests were replaced by these |
| `MigrationTest` (3 → 9) | Version 2 → 3: single custom condition → one-item list; single custom allergy → one-item list; missing and blank → empty lists; text with quotes and commas carried over intact; region, household size, listed selections and account links preserved; upgraded database stores several values and still deletes a profile with its account; empty database. The version 1 tests now run both migrations |
| `AppShellNavigationTest` (8, one rewritten) | On the real screens: no "Other" chip; Add disabled when empty; three allergies added; typed-but-not-added disables Save; case-insensitive repeat shows "Already added"; removing a chip; two conditions added; values appear on the Profile screen and again in Edit profile; "None of these" clears the custom conditions but not the allergies |

### Files changed

Main: `data/local/Entities.kt`, `data/local/Converters.kt`, `data/local/NutriCartDatabase.kt`, `data/repository/ProfileRepository.kt`, `ui/components/TagChip.kt` (new `RemovableChip`), `ui/screens/profilesetup/ProfileSetupScreen.kt`, `ui/screens/profilesetup/ProfileSetupViewModel.kt`, `ui/screens/profile/ProfileScreen.kt`, `res/values/strings.xml`.

Tests: `ui/ProfileSelectionTest.kt`, `data/local/MigrationTest.kt`, `ui/AppShellNavigationTest.kt`, `ui/ProfileViewModelsTest.kt`.

Docs: this document and `CLAUDE.md`.

No files were created and no dependency changed.

### Verification

Command: `gradlew.bat clean assembleDebug testDebugUnitTest lintDebug`

- **Build:** `BUILD SUCCESSFUL in 1m 38s`, 58 actionable tasks: 58 executed.
- **Unit tests:** 170 total, 170 passed, 0 failed, 0 skipped. `MigrationTest`: 9 of 9 passed.
- **Lint:** 0 errors, 17 warnings, the same 17 as before. No new warnings.
- **Compiler and KSP warnings:** none in the final build.

### On-device check

The build was installed on the attached phone and opened once.

- **Seen:** the app started without a crash on a database that already held a version 2 profile, so the 2 → 3 migration ran on real data. The Profile screen showed the profile's earlier single custom values as chips ("Egg plant" under Allergies, "Insulin resistance" under Health conditions), with region and household size intact. The screen was readable in dark mode and the bottom bar sat clear of the system navigation bar.
- **Not done:** the scripted smoke test (adding and removing custom values, Save, reopening Edit profile, logout and login, keyboard overlap, chip wrapping). The phone was being used at the same time, so the test was stopped after the first screen to avoid sending taps and text into whatever was on screen. Logout and login would also have needed the account's password.

### Remaining limitations

1. **The custom-entry UI has not been looked at on a device**: the Add button beside the field, the keyboard over it, chip wrapping with long values, and Save with the keyboard open.
2. **Custom values are display-only.** No rule can match free text, so later allergy and condition matching can use only the listed options.
3. **A custom value can repeat a listed option** (typing "Fish" while the Fish chip exists); duplicates are checked only among custom values.
4. **Limits of 40 characters and 10 values per section** are fixed in code.
5. **Removing a chip has no undo**; the value has to be typed again.

## Verification (before the revision)

Command (run from the project root):

```
gradlew.bat clean assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 1m 39s`, 58 actionable tasks: 58 executed.
- **Unit tests:** 108 total, 108 passed, 0 failed, 0 skipped. The suite was also run three more times with `--rerun`; all passed each time.
- **Lint:** 0 errors, 17 warnings, the same 17 as after Phase 3. No new warnings.
- **Compiler and KSP warnings:** none.
- **Other output:** JDK 26 warnings from Robolectric during tests, as before.
- **Fixed during the phase:** one lint error (`NonObservableLocale` in the Profile screen's date format) and unescaped apostrophes in three strings; both were caught by the build before this report.
- **Not installed:** the phone had disconnected by the time the build finished, so this build is not on it yet.
- **Not run:** the instrumented test.
