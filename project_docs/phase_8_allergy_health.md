# Phase 8: Allergy & Health-Condition Intelligence

Written 2026-10-05. Covers SCR-08 (alerts) and the content of SCR-12 (alert details). No later phase is included.

## 1. Objective

Make the app aware of the profile's allergies and health conditions: check the current grocery list against them, say clearly what was found and why, say just as clearly what could not be checked, and leave every decision to the user. The engine is deterministic and offline. It gives no medical advice and never calls anything safe or unsafe.

## 2. Architecture

Everything is in `domain/conflicts/`, pure Kotlin with no Android, database or UI code.

| Part | File | Job |
|---|---|---|
| Result model | `ConflictModels.kt` | `ConflictAnalysis`, `ItemConflict`, `ItemConcern`, `ListConsideration`, `Alternative`, and the enums `ConcernType`, `Severity`, `ConcernReason` |
| `AllergyMatcher`, `CustomAllergenAliases` | `AllergyMatcher.kt` | Profile allergies against the catalog's stated allergens |
| `HealthRule`s, `HealthRules`, `HealthConditionAnalyzer` | `HealthConditionAnalyzer.kt` | Rules as data, and the code that applies them |
| `AlternativeFinder`, `ConflictAnalyzer` | `ConflictAnalyzer.kt` | Like-for-like alternatives; the coordinator |

    ProfileSelections + list items + catalog and regional prices + NutritionAnalysis (Phase 7)
            -> ConflictAnalyzer.analyze(...)
            -> ConflictAnalysis

`ui/screens/alerts/ProfileReview.kt` is the one place screens call the engine. Results are structured data; sentences are built in the UI from string resources, one fixed sentence per `ConcernReason`.

## 3. Allergy matching

- The only evidence is `CatalogItemEntity.allergens`. A profile allergen that is in that set is a match. Nothing else is.
- Item names are never read. An item called "Peanut cookies" with no allergen data is not flagged.
- An item with no allergen data produces no match. That means "nothing known", and the screen says so; it is never described as free of anything.
- All 11 listed allergens are supported: Dairy, Eggs, Fish, Shellfish, Poultry, Beef, Soy, Peanuts, Tree nuts, Gluten, Sesame. The existing `Allergen` enum is used; no second representation was added.

Catalog items carrying allergen data (demo seed): flour and oats (Gluten); eggs (Eggs); chicken and chicken liver (Poultry); beef (Beef); rui, hilsa, small fish and dried fish (Fish); shrimp (Shellfish); peanuts, peanut oil and chanachur (Peanuts); milk and yogurt (Dairy); soybean oil (Soy). **No seed item carries Tree nuts or Sesame**, so those two allergies can never match with the demo catalog. The other 23 items carry no allergen data.

## 4. Health-condition rule model

A rule is a data object (`HealthRule`), not a branch in code. Three kinds:

| Kind | Looks at | Fields |
|---|---|---|
| `AllergenTagRule` | The item's stated allergens | conditions, allergen |
| `ItemNutrientRule` | One nutrient per 100 g of one item | conditions, nutrient, comparison, threshold |
| `ListNutritionRule` | A figure for the whole list from Phase 7 | conditions, metric, comparison, threshold |

`HealthConditionAnalyzer` takes the rule list as a constructor argument, so rules can be added without touching it (a test does this).

The catalog's `flaggedConditions` field is **not** used as evidence. Those are hand-set demo tags (white rice, sugar and jaggery for Diabetes; dried fish and chanachur for Hypertension) with no figure behind them. Since the consistency fix in section 19, nothing reads them.

## 5. Supported conditions

| Rule id | Conditions | What is compared | Line | Level |
|---|---|---|---|---|
| `celiac-gluten` | Celiac disease | Item's stated allergens contain Gluten | n/a | High |
| `concentrated-carbohydrate` | Diabetes, Prediabetes | Item's carbohydrate per 100 g | at least 90 g | Medium |
| `carbohydrate-share` | Diabetes, Prediabetes | Carbohydrate's share of the list's energy | above 65% | Low |
| `iron-coverage` | Anemia | List's iron per person per day against the reference | below 60% | Low |

Reasoning:
- **Celiac**: gluten is stated in the catalog's allergen data, so this is the same kind of exact match as an allergy and is shown with the allergen matches.
- **Concentrated carbohydrate**: 90 g per 100 g means almost pure carbohydrate. With the demo catalog that is sugar (100) and jaggery (98). Rice (80), flour (72), oats (66) and pulses (about 60) are not flagged: carbohydrate itself is not treated as a problem.
- **Carbohydrate share**: the same 65% upper bound Phase 7 already shows on the nutrition screen. It names no item.
- **Iron coverage**: the same 60% gap line as Phase 7. It reports how much iron the list holds. It says nothing about anyone's iron levels and suggests no food.

