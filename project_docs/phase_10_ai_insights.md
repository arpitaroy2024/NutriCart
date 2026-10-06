# Phase 10: AI Grocery Insights

Written 2026-10-07. Covers the first user-facing use of the AI reasoning layer from Phases 9A to 9C: an optional explanation of a grocery list, on the list screen. Nothing else in the app uses the AI layer.

## 1. What was built

On the grocery list screen (SCR-05) a card above the items offers **AI insights**. It is one line until opened. Opened, it says what will be sent and has a **Get AI insights** button. Tapping it sends one request through `AppContainer.aiReasoningEngine` (Firebase AI Logic, App Check, Gemini) and shows the answer: a summary, the reasoning, and up to five suggestions, with a note that it was written by AI, can be wrong, changes nothing and is not medical advice.

The model explains a list the app already generated. It does not generate, change or re-check anything.

## 2. Architecture

    ui/screens/grocerylist/
      AiInsights.kt          AiInsightsUiState, AiInsightsFailure, AiInsightsContext (the mapper)
      AiInsightsCard.kt      the card: offer, loading, answer, failure
      GroceryListViewModel   owns the request; state.insights
    domain/ai/AiRequest.kt   AiGroceryContext gained listTotal, flaggedItems,
                             listConsiderations, notCheckedByRules (all defaulted)
    data/ai/AiJson.kt        serialises the new fields; instructions extended

- The ViewModel depends on `AiReasoningEngine` only. `AppViewModelFactory` is the one UI file that takes the engine from the container. No UI file names `data/ai` or Firebase.
- The response model, schema, parser and `AiResponseValidator` are unchanged. The order of the suggestions stands in for priority; the model is asked to put the most important first.
- Nothing is stored. Insights live in the ViewModel's state and are dropped when the list or the profile changes, so an answer never stays on screen describing a list that has since been edited. Marking an item bought does not drop them.
- The two guard tests that forbade any UI file from naming the AI layer were narrowed: only `ui/screens/grocerylist/` may name `domain.ai`, and nothing outside the `ai` packages but `AppContainer` may name `data.ai`.

## 3. What is sent

Built by `AiInsightsContext.from` from the state already on screen:

| Sent | Source |
|---|---|
| Household size, budget, list total, currency | profile, list, the app's own sum |
| Allergy and health-condition names, listed and typed | profile |
| Item name, category, quantity, unit | list and catalog |
| Nutrition score, band, days, coverage percent, missing food groups | `NutritionAnalyzer` |
| Flagged items with the reason and whether the user kept them | `ConflictAnalysis` |
| List-wide considerations; allergies and conditions no rule covers | `ConflictAnalysis` |

Not sent: name, email, account id, region, list id, unit prices, bought state, catalog allergen data, any database object. A test checks the prompt for these.

**Design decision: disclosure instead of a consent screen.** Allergies and health conditions leave the device, so the card states this before the button is shown, and nothing is sent until the button is tapped. A stored consent setting was not added.

## 4. Safety

- The instructions now say the context is authoritative and must not be contradicted, figures must not be invented, a flagged item must never be described as acceptable, a food containing a listed allergy must never be suggested, unchecked entries must be reported as unchecked, and missing data must be stated rather than guessed. The Phase 9A rules (no medical advice, no "safe" or "unsafe", no diagnosis) stand.
- Instructions are a request. What is enforced: every answer passes `AiResponseJson.parse` and `AiResponseValidator`, a rejected answer is discarded whole, and no code path leads from an answer to the list, the review, the nutrition figures or the database. The allergy badge and the review screen show the deterministic result whatever the model says.

## 5. Failure behaviour

| Cause | Shown |
|---|---|
| No connection, or no answer within 30 seconds | "Couldn't reach AI insights. Check your connection and try again." |
| No Firebase configuration, App Check rejection, provider error, empty, malformed or rejected answer, unexpected exception | "AI insights aren't available right now. Your list is not affected." |

Both have **Try again**. The list, search, filter, bought state, totals and editing work throughout.

## 6. Deviations from the PDFs

| PDF | Suggestion | Decision |
|---|---|---|
| SCR-05 has no AI element | Add the entry point as a card in the item list rather than a new screen or a top-bar icon | Done. Collapsed to one line by default so it costs the list about one row; it scrolls away with the items |

## 7. Tests

`ui/AiInsightsTest` (21 tests, fake engine, no network): the request context and what it leaves out; a kept item stays flagged; the instructions; idle, loading, answer; every failure kind; retry; an engine that throws; timeout; parsing through the real `FirebaseAiReasoningEngine` with malformed, incomplete and rejected answers; an answer that contradicts the review changes nothing; the list works when AI is unavailable; insights are dropped on a list or profile change; nothing is sent without a list or items; the card's four states; wording.

Existing tests adjusted: `AiFoundationTest` (context keys, the dependency guard), `FirebaseAiLayerTest` (the factory may take the engine), and the three tests that construct `GroceryListViewModel`.

## 8. Device check (2026-10-07)

Debug build on a physical phone, existing generated list of 13 items: the card opened, showed the data note, showed loading, and showed a live answer from Gemini. The total, remaining budget and items were unchanged.

## 9. Known limitations

1. **Answer quality.** The live answer contained a garbled sentence ("a nutrition score of 77 out of 30 days covered"). The validator checks shape, length and banned wording, not sense or agreement with the context. A check of numbers in the answer against the context would be the next step.
2. The banned-wording check is a short substring list, as noted in Phase 9B.
3. No stored consent, no rate limiting, no caching: every tap is one request against the project's quota.
4. The answer is in English whatever the device language.
5. The insights offer is lost if the card is scrolled far off screen while expanded, and insights are lost on leaving the screen.
6. App Check enforcement is still off in the console and becomes mandatory on 2026-11-02 (Phase 9B, section 6).
