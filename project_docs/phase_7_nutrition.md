# Phase 7: Nutrition Calculation, Analysis & Scoring

Written 2026-10-05. Covers SCR-07 (Nutrition analysis). Nothing from Phase 8 is included.

## 1. What was built

- A nutrition engine in `domain/nutrition/`: pure Kotlin, no Android, database, UI or network.
- A real Nutrition screen in place of the "Coming soon" placeholder (`ui/screens/nutrition/`).
- The same screen for one named list, opened from the chart icon in the list's top bar (`nutrition/{listId}`).
- One line on Home's "This month so far" card: "Nutrition balance N / 100".
- 50 new tests (306 in total).

Removed: `ui/screens/upcoming/UpcomingScreen.kt` and its three `upcoming_*` strings, which nothing else used.

## 2. Nutrition data audit

| Question | Finding |
|---|---|
| What nutrition does a catalog item hold? | `caloriesPer100g`, `proteinPer100g`, `carbsPer100g`, `fatPer100g`, `ironMgPer100g` on `CatalogItemEntity` |
| What is the basis? | Per 100 g, for all 40 items |
| How is a quantity turned into weight? | `unit` ("kg", "L", "pcs") and `gramsPerUnit` (1000 for kg, 920 for a litre of oil, 1030 for a litre of milk, 50 for an egg, 100 for a banana) |
| Is anything missing? | No fibre, sodium, sugar or any micronutrient other than iron. No item in the seed lacks the five values |
| Is the data authoritative? | No. It is demo seed data (`DemoCatalogSeed`), and the screen says so |
| Is a schema change needed? | No. The database stays at version 3; no migration was written |

Because the data cannot support them, fibre, sodium and sugar are not shown and are not part of the score.

## 3. Structure

| Part | File | Job |
|---|---|---|
| `NutritionFacts`, `Nutrient`, `NutritionReference` | `NutritionFacts.kt` | The five figures, and every reference value and threshold in one place |
| `NutritionCalculator` | `NutritionCalculator.kt` | Items and quantities to totals |
| `NutritionScorer` | `NutritionScorer.kt` | Coverage, macronutrient shares, the 0 to 100 score and its band |
| `NutritionInsights` | `NutritionInsights.kt` | Observations as data (`NutritionInsight`), not sentences |
| `NutritionAnalyzer` | `NutritionAnalyzer.kt` | Runs the three in order and returns a `NutritionAnalysis`, or null |

The three parts do not call each other; only the analyzer joins them. A later layer (an LLM or model that rewords or ranks) would take `NutritionAnalysis` as input. It would not replace the arithmetic.

## 4. Calculation

    grams        = quantity x gramsPerUnit
    contribution = per-100 g values x grams / 100
    list total   = sum of contributions
    per person per day = list total / (household size x 30)

- A kilogram is 1000 g and a piece or litre uses that item's own weight, so 2 kg of rice is 2000 g, not 2 g or 2 units.
- An item contributes nothing and is counted as "skipped" when its quantity is not positive, its `gramsPerUnit` is not positive, or all five of its values are zero. Negative values in the data are read as zero. Nothing is guessed or filled in.
- Household size comes from the profile. It is the only profile field the engine reads.
- The period is 30 days because a list is generated from a monthly budget.
- The "bought" tick does not affect nutrition: the analysis is of what the list holds.

## 5. Reference values

| Nutrient | Per person per day | Source |
|---|---|---|
| Energy | 2,000 kcal | A round general figure |
| Protein | 70 g | "Required" figure on the SCR-07 mockup |
| Carbohydrate | 300 g | SCR-07 mockup |
| Fat | 60 g | SCR-07 mockup |
| Iron | 18 mg | SCR-07 mockup |

Energy shares used for balance: protein 10 to 35%, carbohydrate 45 to 65%, fat 20 to 35%, with 4, 4 and 9 kcal per gram.

These are one set of general adult values. They are not adjusted for age, sex, activity or health, because the profile does not hold those fields (decided in Phase 4) and Phase 7 must not use health conditions. They are labelled in the app as NutriCart's reference values.

## 6. The score

**This is a NutriCart product scoring heuristic. It is not a clinical, official or validated measure.**

    score = 60 x adequacy + 25 x balance + 15 x variety      rounded, 0 to 100

| Part | Weight | Definition |
|---|---|---|
| Adequacy | 60 | Coverage of each of the five nutrients (per person per day / reference), each capped at 100%, averaged |
| Balance | 25 | For protein, carbohydrate and fat: 1 if its share of energy is inside its range, falling in a straight line to 0 at 20 percentage points outside; averaged |
| Variety | 15 | How many of Grains, Protein, Veg, Fruit, Dairy are on the list, divided by 5 |

Bands: 85 and above "Very good balance", 70 to 84 "Good balance", 50 to 69 "Fair balance", below 50 "Low balance".

Why this shape:
- The PDF describes the score as distance from the household's targets, so adequacy carries most weight.
- The cap means a surplus of one nutrient cannot hide a shortage of another.
- Balance and variety stop a list of rice alone, bought in quantity, from scoring highly.
- Oils and Pantry do not count towards variety.

Example: a list holding exactly half the reference for its household, in range and from all five groups, scores 60 x 0.5 + 25 + 15 = 70.

Known limits: the score rewards quantity up to the reference, so a small top-up list scores low even if well chosen; surplus is not penalised in the score (it is reported as an insight); the weights are a judgment, not derived from evidence.

## 7. Gaps and insights