All thresholds are NutriCart's own, not clinical ones.

## 6. Unsupported conditions

| Condition | Why there is no rule |
|---|---|
| High blood pressure | Needs sodium. The catalog has none |
| High cholesterol | Needs saturated fat. The catalog has total fat only, which is not the same thing |
| Heart disease | Needs sodium and saturated fat |
| Kidney disease | Needs potassium, phosphorus and sodium, and depends on individual circumstances |
| Liver disease | No defensible general rule from five nutrients |
| PCOS | "Low carbohydrate" would be a simplification the data cannot justify |
| Thyroid condition | Needs iodine. The catalog has none |
| Any typed condition | Free text is never given a rule |

For each of these the review screen says "Automated analysis isn't available for: …". Nothing is flagged for them.

## 7. Nutrition fields used

Carbohydrate per 100 g (item rule); protein, carbohydrate and fat per person per day (for the energy share); iron coverage. All come from Phase 7's `NutritionCalculator`, `NutritionScorer` and `NutritionAnalysis`; nothing is calculated a second time. No field was added and none is estimated.

## 8. Severity model

`Severity` describes how firmly the data supports the finding and how prominently it is shown. **It is not a measure of medical risk.**

| Level | Meaning | Shown as |
|---|---|---|
| High | Two stated facts match exactly: the catalog names an allergen and the profile lists it (or celiac disease, for gluten) | "Allergen matches" section, red bar, ALLERGEN badge |
| Medium | One item's figure crosses a rule's line | "Nutrition considerations" section, amber bar, NUTRITION badge |
| Low | A whole-list figure crosses a rule's line | "About the whole list", amber information card, no actions |

`ConcernType` is separate: `Allergy`, `HealthCondition` (celiac), `NutritionConsideration`.

An item with several concerns is one `ItemConflict` with the concerns listed strongest first. It appears once, in the section of its strongest concern, with "N concerns" above the lines.

## 9. Replacement logic

`AlternativeFinder` offers up to three catalog items. A candidate must:

1. be in the same food category and have the same main-nutrient tag as the flagged item;
2. have a price above zero in the profile's region;
3. have nutrition data and a weight per unit;
4. raise no concern of its own for this profile (the full item check is run on it);
5. not be on the list already.

Order: closest price per 100 g to the flagged item, then catalog id. Quantity: the number of units nearest the flagged item's weight, never less than 1 (48 eggs become 2 kg).

If nothing qualifies the card says "No suitable alternative found." (for example sugar under Diabetes: the only like-for-like item is jaggery, which the same rule flags).

Replacing is one transaction (`ItemDao.replaceOwned`): the flagged row is deleted and the alternative added at the regional price, unbought and without "keep anyway". If the alternative is somehow on the list already, its quantity is raised instead of adding a duplicate. The view model only accepts an alternative the current analysis offered for that item.

An alternative is a like-for-like grocery swap. It is not presented as suitable for anyone's health.

## 10. Keep Anyway

- Stored in the existing `list_items.alertOverridden` column, per list item.
- The conflict stays in the analysis with `keptAnyway = true`: same concerns, same section, still marked on the list ("KEPT ANYWAY"). Only the count of items waiting for review goes down.
- It does not touch the profile, does not affect other items with the same allergen, other lists, or future matching.
- It can be undone ("Review again").
- Removing the item and adding it again creates a new row, so the question is asked again.

## 11. Custom allergies and conditions

**Typed allergies** are matched only when the whole entry, trimmed and ignoring case, is one of these names for an existing allergen group:

| Allergen | Accepted entries |
|---|---|
| Dairy | milk, dairy, cow's milk |
| Eggs | egg, eggs |
| Fish | fish |
| Shellfish | shellfish, shrimp, prawn, prawns |
| Poultry | chicken, poultry |
| Beef | beef |
| Soy | soy, soya, soybean, soybeans |
| Peanuts | peanut, peanuts, groundnut, groundnuts |
| Tree nuts | tree nut, tree nuts |
| Gluten | gluten, wheat |
| Sesame | sesame, sesame seeds |

A match is shown with the entry as typed ("matched to your entry …"). Anything else ("Kiwi", "Mustard", "peanut butter", "nuts") stays on the profile and is listed under "Not checked automatically". There is no partial or keyword matching.

