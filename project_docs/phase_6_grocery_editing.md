# NutriCart — Phase 6: Grocery List Editing & Management

**Date:** 2026-10-05
**Phase:** Phase 6 — Editing a saved grocery list
**Status:** Implemented; build, 256 unit tests and lint pass. **Not seen on a device** (see Known limitations).

## 1. Objective

Make the generated list editable and keep every edit: change quantities, remove items (with undo), add items from the catalog, and see the cost against the budget at all times, including when the list goes over it.

Nothing in this phase scores nutrition, checks allergies or health conditions, or suggests replacements. Those are Phases 7 and 8.

## 2. Implemented functionality

| Function | Where |
|---|---|
| Change an item's quantity | Editor: minus and plus on each card |
| Remove an item | Editor: the cross on each card |
| Undo a removal | Editor: "… removed — Undo" for five seconds |
| Add an item from the catalog | Item picker, opened from the editor |
| Search the catalog | Item picker |
| Filter the catalog by category | Item picker |
| Total, remaining or over-budget amount, item count, bought count | Under the list and under the editor; a one-line total in the picker |
| Mark bought / not bought | List screen (unchanged from Phase 5) |
| Empty list with a way to add | List screen and editor |

New screens and routes:

| Screen | Route | Files |
|---|---|---|
| Edit list (SCR-06) | `list/{listId}/edit` | `ui/screens/editlist/EditListScreen.kt`, `EditListViewModel.kt` |
| Add groceries (the PDF's item picker) | `list/{listId}/add` | `ui/screens/editlist/AddItemsScreen.kt`, `AddItemsViewModel.kt` |

"Edit list" on the list screen now opens the editor, from both the List tab and the list pushed after generation.

## 3. Architecture and data flow

```
Composable  ──tap──>  ViewModel  ──>  GroceryListRepository  ──>  ItemDao  ──>  Room
     ▲                                                                           │
     └──────────────  state  <── Flow of the list and its items  <───────────────┘
```

- **Every edit is written to the database when it is made.** There is no draft held in memory and no Save step.
- The screens show what the database holds: each ViewModel observes the list and its items as a Flow, so the editor, the picker, the list screen and Home's card all update from the same rows.
- **Rules live below the UI.** Quantity limits and "no duplicate rows" are applied in the DAO; ownership is checked in the repository; totals are computed by `domain/ListTotals`.
- The editor and the picker each receive the **list id from their route** and work on that list only. Neither uses "the latest list".

New and changed code below the UI:

| Part | Change |
|---|---|
| `domain/ListRules.kt` | `ListRules` (quantity 1 to 999) and `ListTotals` (total, remaining, over-budget amount, percent used) |
| `ItemDao` | `changeQuantity`, `getOwned`, `deleteOwned`, `removeOwned`, `findInList`, `addOrIncrease` |
| `GroceryListRepository` | `changeQuantity`, `removeItem`, `restoreItem`, `addItem` and `AddItemResult` |

## 4. Database changes

**None.** The Phase 2 `list_items` table already had quantity, unit price, bought and the list link. The database stays at version 3; no migration was needed. Lists generated in Phase 5 open and edit without any conversion.

## 5. UI/UX decisions

- **A separate editor, not controls on the shopping list.** The list screen stays a clean checklist for use in the shop; steppers and remove buttons are one tap away.
- **Auto-save.** Leaving the editor by Back, Done or the system gesture never loses anything, so there is no "unsaved changes" dialog.
- **Undo instead of confirmation** for removal.
- **A full-screen picker** for adding, so the search field and the keyboard have room.
- **The summary is shared** by the list and the editor (`BudgetSummary`), so the budget reads the same everywhere. It was kept compact: two figures, the rail and one caption.
- **Over budget is stated plainly**, in the danger colour, with the amount.
- **Long names** wrap to two lines on every card.
- **Content descriptions name the item** ("Increase Lentils (Masoor)", "Remove Eggs", "Add Tomato"), so the many identical buttons are distinguishable to a screen reader.

## 6. PDF requirement vs suggestion vs final decision

| Area | PDF / existing requirement | Suggested improvement | Final decision |
|---|---|---|---|
| Saving | Edits are a draft; "Save changes" writes them in one transaction; Back warns about unsaved changes | Save each change as it is made, so nothing can be lost and no warning is needed | **Auto-save.** The docked button is "Done" and simply goes back |
| Discarding | Back offers to discard all changes | With auto-save there is nothing to discard; give Undo where a mistake is most costly | **Undo for removal** (5 seconds, as in the PDF). There is no "discard everything" |
| Live summary | Total cost plus four macro rows (calories, protein, fat, carbohydrate) against household targets | The macro rows are nutrition calculation, which is Phase 7 | **Cost summary only** in this phase; macro rows are deferred |
| Quantity limit | Stepper min 1, max 99 | Phase 5 can generate more than 99 (over 200 eggs for twenty people), so 99 would make generated items uneditable | **1 to 999** |
| Stepper smoothness | Updates debounced by 120ms | Apply each step as one atomic database update instead | **No debounce**; each tap is a single `quantity + 1` statement, so rapid taps all count |
| Add item | "Opens a searchable item picker sheet" | A bottom sheet is cramped with a keyboard | **A full screen** with search, category chips and a running total |
| Adding an item already on the list | Not specified | Never show two cards for the same grocery | **Its quantity goes up by one**; the row shows "In your list: 2 kg" and the button reads "1 more" |
| Remove | 36dp circle, danger tint, 48dp target | — | **As the PDF**. Swipe-to-delete was not added: a visible button is clearer and does not conflict with scrolling |
| Over budget | Total switches to the danger token | Say how far over, in words | **"Over budget" with the amount**, a red rail, and "Your list is Tk … over your Tk … budget" |
| Card contents | Name and price read-only; stepper; delete | Also show the unit price, so the effect of a step is predictable | **Name, "Tk … per kg", line price, stepper with unit, remove** |
| Reordering | "Not supported at v1" | — | **Not supported** |
| Card height | 94dp fixed | Let long names wrap | **Grows with the name** |
| "Edit list" on an empty list | Not specified | Offer the way forward | **"Your list is empty" with "Add groceries"** |

## 7. Quantity behaviour

- Minus and plus change the quantity by one. Minus is disabled at 1 and plus at 999.
- The change is one database statement (`quantity = min(999, max(1, quantity + delta))`), so the limits hold whatever is tapped and however fast. A test sends twenty taps before the screen has seen any of them, and all twenty count.
- The card's line price and the summary update as soon as the row changes.
- Reducing never removes an item; removal is its own action.
- The unit price does not change: an item keeps the price it was listed at.

## 8. Add and remove behaviour

**Remove**

- The item is deleted at once and the total drops.
- "… removed" appears with **Undo** for five seconds. A second removal replaces the first offer.
- Undo restores the same row: same position, quantity, price and bought state.
- Removing every item leaves the list in place, shown as "Your list is empty" with "Add groceries".

**Add**

- The picker lists every catalog item with its category and its price **for the profile's region**, from the same price table the generator uses.
- Add puts one unit on the list at that price. It is saved immediately; there is no confirmation step.
- An item with **no price in the region** is still shown, marked "Price unavailable for your region", and cannot be added. No price is invented.
- A one-line total at the bottom shows "Total Tk … · Tk … left" or "… over budget", and rises with the keyboard.

## 9. Duplicate behaviour

A list never holds the same catalog item twice.

- Adding an item that is already on the list raises its quantity by one.
- The picker shows "In your list: 3 kg" on such items, and the button reads "1 more".
- Undoing a removal after the same item was added again merges the two quantities instead of creating a second row.

## 10. Budget behaviour

- The list's budget is the one it was generated with; editing does not change it.
- **Editing is never blocked by the budget**, and nothing is removed or trimmed automatically.
- Within budget: "Remaining budget Tk …" in green and "N% of Tk … used".
- **Exactly at budget** counts as within it: remaining Tk 0, 100% used.
- **Over budget:** the label becomes "Over budget", the amount and the rail turn red, and the caption reads "Your list is Tk … over your Tk … budget".
- Home's "This month so far" card shows the same total.
- All prices remain demo data; the caption says so.

## 11. Bought-state behaviour

- Unchanged from Phase 5 and still on the list screen: tapping the round checkbox stores the item as bought and dims it; tapping again undoes it.
- A bought item stays on the list and in the total.
- The summary now shows the count ("22 items · 3 bought").
- Bought state survives quantity changes, removal followed by undo, leaving the screen, and logout and login.
- There is no purchase history.

## 12. Search and filter behaviour

- **Search** in the picker is local, ignores case, and matches any part of the name ("tom" finds Tomato).
- **Category chips** are "All" plus every category in the catalog, single-select, in a horizontally scrolling row.
- Search and category combine.
- Changing either scrolls back to the top and never changes the list.
- No match shows "No groceries found."
- The list screen's own search and filter (Phase 5) are unchanged.

## 13. Account isolation

- Every new operation is limited to the logged-in account's lists: quantity change, remove, restore and add all check ownership in the database query or the repository.
- Opening the editor or the picker with another account's list id shows "This list isn't available".
- Tests log in as a second account and try every operation against the first account's list and items; nothing changes.

## 14. Testing

256 unit tests in total, all passing; 33 are new. One Phase 5 test, and one step of another, asserted the old "Edit list is not built yet" message; they were replaced, since that behaviour is intentionally gone.

| Test class | Tests | Covers |
|---|---|---|
| `ListEditingTest` (new) | 31 | Totals: under, exactly at and over budget. Quantity: increase, decrease, minimum, maximum, twenty rapid taps, other items untouched. Remove: removed and total lowered; undo restores the identical row; undo after dismissal does nothing; undo after re-adding merges; removing everything leaves a refillable empty list. Add: new item at the region's price; price follows the profile's region; duplicate raises quantity; no regional price cannot be added; all 40 items offered. Search: case-insensitive, partial, empty. Category: filter, with search, All. Budget: going over is allowed and reported on every screen; exactly at budget; picker's running total; Home follows. Bought: mark, unmark, survives editing. Persistence: recreated screens; logout and login. The named list, not the latest. Unknown list. Two account-isolation tests |
| `AppShellNavigationTest` (10 → 12) | +2 | On the real screens at 393 × 832dp: List → Edit → increase (over budget shown) → decrease → remove → Undo → Add item → search → add → add again → no results → category → back → Done → list and Home updated; removing everything → "Your list is empty" → Add groceries |
| Phase 0–5 classes | 213 | Unchanged and passing, including generation, the generated list, search and filter, persistence and isolation |

## 15. Known limitations

1. **Not seen on a device.** The build was installed on the phone, but the phone was in use, so the app was not opened and the 18-step smoke test was not done. The navigation tests drive the real screens, which shows they work, not how they look.
2. **Large quantity changes take many taps.** There is no way to type a quantity or step by more than one.
3. **Only removal can be undone**, and only for five seconds. A quantity change is reversed by tapping the other way.
4. **A new item always starts at one unit**, whatever the household size.
5. **Prices are fixed when an item is listed.** If the profile's region changes, items already on the list keep their old price while newly added ones use the new region's.
6. **The picker does not warn about allergies or conditions.** Any catalog item can be added; conflict detection is Phase 8.
7. **Items cannot be reordered**, and only the latest list can be opened from the app.
8. **All prices and catalog data are demo data.**

## 16. Deferred to Phases 7 and 8

| Phase | Not built here |
|---|---|
| 7 | `NutritionCalculator`; the macro rows in the editor's summary; the nutrition score, targets, deficiency and recommendation; SCR-07; the Nutrition tab |
| 8 | `AllergyMatcher`; alert cards; replacement suggestions; "Keep anyway"; SCR-08 and SCR-12; the alerts count on Home |
| Later | Item details (SCR-11); list history; slide and shared-element transitions |

No nutrition calculation, medical or allergy logic, AI, backend or network code was added.

## Files

**Created**

- `app/src/main/java/com/example/nutricart/domain/ListRules.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/editlist/EditListScreen.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/editlist/EditListViewModel.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/editlist/AddItemsScreen.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/editlist/AddItemsViewModel.kt`
- `app/src/main/java/com/example/nutricart/ui/screens/grocerylist/BudgetSummary.kt`
- `app/src/test/java/com/example/nutricart/ui/ListEditingTest.kt`
- `project_docs/phase_6_grocery_editing.md`

**Modified**

- `data/local/Daos.kt` — single-edit queries on `ItemDao`
- `data/repository/GroceryListRepository.kt` — the four edit operations
- `navigation/NutriCartNavHost.kt`, `navigation/Routes.kt` — the two routes
- `ui/AppViewModelFactory.kt`
- `ui/components/Stepper.kt` — optional content descriptions
- `ui/screens/grocerylist/GroceryListScreen.kt`, `GroceryListViewModel.kt` — Edit list opens the editor; shared summary; empty-list state
- `res/values/strings.xml`
- Tests: `AppShellNavigationTest.kt`, `GroceryGenerationTest.kt`
- `project_docs/implementation_plan.md`, `CLAUDE.md`

**Deleted:** none. **Dependencies:** none added or changed.

## Verification

Command (run from the project root):

```
gradlew.bat clean assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 3m 4s`, 58 actionable tasks: 58 executed.
- **Unit tests:** 256 total, 256 passed, 0 failed, 0 skipped. Also run twice more with `--rerun`; all passed.
- **Lint:** 0 errors, 17 warnings: 8 dependency and plugin version notices, 7 unused template colours, 1 redundant manifest label, 1 plurals suggestion. None is new. (The number of version notices varies between runs; Phase 5 reported 3.)
- **Compiler and KSP warnings:** none.
- **Database:** version 3, unchanged; no migration.
- **Installed** on the attached phone; not opened.
