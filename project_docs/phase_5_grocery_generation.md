# NutriCart — Phase 5: Grocery Generation

**Date:** 2026-10-05
**Phase:** Phase 5 — Grocery generation, processing screen, generated list
**Status:** Implemented; build, 224 unit tests and lint pass. **Not seen on a device** (see Known limitations).

## 1. Phase objective

Turn the monthly budget typed on Home into a saved grocery list for the logged-in account's household, and show that list.

**What "AI" means here.** The generator is not an AI model and does not call one. It is a fixed set of rules that runs on the phone: the same inputs always give the same list, nothing is random, and nothing leaves the device. There is no network, backend or external API anywhere in the app.

Nutrition scoring (`NutritionCalculator`) belongs to Phase 7 and conflict detection (`AllergyMatcher`) to Phase 8. Neither was built.

## 2. Implemented screens

| ID | Screen | Route | Files |
|---|---|---|---|
| SCR-10 | Building your list | `generate?budget={amount}` | `ui/screens/generate/GenerateScreen.kt`, `GenerateViewModel.kt` |
| SCR-05 | Grocery list, pushed after generation | `list/{listId}` | `ui/screens/grocerylist/GroceryListScreen.kt`, `GroceryListViewModel.kt` |
| SCR-05 | Grocery list, as the List tab | `list` | Same composable; shows the latest list |
| SCR-04 | Home (updated) | `home` | Generate now starts generation; a card shows the latest list |

The List tab's "Coming soon" screen is replaced. The Nutrition tab is unchanged.

## 3. Generator architecture

| Part | Responsibility |
|---|---|
| `domain/GroceryGenerator.kt` | Chooses items and quantities. A pure function with no Android, database or UI dependencies |
| `domain/ProfileConflicts.kt` | One small test: is this item tagged with a listed allergy or condition |
| `data/local/SeedData.kt` → `DemoCatalogSeed.basket` | The basket template: which items make up a month's shopping and how much per person. Demo data, like the prices |
| `CatalogRepository` | Supplies the catalog, the region's prices and the basket template |
| `GroceryListRepository.createList` (Phase 2) | Saves the list, its items and its budget row in one transaction |
| `GenerateViewModel` | Reads the profile and prices, runs the generator off the main thread, saves the result, reports progress |
| Composables | Render state only |

The generator takes a `GenerationRequest` (budget, household size, catalog, prices for the region, basket template, listed allergies, listed conditions) and returns either `Success(items)` or `Failure(reason, minimumBudget)`.

## 4. Generation algorithm

1. **Usable items.** Only catalog items with a price above zero in the profile's region are considered. Items tagged with one of the profile's listed allergies, or flagged for one of its listed conditions, are left out.
2. **Fill the basket slots.** The template has 31 slots in four priority groups. Each slot names one or more items, the usual choice first, and a per-person monthly quantity. A slot takes its first usable item that no earlier slot has taken; a slot with no usable item is skipped.
3. **Core staples first** (rice, lentils, eggs, potato, onion). If the budget covers them in full, they are bought in full. If not, they are all reduced in the same proportion, any that fell to zero gets one unit if it can be afforded, and what is left tops them up in order.
4. **Everyday items, then variety**, in template order, each at its target quantity or as much as the remaining money buys.
5. **Better choices before more.** Only when everything above is covered in full: the staple grain may be upgraded (white rice to brown rice) if the difference fits, then extras are added (liver, peanuts, oats, mung dal, red amaranth, beef, shrimp, hilsa).
6. **Result check.** The list must contain something from every food group the core staples cover (grains, protein, vegetables). If it cannot, the result is "budget too small" with the smallest budget that would work.

Quantities never exceed the template's target, so a large budget is not spent for its own sake.

## 5. Budget rules

- Home's existing validation is the only budget validation: digits only, at most 7 digits, Tk 500 to Tk 999,999. The generator does not repeat it.
- **The total never exceeds the budget.** A test checks this for every region, six household sizes and thirteen budgets from 500 to 999,999.
- **3% of the budget is always left unspent**, so the list is not built to the last taka.
- At the minimum budget of Tk 500 the demo data still gives grains, protein and vegetables for every household size from 1 to 20.
- An example from the tests: Tk 12,000 for four people in Rangpur Division, with diabetes and a peanut allergy listed, gives 22 items for Tk 11,489 (95% of the budget).