**Typed conditions** are never analysed, even if the text equals a listed condition's name. They are listed under "Not checked automatically".

## 12. Missing data

| Situation | Behaviour |
|---|---|
| Item has no allergen data | No match; the screen states that this is not known to be free of anything |
| Item has no nutrition data | Nutrient rules are skipped for it and it is counted ("N items have no nutrition data and were not checked") |
| List has no nutrition analysis | List-wide rules are reported as not run, never as passed |
| Condition needs data the catalog lacks | No rule; reported as unavailable |
| No regional price for a candidate | It is not offered as an alternative |
| No profile | No analysis |

Limit: nutrition columns are not nullable, so an item with a true zero and an item with one missing figure cannot be told apart. Only "all five are zero" is treated as missing.

## 13. UI and UX

- **Review screen** (`alerts/{listId}`, pushed, no bottom bar): summary card, "Allergen matches", "Nutrition considerations", "About the whole list", "Not checked automatically", disclaimer.
- **Item card**: name, badge, one sentence per concern, suggested alternatives each with quantity, cost and "Replace", then "Keep anyway".
- **List**: flagged items get one small chip under the name (ALLERGEN, REVIEW or KEPT ANYWAY) that opens the review; a warning icon in the top bar opens it at any time. Cards are otherwise unchanged.
- **Nutrition**: a "Profile review" card under the highlights with the count and a "Review list" button. The score and every figure are untouched.
- **Home**: "N items to review" on the current-list card, only when N is above zero; it opens the review.
- **Nothing flagged** reads "No profile conflicts detected in this list." with a note on what was and was not checked.

## 14. PDF compared with what was built

| Area | PDF / Existing Requirement | Suggested Improvement | Final Decision |
|---|---|---|---|
| Alert banner | Banner with a count | Count only items still waiting; red only while an allergen match is open | Built that way |
| One card per conflict | 152dp card per conflict with a severity bar and ALLERGY or HEALTH badge | One card per item, grouping its concerns; height by content | One card per item; ALLERGEN or NUTRITION badge |
| Sections | A single list | Separate allergen matches from nutrition considerations so they do not look equally certain | Two sections plus a whole-list section |
| SCR-12 alert details | Separate screen: why flagged, alternatives, Replace, Keep anyway | All of it fits on the card; a second screen adds a tap and no information | **Folded into the card.** `alerts/{listId}/item/{itemId}` is not built; no information was dropped |
| "Safe alternatives" | Section title on SCR-12 | The app cannot say safe | "Suggested alternatives" |
| "Safe items" confirmation card | Confirms the rest are safe | Cannot be claimed | "No profile conflicts detected in this list." with a note on the limits |
| Alternatives ranking | By price proximity | Also require same role, regional price, no concern of its own, not on the list | Built; price proximity is the ordering |
| Replace | Opens a substitutes picker | Up to three choices inline, each with its own button | Inline |
| Keep anyway | Records an override | Keep the warning visible and make it undoable | Built, with "Review again" |
| "Continue to list" docked button | Pops | The system back and the top bar's back already do this | Not built |
| Entry points | Home's alerts cell only | Also from the list and from Nutrition | All three |
| Health badge | HEALTH | Avoid implying a medical finding | NUTRITION, "Nutrition considerations" |
| Unchecked profile entries | Not in the PDF | State them instead of ignoring them silently | "Not checked automatically" section |
| Phase 7's deferred "add this item" card | Recommendation with an Add button | Suggesting a food because of a condition would be advice | Still not built; see section 16 |

## 15. Privacy and account isolation

- The engine takes its inputs as arguments and holds no state. There is no global profile.
- Lists and profiles are read through repositories that take the account from the session. Another account's list id returns nothing; `setKeptAnyway` and `replaceItem` on another account's item do nothing.
- Nothing derived is stored. The database stays at **version 3**; there is no migration. The only stored Phase 8 value is the existing per-item keep-anyway flag.
- Tests cover two accounts with different profiles and lists.

## 16. Limitations

1. All catalog data is unverified demo data. An allergen missing from the seed is a missed match.
2. Tree nuts and Sesame have no tagged seed item.
3. Seven of eleven listed conditions have no rule, by design.
4. The four rules and their thresholds are product choices and need review by a qualified person before real use.
5. Typed allergies that map to an allergen group (section 11) are used by the review but not by generation, which reads only the listed allergies. A list generated for someone who typed "peanut" instead of ticking Peanut can contain peanuts; the review then flags them.
6. Cross-contamination, "may contain" labelling and ingredients of composite foods are outside what the catalog can express.
7. Only the review screen computes alternatives; Home and the list compute the flags only.
8. No screen suggests foods to add for a condition.