- A nutrient under 60% of its reference is a gap (the PDF's warning threshold). Exactly 60% is not a gap.
- Only the largest gap gets an alert card. With no gaps and a score of 90 or more, a "No major gaps" card is shown. Otherwise no card.
- Insights are chosen by fixed rules, at most four, always starting with food groups:

| Insight | Rule |
|---|---|
| Variety | Always: groups present and missing |
| Grains dominant | Grains supply more than 60% of the list's energy |
| Oils and pantry high | Oils plus Pantry supply more than 30% of the energy |
| Macronutrient out of range | The one macronutrient furthest outside its range (one only: when one share is high another is low) |
| Protein well covered | Protein at 90% of the reference or more |
| Energy above reference | Energy at 130% of the reference or more |
| Items not counted | One or more items were skipped |

Each insight is a fixed sentence filled with calculated figures. They describe the list and give no advice.

## 8. The screen

Top to bottom: score ring with band, gap count and the basis ("Based on your current grocery list: 22 items, shared by 4 people over a 30-day period"); the gap or no-gaps card; "Per person, per day" with a bar per nutrient ("762 of 2,000 kcal"), amber under 60%; food variety; highlights; the disclaimer; "Edit list".

States:
- Loading: a spinner.
- No list: "No list to analyse yet" with "Go to Home". No score, no zeros.
- A list with nothing countable (emptied, or no data): "Nothing to analyse" with "Edit list". No score.

## 9. Which list, and whose

- The Nutrition tab analyses the account's latest list; `nutrition/{listId}` analyses that list and keeps to it if a newer one appears.
- Lists are read through `GroceryListRepository`, which takes the account from the session, so another account's list id yields nothing.
- Only the list's items are read, never the whole catalog.

## 10. Staying current after edits

Nothing derived is stored. `NutritionViewModel` (and Home) combine the list's items Flow with the profile Flow and recompute on every emission, so a quantity change, add, remove, undo or household change shows without regenerating and without a stale score. The cost is 40 items at most, computed off the main thread.

## 11. Boundary with Phase 8

Not built, deliberately: allergy matching, health-condition logic, warning cards, swaps, "Keep anyway", any recommendation of a specific item.

- The engine does not read `allergies`, `conditions` or their custom values. A test changes them and asserts the analysis is identical.
- A test scans every nutrition string for medical and safety wording ("safe", "unsafe", condition names, "treat", "you should" and so on).
- The catalog's `allergens` and `flaggedConditions` fields are untouched by this phase.

## 12. PDF compared with what was built

| Area | PDF / Existing Requirement | Suggested Improvement | Final Decision |
|---|---|---|---|
| Score ring | Large ring with the score | Add "out of 100", a band label and the basis line so the number can be understood | Built with those additions |
| Nutrient bars | Protein, carbs, fat, iron: required against provided | Add calories; state the figures per person per day rather than as unexplained monthly totals | Five bars, per person per day |
| Warning threshold | Bar turns to warning under 60% | Keep | Kept, using the existing `ProgressRail` |
| Gap alert | One alert for the largest gap | Keep one only; add a positive card when nothing is short | Built; positive card needs no gaps and a score of 90 or more |
| "Add X to close the gap" recommendation card | A named item with price and an Add button | It needs to avoid the user's allergens and conditions, which is Phase 8 logic. Recommending an item without that could suggest something the profile excludes | **Deferred to Phase 8.** The gap is stated; no item is suggested |
| Food variety | Not in the PDF | Requested in the Phase 7 brief | Built as a card and as part of the score |
| Highlights | Not in the PDF | Requested in the Phase 7 brief | Built, four at most |
| Disclaimer | Not in the PDF | Required: demo data, not advice | Built, always visible below the analysis |
| Empty state | Not specified | Explain and offer the next step instead of zeros | Built |
| Home summary | Shows a nutrition score beside spend | Show it now that it exists | One line on the current-list card |
| Entry from the list | Tab only | Also reachable from the list it describes | Chart icon in the list's top bar |
| RDA by age and sex | Implied "household targets" | The profile has no such fields | One general adult reference, labelled as such |
| Period | Not stated | Monthly budget implies a month | 30 days, shown on screen |

## 13. Tests

| File | Tests | Covers |
|---|---|---|
| `domain/nutrition/NutritionEngineTest` | 32 | Units (g, kg, L, pcs), scaling, zero and invalid quantity, missing weight and data, totals, per person per day, score formula, cap, balance, bands, bounds over 20 generated lists, determinism, variety, gaps, each insight, the limit of four |
| `ui/NutritionScreenTest` | 16 | No list, empty list, current list, quantity change, add, remove, household change, bought state, latest against pinned list, list-only scope, generated list, Home score, account isolation, conditions and allergies ignored, wording scan |
| `ui/AppShellNavigationTest` | 2 new, 1 changed | Nutrition tab with a list, edit from it and return to changed figures, entry from the list, the no-list state and its action |

No existing test was removed or weakened. One assertion changed: the tab no longer shows "Coming soon".

## 14. Verification

`gradlew.bat clean assembleDebug testDebugUnitTest lintDebug`: see the report accompanying this phase for the exact result of the final run.

Lint baseline is unchanged from Phase 6 (0 errors; the warnings are version notices, unused template colours, the manifest label and one plurals suggestion).

Device: see the report. Layout, colours and scrolling of the new screen have been exercised by Robolectric Compose tests only unless the report says otherwise.

## 15. Open points

1. The reference values and weights are product choices and should be reviewed by someone with nutrition expertise before any real use.
2. The demo nutrition data is unverified.
3. The recommendation card is deferred to Phase 8 (row 5 of the table).
4. The Variety insight repeats the variety card in brief; one of them could go.
5. Only the latest list is reachable from the tab; older lists have no entry point yet (carried from Phase 5).
6. Carried from earlier phases and unchanged: PBKDF2 iteration count not stored per account, `allowBackup`, schema export off, provisional onboarding copy.
