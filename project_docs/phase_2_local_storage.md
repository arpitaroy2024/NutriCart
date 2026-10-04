# NutriCart — Phase 2: Local Storage & Authentication Foundation

**Date:** 2026-10-04
**Phase:** Phase 2 — Local storage and authentication domain layer
**Status:** Implemented; build, 42 unit tests and lint pass. Not yet run on a device (see Verification).

Everything in this phase is on-device. There is no Firebase, backend, network call or API.

No screen was built or changed. Nothing in the UI calls the new code yet, so the running app behaves as it did after Phase 1.

## 1. Architecture

```
UI (later phases)
   │
   ▼
Repositories (interfaces + Local… implementations)        data/repository/
   │                         │
   ▼                         ▼
Room DAOs                 SettingsStore (DataStore)        data/local/, data/
   │
   ▼
NutriCartDatabase (SQLite file "nutricart.db")
```

- `AppContainer` builds the database, the settings store and the five repositories once, lazily. `NutriCartApp` creates the container in `onCreate`. There is no dependency-injection library.
- **Account ownership is enforced in two places.** Every DAO query on user-owned data takes the owning `accountId`. The repositories for user-owned data never accept an account id from the caller; they read it from the session in `SettingsStore`. A screen therefore cannot ask for another account's data.
- While nobody is logged in, the `observe…` flows emit `null` (or an empty list) and the suspend functions throw `IllegalStateException`.

## 2. Room entities

Database: `NutriCartDatabase`, version 1, file `nutricart.db`.

| Table | Entity | Columns | Keys and relationships |
|---|---|---|---|
| `accounts` | `AccountEntity` | id, name, email, passwordHash, passwordSalt, createdAt | PK id (auto); unique index on email |
| `profiles` | `ProfileEntity` | accountId, region, householdSize, allergies, condition | PK accountId, FK → accounts (cascade delete); one row per account |
| `budgets` | `BudgetEntity` | id, accountId, amount, month (`yyyy-MM`), listId, createdAt | FK → accounts (cascade); FK → grocery_lists (set null) |
| `grocery_lists` | `GroceryListEntity` | id, accountId, budget, createdAt | FK → accounts (cascade) |
| `list_items` | `ListItemEntity` | id, listId, catalogItemId, quantity, unitPrice, bought, alertOverridden | FK → grocery_lists (cascade); FK → catalog_items |
| `catalog_items` | `CatalogItemEntity` | id, name, category, unit, gramsPerUnit, nutrientTag, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, ironMgPer100g, allergens, flaggedConditions | PK id (fixed by the seed) |
| `region_prices` | `RegionPriceEntity` | catalogItemId, region, price, updatedAt | PK (catalogItemId, region); FK → catalog_items (cascade) |