## 6. Household scaling

- The saved household size (1 to 20) is used.
- Up to four people count in full. Each person beyond four counts as 0.85, on the assumption that a large household shares staples and wastes less: 12 people count as 10.8 and 20 as 17.6.
- Each quantity is the per-person amount times that figure, rounded, and never less than 1.
- With the same budget a larger household gets fewer kinds of food, because staples are covered first.

## 7. Region-price behaviour

- Every item's price is the `region_prices` row for the profile's region. The price is copied onto the list item when the list is created, so an old list keeps the prices it was built with.
- An item with no price in the region, or a price of zero or less, is left out. No price is ever invented.
- A region with no prices at all fails with "There are no prices for … yet."
- **All prices are demo data, not market prices.** The list's summary says "demo prices, not market prices".

## 8. Persistence behaviour

- **No schema change.** The Phase 2 tables already held everything needed; the database stays at version 3 and no migration was added.
- A successful generation writes one `grocery_lists` row (account, budget, time), its `list_items` (catalog item, quantity, unit price, not bought, no alert override) and one `budgets` row linked to the list, in a single transaction.
- Nothing is written until the generator has succeeded. A failed or cancelled generation leaves no list, no items and no budget row; a test forces the write to fail part-way and checks that nothing remains.
- **Generating again adds a new list.** Earlier lists stay stored as history. Home and the List tab treat the newest as the current list. There is no history screen, and nothing is deleted automatically.

## 9. Account isolation

Unchanged from Phase 2 and tested again with generated lists: lists, items and budgets are read and written only for the logged-in account. A second account sees no list on Home, on the List tab or by list id, generates its own, and the first account still sees only its own after logging back in.

## 10. SCR-10 behaviour

- Title "Building your list", subtitle "Usually takes a few seconds", a progress ring with the percentage, four steps, chips for the budget and the household, and Cancel.
- **Steps:** Reading regional prices → Balancing food groups → Fitting to your budget → Applying your allergies and conditions.
- **What is real:** step 1 is ticked when the profile, catalog and regional prices have been read; steps 2 to 4 when the generator has returned; the list opens when it has been saved. The generator runs on a background thread.
- **Pacing:** the real work takes milliseconds, so each finished step is held for 350ms before the next is ticked; otherwise the screen would flash past unread. This is display pacing, not simulated work, and adds about 1.4 seconds.
- **Cancel and system Back** stop generation and return to Home. Nothing has been written at that point. The typed budget is still in the field on Home. Once the save has started (a few milliseconds), Cancel is disabled.
- **Failure** stays on this screen with a message: budget too small (with the amount that would work), no prices for the region, nothing in the catalog fits the profile, or "Something went wrong. Nothing was saved." Only the last offers "Try again"; all offer "Back to Home".
- It is a destination of its own, so it survives rotation. It is removed from the back stack when the list opens.

## 11. SCR-05 behaviour

- App bar "Grocery list", with a back chevron when pushed after generation and none on the List tab.
- **Search** filters by name as you type, ignoring case.
- **Category chips:** "All" plus only the categories the list contains, in a horizontally scrolling row. Single-select.
- Search and category combine. They change only what is shown; the stored list and the totals are unaffected. Changing either scrolls the list back to the top.
- **Item card:** round checkbox, name (up to two lines), quantity and unit, line price, and the item's nutrient tag from the catalog.
- **Bought:** tapping the checkbox stores the item as bought and dims it. It stays in the list and in the total. Tapping again undoes it.
- **Docked summary:** estimated total, remaining budget (red if it were ever negative), the budget rail, and "N% of Tk … used · demo prices, not market prices". It sits below the list, so it never covers an item.
- **Edit list** shows "Editing isn't available yet. It is built in Phase 6." Nothing else happens.
- **No list yet** (List tab only): "No grocery list yet. Enter your monthly budget on Home to build one."
- Not present: tapping a card, swipe actions, the nutrition icon, alerts.

### Home