## 17. Future LLM / ML extension point

A later layer should take `ConflictAnalysis` (and `NutritionAnalysis`) as input. Each concern carries its type, severity, reason, matched allergen or conditions, rule id, value and threshold, so wording, ranking of alternatives or explanation can be built without parsing UI text. The matcher and rules stay the source of truth for what is flagged.

## 18. Tests

| File | Tests | Covers |
|---|---|---|
| `domain/conflicts/ConflictEngineTest` | 45 | Listed allergy match and non-match, several allergies, several allergens on one item, no inference from names, typed allergies (known, unknown, normalisation, duplicate of a listed one), rule set contents, carbohydrate rule (match, staples not flagged, boundary 89.9/90, without the condition, two conditions), missing nutrition data, demo tags not used, unsupported and typed conditions, celiac, several concerns on one item, carbohydrate share, iron coverage (below, at and above the line, household size), rules not run without nutrition, several conditions together, a custom rule, alternatives (valid, conflicting, on the list, no price, none, weight, no data), keep anyway, quantity and bought state, determinism, empty list, generator unchanged |
| `ui/ProfileReviewTest` | 20 | The same against the real database: review of the current list, unchecked entries, empty profile, Phase 7 figures, keep anyway (warning stays, profile unchanged, per item and per list, re-add asks again), replace (swap, only offered alternatives, merge), add/remove/quantity/bought, profile changes, list screen flags, Home count, nutrition unchanged, two accounts, database version, wording scan |
| `ui/AppShellNavigationTest` | 2 new | Home → review → keep anyway → review again → replace; list marker, top-bar entry, editor add, nutrition entry, removal clears the warning |

The wording scan checks every Phase 8 string for: safe, unsafe, cure, treat, diagnos, guarantee, forbidden, medically, you should, stop eating, prevent, dangerous, harmful, must not, good for, bad for, approved.

Existing tests: two call sites gained an argument (`GroceryListViewModel` now takes the profile repository). None was removed or weakened.

## 19. Consistency fix: generation and review use the same condition rules

**What was inconsistent.** Phase 5's generator left an item out when its `flaggedConditions` demo tag matched a listed condition (`domain/ProfileConflicts.kt`). Phase 8 ignores those tags and uses rules. The two disagreed in four ways:

| Case | Generation (before) | Review |
|---|---|---|
| Diabetes, white rice (80 g carbohydrate per 100 g) | Swapped for brown rice, because of a tag | Not flagged; no rule supports it |
| High blood pressure, dried fish and chanachur | Left out, because of a tag with no sodium data | No rule; reported as not checked |
| Prediabetes, sugar | Kept (no tag) | Flagged, by the same rule as Diabetes |
| Celiac disease, flour and oats | Kept (no tag) | Flagged as containing gluten |

So a tag with no data behind it changed what people were given, and a freshly generated list could be flagged at once. (Dried fish and chanachur are not in the demo basket, so the second row was a latent rule rather than a visible effect.)

**The fix.** One function changed: `CatalogItemEntity.conflictsWith` now asks `HealthConditionAnalyzer` whether a documented item rule flags the item, instead of reading `flaggedConditions`. The allergen half of the function is untouched. The generator itself, the rules, the seed data and the database (still version 3) are unchanged; the `flaggedConditions` column is kept and read by nothing.

**Resulting behaviour.**

| Profile lists | Generation leaves out |
|---|---|
| Any listed allergen | Items the catalog states contain it (as before) |
| Diabetes or Prediabetes | Sugar and jaggery (at least 90 g carbohydrate per 100 g). White rice is no longer swapped |
| Celiac disease | Flour and oats (stated gluten) |
| Anemia | Nothing (its rule is about the whole list) |
| Any condition without a rule | Nothing |

Two of these are new exclusions (Prediabetes: sugar; Celiac: flour and oats). They follow from rules that already existed; no rule was added or changed. A list generated for a profile's listed allergies and conditions now never contains an item the review flags, and tests check that for every allergen and condition.

List-wide considerations (carbohydrate share, iron) can still appear on a generated list: they describe the list and name no item.

**Tests.** `GroceryGeneratorTest`: one test rewritten (it asserted the white-rice swap) and four added. `ConflictEngineTest`: two added. `GroceryGenerationTest`: one assertion changed from the tag to the rule.
