# Phase 12: Customizable Planning Period

Written 2026-10-07. Covers one change: a grocery list can be planned for 1, 2 or 3 weeks as well as a month. Catalog units, quantities, prices, allergy and health-condition logic, nutrition formulas and the AI features are not changed.

## 1. Behaviour

Home has a **Planning period** control under the budget field: 1 week, 2 weeks, 3 weeks, 1 month. One month is selected by default, and a list made that way is identical to a list made before this phase. The budget entered is the budget for the chosen period, so the label is now **Budget**.

`domain/PlanningPeriod` holds the options (7, 14, 21, 30 days) and the default. Any other value is treated as 30.

## 2. Storage

`GroceryListEntity.periodDays` (default 30). Room goes from version 3 to 4 with one statement:

    ALTER TABLE grocery_lists ADD COLUMN periodDays INTEGER NOT NULL DEFAULT 30

Existing lists become 30-day lists. Nothing is rebuilt or reset. `GroceryListRepository.createList` takes the period and stores it.

## 3. Generation

`GenerationRequest.days` (default 30). The basket template's quantities are per person per month and were not edited. The target for a slot is:

    monthly = perPerson x effectivePeople(household)
    target  = monthly                 when days is 30
            = monthly x days / 30     otherwise
    quantity target = round(target), at least 1

Everything after that is the existing code: priority order, proportional scaling of staples on a small budget, the 3% reserve, upgrades and extras, allergy and condition filtering. A 30-day request takes the unchanged branch, so no rounding can move.

The period travels from Home on the existing route, `generate?budget={amount}&days={days}`, to `GenerateViewModel`, which passes it to the generator and to `createList`.

## 4. Nutrition

`NutritionAnalyzer.analyze(entries, householdSize, days = 30)`. The calculator and scorer formulas are untouched; only the number of days the list is shared over now comes from the list. Callers that have a list pass `list.periodDays`: the nutrition screen, Home's score, the profile review (`ProfileReview.analyze`, `analyzeWithAlternatives`) and the AI context.

Without this a one-week list would be scored as a month's food divided by 30 and look badly short.

## 5. Budget rules

`BudgetRules.MIN = 500` was enforced on Home for every list, so it did block small short-period budgets. `BudgetRules.minimum(days)` is the monthly minimum in proportion, rounded up to Tk 50: 150, 250, 350 and 500. `validate` and `isValid` take the days and default to 30. Totals, remaining budget and `ListTotals` are unchanged.

Home's minimum is only a floor. The generator still reports the real minimum for the household when a budget cannot buy one unit from each core food group ("Try Tk 350 or more").

## 6. Wording

Changed because it would now be wrong: "Monthly budget" to "Budget"; "This month so far" to "Your current list" (the card always showed the latest list, not a month's spending); "monthly budget" removed from three sentences.

Left alone: `BudgetEntity.month`. It records the calendar month a budget was entered in and is not the planning period.

## 7. AI context

`AiGroceryContext.monthlyBudget` is now `budget`, and `planningPeriodDays` was added; the JSON keys match. `nutrition.daysCovered` follows the list's period because it is copied from the analysis. No instruction, schema, pick logic or UI was changed.

## 8. Tests

`ui/PlanningPeriodTest` (13): the default; target scaling for 7, 14 and 21 days across every basket slot and household size; whole units and the minimum of one; generated quantities when the budget does not bind; 30 days equal to the old formula and the old lists; the period saved on the list; Home's selection; nutrition over the list's days; budget arithmetic; the period-aware minimum; allergy, typed-allergy and condition filtering on short lists; the AI context's period; AI failure on a short list.

`data/local/MigrationTest` (+3): version 3 to 4 with lists, items, budgets and a profile kept; an empty version 3 database; version 1 through to 4.

Adjusted: the database version in two tests, the AI context field in three, two Home strings in the navigation test.

## 9. Device check (2026-10-07)

On a physical phone with existing data: the app opened after the upgrade with its earlier list intact; Home showed the selector with 1 month selected; tapping 1 week selected it. The first layout clipped the selected chip at the card's edge, so the chips now wrap. A generated short list and the AI card were not checked on the device.

## 10. Known limitations

1. **Short lists over-buy small items.** Quantities are whole units with a minimum of one, so an item needed at 0.25 kg a month is still 1 kg on a one-week list. Fixing that needs smaller catalog units, which is a separate phase.
2. For the same reason a short list for a small household costs more than its share of a month, and the generator's real minimum can be above Home's floor.
3. The period is not shown on the list screen; the nutrition screen shows the days.
4. No custom number of days. The model allows it; only the four options are offered.
5. `home_current_list` is the latest list, whatever its period. There is no monthly spending history.