- Generate is disabled while the field is blank, shows the budget error for an out-of-range amount, and otherwise opens SCR-10.
- Once a list exists, the "No grocery list yet" card is replaced by "This month so far": item count, total of budget, the rail, percent used and "View list". Tapping it opens the List tab.
- The nutrition score and alert count from the PDF's summary are not shown; they need Phases 7 and 8.

## 12. Tests

224 unit tests in total, all passing; 54 are new. None was removed.

| Test class | Tests | Covers |
|---|---|---|
| `GroceryGeneratorTest` (new) | 28 | Budget: non-empty within budget; total never over budget across 8 regions × 6 household sizes × 13 budgets; 3% left; Tk 500 still gives grains, protein and vegetables for 1, 4, 12 and 20 people; staples before variety; larger budget buys more kinds but not endlessly more; the grain upgrade; every category covered. Household: quantities grow from 1 to 4 to 12 to 20; exact amounts for four; bounded scaling; minimum of one. Region: prices come from the region; two regions cost differently; missing, zero and negative prices leave the item out. Determinism: identical inputs repeat exactly; catalog order does not matter; no item twice. Profile: listed allergies and conditions leave items out. Failures: empty catalog, no prices, nothing usable, budget too small with the amount needed |
| `GroceryGenerationTest` (new) | 24 | Home requests generation only for a valid budget and shows the list summary afterwards. Processing: progress state, completion, cancel leaves nothing, cancel after saving changes nothing, no profile, no prices, write failure leaves nothing partial, retry. Persistence: list, items, quantity, unit price, budget, account. Uses the profile's household, region and restrictions. Generating again adds a list and keeps the old one. Survives logout and login. Account isolation in both directions. List screen: items and totals, List tab, case-insensitive search, category filter and All, only present categories, bought stored and kept in the total, Edit is only a notice |
| `AppShellNavigationTest` (8 → 10) | +2, one replaced | On the real screens at 393 × 832dp: Home → Generate → processing → list, back to Home, List tab; search, filter, bought, Edit notice; Cancel returns Home with nothing saved; List tab without a list |
| `ProfileViewModelsTest` | updated | Home's Generate now requests generation instead of raising the Phase 4 "not available" notice |

Two defects were found by the tests and fixed before this report: the list kept its old scroll position after a search or filter change, hiding the first results; and the "editing isn't available" message cancelled itself before it could appear.

## 13. PDF vs suggestion vs decision

| PDF | Suggestion | Decision |
|---|---|---|
| "AI processing" that solves prices, nutrition targets and budget together | The PDF defines no algorithm and no service. Use an on-device rule-based generator that is deterministic and testable | **Implemented** as described in section 4. It is not presented as a learned model |
| Step labels "Matching nutrition targets" and "Checking allergies" | Those name engines that do not exist until Phases 7 and 8. Label the steps for what this phase does | **"Balancing food groups"** and **"Applying your allergies and conditions"** |
| Conflicting items appear in the list and are flagged afterwards (peanut oil for a peanut allergy in the SCR-08 mockup) | A generator that knows the allergy should not choose the allergen | **Items tagged with a listed allergy or condition are not chosen.** Phase 8 alerts will then mainly concern items the user adds. This is a filter on catalog tags, not a safety check: typed allergies and conditions cannot be matched, and the UI makes no "safe" claim |
| Ring label "Analysing" | Nothing is analysed | **"Building"** |
| Item name in title/20sp on a 76dp card | Names such as "Whole wheat flour (Atta)" do not fit beside the price at 20sp | **body/16sp Bold, up to two lines**; the card is at least 76dp and grows |
| Four fixed category chips: All, Protein, Grains, Veg | The catalog has seven categories; a chip for an absent category filters to nothing | **"All" plus the categories present in the list**, scrolling horizontally |
| Docked total card over the list | A sheet that overlaps hides the last items | **Docked below the list**, never over it |
| Summary shows "90% of budget used" | Say what the percentage is of, and that prices are not real | **"N% of Tk … used · demo prices, not market prices"** |
| Swipe left to delete, swipe right to mark bought | Delete is Phase 6; a hidden swipe duplicates the checkbox | **Checkbox only** in this phase |
| Edit list opens SCR-06 | Not built yet | **A message saying so** |
| Generate button "shows a spinner during generation" | The flow document makes processing a destination | **The processing screen replaces the spinner** |
| Empty result "suggests raising the budget by a named amount" | — | **Implemented** on the processing screen's failure state |
| Home summary with items, nutri score and alerts | Two of the three figures do not exist yet | **Items, total of budget and percent used** |
| Rows "stagger in at 40ms intervals"; crossfade | Polish | **Deferred** to the polish phase; the 200ms fade is used |

