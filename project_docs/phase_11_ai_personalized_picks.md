# Phase 11: Hybrid AI Grocery Personalization

Written 2026-10-07. Covers Phase 11 only: the model prioritises items that are already on a generated list, and Kotlin decides which of its picks are shown. It also records the small custom-allergy fix made just before it.

## 1. What was built

The Phase 10 "AI insights" card now also shows **AI picks from your list**: up to six items from the list, each with a High, Medium or Low priority and a one-sentence reason, plus up to three **trade-offs**. It is the same tap and the same single request as Phase 10; there is no second AI call, screen or task.

The list itself is never changed by the model. Nothing is added, removed, reordered or re-quantified.

## 2. Who decides what

| Kotlin (authoritative) | Gemini (advisory) |
|---|---|
| Which items exist, their prices and availability (`GroceryGenerator`, catalog) | Which items already on the list matter most, and why |
| What is left out for allergies and condition rules (`conflictsWith`, `AllergyMatcher`, `HealthConditionAnalyzer`) | A summary, reasoning, suggestions, trade-offs |
| Quantities, totals, remaining budget (`ListTotals`) | |
| Nutrition figures and score (`NutritionAnalyzer`) | |
| Whether an answer is accepted (`AiResponseJson`, `AiResponseValidator`) | |
| Which picks are shown (`AiPicks`) | |

## 3. Flow

1. `GroceryGenerator` builds the list exactly as before. It does not know the AI layer exists.
2. On the list screen the user opens the card and taps **Get AI insights**.
3. `AiInsightsContext` builds the context. Each item now also carries `mainNutrient`, the catalog's own tag (Protein, Carbs, Fat, Iron). No price, per-item figure or id is added.
4. The request goes through the existing `AiReasoningEngine`. The question now also asks for `prioritizedItems` and `tradeOffs`.
5. `AiResponseJson.parse` and `AiResponseValidator` check the answer's shape, lengths, counts and wording. A malformed pick or trade-off fails the whole answer.
6. `domain/ai/AiPicks.accepted` keeps a pick only when its name matches, ignoring case and surrounding spaces, an item that was in the context **and** that item is not in the review's flagged items. The name shown is the app's own. Picks are de-duplicated and sorted by priority.
7. The card shows the accepted picks. With none, the section is left out and the rest of the answer is shown.

## 4. Changes

- `domain/ai/AiRecommendation.kt`: `AiPriority`, `AiPrioritizedItem`, `AiPicks`.
- `domain/ai/AiResponse.kt`: `prioritizedItems` and `tradeOffs` on `AiResponse` (defaulted), with validator limits.
- `domain/ai/AiRequest.kt`: `AiListItem.mainNutrient` (optional).
- `data/ai/AiJson.kt`: the question, the schema, the parser.
- `data/ai/firebase/FirebaseAiLogicGateway.kt`: the same two fields in the Firebase schema.
- `ui/screens/grocerylist/AiInsights.kt`, `AiInsightsCard.kt`, `GroceryListViewModel.kt`: `Ready.picks`, the two card sections.
- Strings: five `ai_insights_*` entries.

**Design decision: one request, not two.** A separate "personalize" task would need a second schema, a gateway that knows about tasks, a second loading state and a second disclosure. Extending the one answer reuses everything and costs one request per tap.

**Design decision: the two new fields are optional when parsing.** An answer without them is still a valid Phase 10 answer. When present they must be well formed.

**Design decision: flagged items cannot be picked.** An item the review flags, for an allergen or a condition rule, kept or not, is never shown as an AI pick.

## 5. Custom allergies (fix made before this phase)

`GenerateViewModel` now gives the generator the allergens resolved by `AllergyMatcher.resolve`: the listed ones plus typed names found in `CustomAllergenAliases`. A typed "peanut" leaves out the same items as picking Peanuts. An unknown typed allergy leaves nothing out and is still reported as not checked. There is one allergy system; Phase 11 reads its result through the review.

## 6. Health conditions

No rule was added. Conditions without a rule, and typed conditions, are sent in `notCheckedByRules`, and the model is told to say they were not checked rather than reason about them. That is stricter than the brief allowed, and was kept from Phase 10.

## 7. Failure behaviour

Unchanged from Phase 10. Offline, a timeout, a Firebase or App Check failure, or a malformed or rejected answer shows the friendly failure with **Try again**; the list, totals and review are untouched.

## 8. Tests

`ui/AiPersonalizedPicksTest` (12 tests): generation is identical whatever the AI does; the candidate context; unknown products are dropped and nothing is added to the list; picks are shown once in priority order; picks for flagged items are dropped and the flags stay; a typed allergy is resolved by the existing matcher; well-formed picks and trade-offs are parsed; eleven malformed variants fail the whole answer; an answer without picks is still the Phase 10 answer; budget figures are the app's own; the card with and without picks.

`ui/GroceryGenerationTest`: two tests for typed allergies. `AiInsightsTest` and `AiFoundationTest`: two assertions updated for the new item key and schema fields.

## 9. Device check

**Not run.** The phone reported `unauthorized` to adb when the check was attempted, which needs the USB-debugging prompt to be accepted on the device. The new schema has therefore not been exercised against the live model.

## 10. Known limitations

1. **Live schema unverified** (section 9). `Schema.array` and `Schema.string` calls compile; the model's behaviour with the larger schema has not been seen.
2. The model does not influence what is generated: it prioritises a finished list. Letting it reorder or choose between the generator's valid alternatives would be a further step.
3. No stored preferences or goals exist in the profile, so "personalisation" rests on household size, budget, allergies, conditions and the nutrition findings.
4. A pick is matched by exact name. A slightly reworded name is dropped rather than guessed, so some answers will show fewer picks than the model gave.
5. Reasons are free text: the validator checks length and banned wording, not agreement with the context. A reason may still restate a figure wrongly.
6. The card is longer. It scrolls with the list.