- Amounts and prices are whole Tk.
- `allergies`, `allergens` and `flaggedConditions` are enum sets stored as comma-separated names (`Converters`). Single enums are stored by name.
- `ListItemWithCatalog` is a read model joining a list item to its catalog item.
- Enums and the region list are in `data/model/Models.kt`: `Allergen` (Peanuts, Shellfish, Dairy, Eggs, Gluten), `HealthCondition` (None, Diabetes, Hypertension), `FoodCategory`, `NutrientTag`, and `Regions.all` (Bangladesh's eight divisions).

## 3. DAOs

| DAO | Operations |
|---|---|
| `AccountDao` | insert, findByEmail, observeById |
| `ProfileDao` | exists, get, observe, upsert |
| `BudgetDao` | latest, observeLatest |
| `GroceryListDao` | insertGenerated (list + items + budget in one transaction), get, observe, latest, observeLatest |
| `ItemDao` | getForList, observeForList, setBought, setAlertOverridden, applyEdits (adds, quantity changes and removals in one transaction) |
| `CatalogDao` | itemCount, insertSeed (one transaction), getAll, getById, pricesForRegion, price |

## 4. DataStore settings

`SettingsStore` wraps a Preferences DataStore file named `settings`.

| Key | Type | Meaning | Default |
|---|---|---|---|
| `onboarding_complete` | Boolean | Onboarding was completed or skipped | false |
| `logged_in_account_id` | Long | The session: id of the logged-in account | absent (logged out) |

Both survive app restarts. Logging out removes only `logged_in_account_id`.

## 5. Authentication design

`AccountRepository` (implemented by `LocalAccountRepository`):

| Function | Behaviour |
|---|---|
| `register(name, email, password)` | Validates, checks the email is free, hashes the password, inserts the account, and **logs it in**. Returns `Success(account)` or `Failure(errors)`. |
| `login(email, password)` | Looks the account up by email and verifies the password. Returns `Success(account)` or `InvalidCredentials`. |
| `logout()` | Clears the session. Accounts, profiles and grocery history stay on the device. |
| `currentAccount` | Flow of the logged-in account, or null. |

- **Validation** (`AccountValidator`): name not blank, email in a valid format, password at least 6 characters. All problems are reported together as a set of `RegistrationError` (`NameBlank`, `EmailInvalid`, `EmailTaken`, `PasswordTooShort`), so Phase 3 can show each on its own field.
- **Email handling:** trimmed and lower-cased before storing and before lookup, so `Arpita@Example.com` and `arpita@example.com` are the same account. The database also has a unique index on email.
- **Generic failure:** an unknown email and a wrong password return the same `InvalidCredentials` value. An unknown email still performs a hash, so the two cases take about the same time.
- **No password material leaves the repository.** The UI-facing `Account` model has id, name, email and createdAt only.

## 6. Password hashing

`PasswordHasher`:

| Setting | Value |
|---|---|
| Algorithm | `PBKDF2WithHmacSHA1` (the SHA-256 variant needs API 26; min SDK is 24) |
| Iterations | 300,000 |
| Salt | 16 random bytes from `SecureRandom`, new for every account |
| Derived key | 160 bits |
| Storage | Hash and salt as hex strings in `accounts.passwordHash` and `accounts.passwordSalt` |
| Comparison | Constant-time (`MessageDigest.isEqual`) |

Plaintext passwords are never stored or logged. Hashing runs off the main thread (`Dispatchers.Default`).

## 7. Repository structure

All in `data/repository/`, each file holding the interface and its `Local…` implementation.

| Repository | Scope | Functions |
|---|---|---|
| `AccountRepository` | Accounts and session | register, login, logout, currentAccount |
| `ProfileRepository` | Logged-in account | observe, get, exists, save (rejects a blank region or a household outside 1–12) |
| `BudgetRepository` | Logged-in account | latest, observeLatest |
| `GroceryListRepository` | Logged-in account | createList, observeList, latestList, observeLatestList, observeItems, getItems, setBought, overrideAlert, saveEdits |
| `CatalogRepository` | Shared by all accounts | items, item, prices(region), price(itemId, region) |

Repositories return data and results only; they hold no UI state, strings or navigation.

## 8. Seed data

**All catalog data is demo data. No price is a real or current market price.**

- `data/local/SeedData.kt` defines the `CatalogSeed` interface and `DemoCatalogSeed`, which holds 40 Bangladesh-oriented items: 4 grains, 13 protein, 10 vegetables, 3 fruit, 2 dairy, 5 oils, 3 pantry.
- Each item has a name, category, unit, grams per unit, per-100 g calories, protein, carbohydrate, fat and iron, a dominant-nutrient tag, allergens, and the health conditions it is flagged for.
- **Prices:** one price per item per region (320 rows). The Rangpur figure reuses the number drawn in the PDF mockups where one exists (lentils, brown rice, eggs, spinach, sugar and the four oils); the rest are invented. Other regions are the Rangpur figure scaled by a made-up percentage.
- **Nutrition:** rounded approximations, not checked against a food composition table.
- **Marked as demo:** every seeded price has `updatedAt = null`. Later screens must show such prices as demo prices and must not show an "updated … ago" line for them.
- **Loading:** `LocalCatalogRepository` fills the catalog tables the first time the catalog is read, if they are empty.
- **Replacing it:** pass another `CatalogSeed` to `LocalCatalogRepository` in `AppContainer`, or edit `DemoCatalogSeed`. Existing installs keep their old catalog until app data is cleared.

## 9. Tests

42 unit tests, all passing. The database and settings tests run on Robolectric against a real in-memory Room database and a real DataStore file, with no fakes.

| Test class | Tests | Covers |
|---|---|---|
| `PasswordHasherTest` | 7 | Random 16-byte salt; deterministic hash; different salt gives a different hash; hash does not contain the password; correct password verifies; wrong password and wrong salt are rejected |
| `AccountValidatorTest` | 7 | Blank name, invalid and valid emails, password length boundary, all errors reported together, email normalisation |
| `SettingsStoreTest` | 4 | Defaults; login session survives a restart; onboarding flag survives a restart; logout clears the session only |
| `AccountRepositoryTest` | 10 | Registration logs in; only a salted hash is stored; name and email are normalised; duplicate email rejected ignoring case; invalid input creates nothing; same password gives different hashes; correct login; wrong password; identical failure for unknown email and wrong password; logout keeps the account |
| `AccountIsolationTest` | 4 | Profiles are separate and restored after login; another account cannot see lists, items or budgets; another account cannot change someone else's items; nothing is reachable while logged out |
| `UserDataRepositoryTest` | 9 | Profile overwrite and validation; list, items and budget written together; a failed create writes nothing; edits applied together; bought and override flags; catalog seeded once with 40 items priced in all 8 regions; allergen and condition flags; demo prices carry no update date |
| `ExampleUnitTest` | 1 | Existing template test, kept |

## 10. Files created

Main (`app/src/main/java/com/example/nutricart/`):

- `AppContainer.kt`
- `data/SettingsStore.kt`
- `data/model/Models.kt`
- `data/auth/PasswordHasher.kt`
- `data/auth/AccountValidator.kt`
- `data/local/Entities.kt`
- `data/local/Converters.kt`
- `data/local/Daos.kt`
- `data/local/NutriCartDatabase.kt`
- `data/local/SeedData.kt`
- `data/repository/Session.kt`
- `data/repository/AccountRepository.kt`
- `data/repository/ProfileRepository.kt`
- `data/repository/BudgetRepository.kt`
- `data/repository/GroceryListRepository.kt`
- `data/repository/CatalogRepository.kt`

Tests (`app/src/test/`):

- `java/com/example/nutricart/data/TestEnvironment.kt`
- `java/com/example/nutricart/data/SettingsStoreTest.kt`
- `java/com/example/nutricart/data/auth/PasswordHasherTest.kt`
- `java/com/example/nutricart/data/auth/AccountValidatorTest.kt`
- `java/com/example/nutricart/data/repository/AccountRepositoryTest.kt`
- `java/com/example/nutricart/data/repository/AccountIsolationTest.kt`
- `java/com/example/nutricart/data/repository/UserDataRepositoryTest.kt`
- `resources/robolectric.properties`

Docs:

- `project_docs/phase_2_local_storage.md`

## 11. Files modified

- `gradle/libs.versions.toml` — new versions, libraries and the KSP plugin.
- `build.gradle.kts` — KSP plugin declared.
- `app/build.gradle.kts` — KSP plugin applied, new dependencies, Android resources enabled for unit tests.
- `app/src/main/java/com/example/nutricart/NutriCartApp.kt` — creates the `AppContainer`.
- `project_docs/implementation_plan.md` — status line.
- `CLAUDE.md` — architecture notes.

## 12. Dependencies

| Dependency | Version | Scope | Why |
|---|---|---|---|
| `com.google.devtools.ksp` (plugin) | 2.3.12 | build | Runs the Room compiler |
| `androidx.room:room-runtime` | 2.8.5 | implementation | Database |
| `androidx.room:room-compiler` | 2.8.5 | ksp | Generates DAO code |
| `androidx.datastore:datastore-preferences` | 1.2.1 | implementation | Settings |
| `org.robolectric:robolectric` | 4.17 | testImplementation | Runs Room and DataStore in JVM unit tests |
| `androidx.test:core` | 1.7.0 | testImplementation | Application context in tests |

All build with Kotlin 2.2.10, AGP 9.3.1, Gradle 9.5.0 and SDK 37. Robolectric is pinned to SDK 35 in `robolectric.properties`.

## 13. Decisions and deviations from the implementation plan

| Item | Plan | Implemented | Why |
|---|---|---|---|
| Repositories | Only an auth repository; ViewModels use DAOs directly | Five repositories | Requested for Phase 2 |
| Auth repository name and place | `data/auth/AuthRepository.kt` | `data/repository/AccountRepository.kt` | Kept with the other repositories |
| `list_items.unitPrice` | Not in the plan | Added | A list keeps the prices it was generated with. FLOW-16 says a region change "invalidates the current list's pricing" until it is regenerated, and the "spent" figure needs a stable price. |
| `region_prices.updatedAt` | Not null | Nullable | Null marks a demo price |
| `catalog_items.gramsPerUnit` | Not in the plan | Added | Needed to turn a quantity in kg, L or pieces into nutrition from per-100 g values |
| Micronutrients | Not specified | Iron only | Iron is the only micronutrient the PDFs show |
| Food categories | Not specified | Protein, Grains, Veg, Fruit, Dairy, Oils, Pantry | The PDFs draw three chips but also show oil, sugar and similar items |
| Health-condition flags | Rules "for the condition", undefined | White rice, white sugar and jaggery flagged for diabetes; dried fish and chanachur for hypertension | Demo rule data so Phase 8 has something to match |
| Seeding | Not specified | On first catalog read, if empty | Keeps app start-up free of database work |
| Schema export | Not specified | Off | No migrations exist yet at version 1 |
| RDA table | Listed under storage "in code" | Not created | It belongs with the nutrition calculator (Phase 7) |

## 14. Left for later phases

- Restyled Login, Create account, onboarding and profile setup screens, and their ViewModels (Phases 3 and 4).
- Splash routing on onboarding, session and profile (Phase 3).
- Budget validation (500 to 999999) and Home (Phase 4).
- Grocery generation, editing, nutrition scoring and allergy matching (Phases 5 to 8).
- The RDA table (Phase 7).
- Database migrations, once the schema changes after release.

## Open points

1. **Login speed on a real phone is unmeasured.** 300,000 PBKDF2 iterations took a fraction of a second per hash in the JVM tests. It should be timed on a low-end device in Phase 3 and the count lowered if login feels slow. Changing the count later invalidates existing accounts' passwords, so it should be settled before real use.
2. **Android backup includes the account database.** The manifest has `allowBackup="true"` with the template backup rules, so `nutricart.db` (including password hashes and salts) and the settings file can be copied to the user's cloud backup or a new device. Nothing was changed here; decide whether to exclude them or turn backup off.
3. **No password reset.** As planned, a forgotten password cannot be recovered.

## Verification

Command (run from the project root):

```
gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 59s`, 57 actionable tasks: 26 executed, 31 up-to-date.
- **Unit tests:** 42 tests, 0 failures, 0 errors, 0 skipped. Run three more times with `--rerun`; all passed each time.
- **Lint:** 0 errors, 16 warnings, the same 16 as after Phase 1 (newer-version notices, unused template colours, one redundant manifest label). None are in Phase 2 files.
- **Compiler and KSP warnings:** none.
- **Build message:** "Unable to strip the following libraries … libandroidx.graphics.path.so, libdatastore_shared_counter.so". Harmless for debug builds; the second library is new with DataStore.
- **Not done:** the app was not installed or launched, because no device was attached. The only runtime change is `NutriCartApp.onCreate` creating the container.
- **Not run:** the instrumented test (`connectedAndroidTest`).

Two test problems were found and fixed during the phase, both in test code only: JUnit rejected test methods that returned a value, and `SettingsStoreTest` failed on a plain JVM on Windows because DataStore's file replace depends on the Android SDK level, so it now runs on Robolectric.