## 14. Known limitations

1. **Not seen on a device.** The build was installed on the phone, but the phone was in use, so the app was not opened or driven. The 24-step smoke test was not done. The navigation tests run the real screens at the PDF's 393 × 832dp, which proves they work, not how they look.
2. **Everything about the food is demo data:** prices, per-person quantities and the nutrition values. The lists look plausible; they are not dietary advice.
3. **A small budget for a large household gives a short list** with no indication of how much of the month it covers.
4. **Typed (custom) allergies and conditions have no effect** on generation; only the listed options can be matched.
5. **The basket template is tied to the demo catalog's item ids.** A replacement catalog needs its own template.
6. **Profile changes do not prompt regeneration.** The PDF's warning when region or household size changes is still not built; the old list simply stays until Generate is used again.
7. **Older lists cannot be viewed**; only the latest is reachable.
8. **The "last month" card on Home is still not built.**
9. **The summary bar is about 215dp tall**, which leaves room for roughly six items at 393 × 832dp and fewer on smaller screens.

## 15. Deferred to later phases

| Phase | Not built here |
|---|---|
| 6 | Editing quantities, adding and deleting items, undo, the item picker, item details (SCR-11), swipe actions |
| 7 | `NutritionCalculator`, the nutrition score, deficiencies, recommendations, SCR-07, the Nutrition tab, the nutrition icon on the list |
| 8 | `AllergyMatcher`, alert cards, replacements, "Keep anyway", SCR-08, SCR-12, the alerts count on Home |
| 9 | Slide, crossfade and staggered-row transitions |

No network, backend, Firebase or LLM integration was added.

## Files

**Created**

- `app/src/main/java/com/example/nutricart/domain/GroceryGenerator.kt`
- `app/src/main/java/com/example/nutricart/domain/ProfileConflicts.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/generate/GenerateScreen.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/generate/GenerateViewModel.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/grocerylist/GroceryListScreen.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/grocerylist/GroceryListViewModel.kt`
- `app/src/test/java/com/example/nutricart/domain/GroceryGeneratorTest.kt`
- `app/src/test/java/com/example/nutricart/ui/GroceryGenerationTest.kt`
- `project_docs/phase_5_grocery_generation.md`

**Modified**

- `data/local/SeedData.kt` — the basket template
- `data/repository/CatalogRepository.kt` — exposes the template
- `navigation/NutriCartNavHost.kt`, `navigation/Routes.kt` — the two new routes; the List tab
- `ui/AppViewModelFactory.kt`
- `ui/Formatting.kt` — taka formatting, category and nutrient labels
- `ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt`
- `res/values/strings.xml`
- Tests: `AppShellNavigationTest.kt`, `ProfileViewModelsTest.kt`, `ProfileSelectionTest.kt`
- `project_docs/implementation_plan.md`, `CLAUDE.md`

**Deleted:** none. `ui/screens/upcoming/UpcomingScreen.kt` is still used by the Nutrition tab.

**Dependencies:** none added or changed.

## Verification

Command (run from the project root):

```
gradlew.bat clean assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 2m 21s`, 58 actionable tasks: 57 executed, 1 up-to-date.
- **Unit tests:** 224 total, 224 passed, 0 failed, 0 skipped. Also run three more times with `--rerun`; all passed each time.
- **Lint:** 0 errors, 12 warnings: 7 unused template colours, 3 dependency-version notices, 1 redundant manifest label, 1 plurals suggestion. No new warning. The count is lower than Phase 4's 17 only because lint reported five fewer version notices in this run.
- **Compiler and KSP warnings:** none.
- **Installed** on the attached phone; not opened.
