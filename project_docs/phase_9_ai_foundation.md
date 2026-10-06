# Phase 9A: AI Foundation (Gemini Proof of Concept)

Written 2026-10-06. Covers Phase 9A only: an AI abstraction and proof that a controlled request can reach a Gemini Flash model and come back as a validated, structured answer. No screen, navigation, database or generation behaviour changed. Phase 10 and later are not started.

Each statement below is marked as one of:

- **Requirement**: asked for by the project owner.
- **Design decision**: chosen during this phase; can be revisited.
- **Future possibility**: not built and not committed to.

## 1. Goal

**Requirement.** Prove that NutriCart can communicate with a Gemini Flash model without exposing an API key and receive a machine-readable reasoning response, with the model placed above the existing deterministic logic rather than in place of it.

The five points to demonstrate, and where each is shown:

| # | Point | Shown by |
|---|---|---|
| 1 | A controlled request can be constructed | `AiSampleContext`, `AiRequestJson`; serialization tests |
| 2 | The request reaches Gemini through a secure development path | `GeminiLiveCheck`, run on 2026-10-06 (section 5) |
| 3 | Gemini returns the expected structure | The same run; the answer matched the schema |
| 4 | The answer is parsed and validated | `AiResponseJson`, `AiResponseValidator`; 24 offline tests |
| 5 | The deterministic layer stays independent and authoritative | Three tests in `AiFoundationTest` (section 3) |

## 2. Why AI is being added

**Requirement.** NutriCart is to become an AI-powered personalised grocery and nutrition planning assistant. The deterministic engine produces figures and flags; it cannot explain trade-offs, prioritise among several findings, or word a recommendation for a particular household. Those are the parts a language model is suited to.

**Design decision.** The model is given results, not raw responsibility: it reads what the engine computed and returns text for a person to read.

## 3. Existing deterministic layer

**Requirement.** These are unchanged and remain the source of truth: `GroceryGenerator`, `NutritionCalculator`, `NutritionScorer`, `NutritionInsights`, `NutritionAnalyzer`, `ProfileConflicts`, `AllergyMatcher`, `HealthConditionAnalyzer`, `ConflictAnalyzer`. No file outside the two new `ai` packages was edited.

Enforced by tests:

- `deterministicCodeAndUi_doNotDependOnTheAiLayer` scans every main source file outside `domain/ai` and `data/ai` and fails if one imports either package.
- `aiAnswer_doesNotChangeTheDeterministicAnalysis` feeds the layer an answer that contradicts the nutrition score and checks the app's analysis is unchanged.
- `context_nutritionFiguresAreTheAnalyzersOwn` checks the figures sent to the model are exactly `NutritionAnalysis`'s.

The dependency runs one way: `domain/ai` reads `domain/nutrition` (to copy figures); nothing reads `domain/ai`.

## 4. New AI abstraction

    domain/ai/                      pure Kotlin, provider-neutral
      AiReasoningEngine.kt          interface: suspend fun analyze(AiRequest): AiResult
      AiRequest.kt                  AiRequest, AiTask, AiGroceryContext, AiListItem, AiNutritionSummary
      AiResponse.kt                 AiResponse, AiResult, AiError, AiResponseValidator
      AiRecommendation.kt           AiRecommendation, AiRecommendationType

    data/ai/
      AiJson.kt                     AiRequestJson (instructions, prompt, context JSON, response schema)
                                    AiResponseJson (text -> AiResponse, or a typed failure)
      GeminiAiDataSource.kt         Gemini's wire format; AiHttpTransport and its HttpURLConnection implementation
      GeminiAiRepository.kt         AiReasoningEngine implemented with the above

**Design decisions.**

- Callers see only `AiReasoningEngine`. `GeminiAiDataSource` is the single file that knows Gemini's request and reply shapes; another provider means another data source and repository, with `AiJson.kt` reusable as it is.
- Expected failures are values, not exceptions: `AiResult.Failure(AiError, detail)` with `NotConfigured`, `Network`, `Api`, `EmptyResponse`, `MalformedResponse`, `InvalidResponse`.
- The HTTP call sits behind `AiHttpTransport` so tests answer with canned replies.
- The engine is **not** registered in `AppContainer`, has no ViewModel and no screen. Nothing in the running app can call it.
- `AiNutritionSummary.from(NutritionAnalysis)` copies figures and calculates nothing.

## 5. Gemini integration approach

**Requirement.** Use Google's currently recommended approach, checked against the project's configuration; prefer structured JSON output.

**What was checked (Google's documentation, 2026-10-06).**

- The **Interactions API** (`POST https://generativelanguage.googleapis.com/v1beta/interactions`) has been generally available since June 2026 and is the one recommended for new projects. `generateContent` is still supported and is now the older path.
- Current Flash models: `gemini-3.8-flash` (recommended default), `gemini-3.7-flash`, `gemini-3.6-flash`, `gemini-3.5-flash`, `gemini-3.5-flash-lite`. The 2.x models are closed to new projects.
- Google's key guidance: do not put keys in web or mobile apps; use a backend proxy; read keys from environment variables.
- Google's client SDKs (`google-genai` for Java, Python, JavaScript, Go) are meant for servers and trusted machines. The earlier Android client SDK is deprecated.

**Design decisions.**

- **Interactions API over plain REST**, using `HttpURLConnection` and `org.json`, both already available to the project. **No dependency was added** and no version was changed. An SDK would add a large dependency to an app that must not call the model directly anyway.
- **Model:** `gemini-3.8-flash` by default, a constructor argument of `GeminiAiDataSource`.
- **Structured output:** `response_format` with `mime_type: application/json` and a JSON Schema generated from the app's own types (section 7).
- **`store: false`**, so Google does not keep the exchange for later retrieval.
- The key is sent in the `x-goog-api-key` header, never in the URL, so it cannot end up in a logged URL or an error detail.

**Status of the live proof.** `GeminiLiveCheck` was run from this development machine on 2026-10-06 with the key taken from the `GEMINI_API_KEY` environment variable:

| Model | Result |
|---|---|
| `gemini-3.8-flash` | Reached; HTTP 503 "currently experiencing high demand" on three attempts. Reported by the layer as `AiError.Api` |
| `gemini-3.7-flash` | The same 503 |
| `gemini-3.5-flash-lite` | **Success.** A schema-conforming answer, parsed and accepted by the validator |

The accepted answer (verbatim):

    summary: The grocery plan has low coverage in several food groups and nutritional metrics.
    reasoning: The plan covers 30 days for a household of four, but currently lacks items from the
      Fruit and Dairy groups. Additionally, energy, protein, carbohydrate, and iron coverage
      percentages are relatively low, and the nutrition score is in the low band.
    recommendations:
      - variety: Add Missing Food Groups. Incorporate items from the Fruit and Dairy groups to
        improve overall variety and nutritional completeness.
      - nutrition: Increase Nutrient Coverage. Boost quantities or select options that increase
        energy, protein, carbohydrate, and iron coverage percentages to raise the nutrition score
        from the current low band.

Every fact in it comes from the supplied context; it names no item outside the list and states no price. The communication path is therefore proven end to end. The default model itself has not yet returned an answer, only capacity errors; that is a service condition, not something the code can settle.

**The test request.** `AiSampleContext` (test sources): a made-up household of 4, 8,000 BDT, a peanut allergy, prediabetes, and eight demo-catalog items (white rice, lentils, eggs, spinach, potato, onion, tomato, white sugar). The nutrition block is what `NutritionAnalyzer` returns for that list: score 42 (Low), coverage of energy 45%, protein 38%, carbohydrate 62%, fat 7%, iron 32%, with Fruit and Dairy missing. No real user data is involved.

## 6. Security considerations

**Requirements.** No key in source, `BuildConfig`, resources, `local.properties` or any committable file; no APK that carries a permanent key.

**How Phase 9A meets them.**

| Measure | Detail |
|---|---|
| No key in the app | `GeminiAiDataSource` takes the key as a function argument. Nothing in `src/main` supplies one |
| No path to the network | The manifest has no `INTERNET` permission, so the installed app could not call Gemini even if it tried. The app is still local-only |
| No wiring | Not in `AppContainer`; no screen or ViewModel references the layer |
| Live call is development-only | `GeminiLiveCheck` is in `app/src/test`, which is never packaged. It reads `GEMINI_API_KEY` from the process environment and never prints it |
| Off by default | The live check is skipped unless `NUTRICART_AI_LIVE=1`; `gradlew test` makes no network call |
| Guard test | `app_hasNoKeyAndNoNetworkPermission` fails if the manifest gains `INTERNET`, if a Google-style key or the variable name appears under `src/main`, or if `app/build.gradle.kts` mentions the key |
| `.gitignore` | Now also ignores `.env` files, `secrets.properties`, keystores, certificates and service-account files |
| Data minimisation | The context has no name, email, account id or region |

**A backend is required before any release.** Any key placed in an APK can be extracted, whatever obfuscation is used. Before the app itself talks to a model, one of these has to exist:

1. **Future possibility: a backend proxy.** The app calls NutriCart's own server; the server holds the key, authenticates the user, rate-limits and calls Gemini. This is what Google's key guidance recommends.
2. **Future possibility: a managed client gateway** such as Firebase AI Logic with App Check, where the key stays with Google and the app is attested. This would bring Firebase into a project that currently has none.

Neither was built: no backend exists and production deployment is out of scope. `AiReasoningEngine` is the seam; a `BackendAiRepository` would implement it and the Gemini data source would move to the server.

## 7. Structured response schema

**Requirement.** Machine-readable, minimal.

    {
      "summary": string,
      "reasoning": string,
      "recommendations": [
        { "type": "budget" | "nutrition" | "variety" | "other", "title": string, "explanation": string }
      ]
    }

All fields are required and no others are allowed. The schema sent to Gemini is generated by `AiRequestJson.responseSchema()` from `AiRecommendationType` and the validator's limits, so the two cannot drift apart.

**Design decision.** The app does not rely on the provider's enforcement. `AiResponseJson` and `AiResponseValidator` check every answer again:

| Check | Failure |
|---|---|
| Text is JSON; each field is present and a real string, array or object | `MalformedResponse` |
| `type` is one of the four known values | `InvalidResponse` |
| No blank text; summary ≤ 400, reasoning ≤ 1,500, title ≤ 80, explanation ≤ 600 characters | `InvalidResponse` |
| 1 to 5 recommendations | `InvalidResponse` |
| No "safe" (which covers "unsafe"), "cure", "diagnos", "medically" or "guarantee" in any text | `InvalidResponse` |

An answer that fails is discarded whole. It is never repaired or shown in part. A recommendation carries no item id, quantity or price, so it cannot be executed; it can only be read.

## 8. What Gemini is allowed to reason about

**Requirement.** Reasoning, prioritisation, explanation, trade-off analysis and personalised recommendations, over facts the app supplies: household size, budget, the user's stated allergies and conditions (as constraints), the list's items and quantities, and the nutrition figures the engine computed.

In Phase 9A it does one thing: comment on a supplied list.

## 9. What Gemini is NOT allowed to decide

**Requirement.** Gemini is not the source of truth for prices, allergens, nutrition calculations, medical facts, catalog availability or hard safety constraints.

How that is held:

| Area | Mechanism |
|---|---|
| Prices | Not sent. The answer has no numeric field. The instructions forbid stating or estimating them |
| Allergens | Catalog allergen data is not sent. The model sees the user's allergy names only, as constraints |
| Nutrition figures | Computed by `NutritionAnalyzer` and passed in. Nothing in an answer flows back into the analysis |
| Medical facts and advice | Forbidden by the instructions; no recommendation type exists for it; the wording check rejects the common phrasings |
| Catalog availability | The instructions forbid naming a food item that is not in the supplied list |
| Hard constraints | Applied by `ProfileConflicts` and `ConflictAnalyzer` before and after any model involvement, not by the model |

Instructions to a model are a request, not a guarantee. The mechanisms that hold regardless of what the model writes are the ones in the app: the facts withheld, the schema, the validator, and the absence of any code path from an answer to stored data.

## 10. Future Phase 10+ architecture

All **future possibilities**; none is designed in detail or agreed.

    Profile + list + catalog
        -> deterministic engine (generation, nutrition, conflicts)      authoritative
        -> AiGroceryContext (figures and findings, no personal data)
        -> AiReasoningEngine  -> backend proxy -> Gemini
        -> validated AiResponse
        -> deterministic re-check of anything actionable
        -> UI, clearly marked as AI-written

- Add the `ConflictAnalysis` findings to the context, so the model explains what the rules flagged instead of forming its own view (the extension point noted in `phase_8_allergy_health.md`, section 17).
- A backend proxy and a `BackendAiRepository`; only then an `INTERNET` permission and an entry in `AppContainer`.
- Recommendations that refer to catalog items by id, each re-checked by `ConflictAnalyzer` and priced from the catalog before it is shown.
- User consent before any profile data leaves the device, and a clear label on AI-written text.
- Retry with backoff and a fallback model for capacity errors; caching; an offline state.
- A screen for the answers. The app works fully without the AI layer and should continue to.

## 11. Known limitations

1. The app cannot call the model. The proof runs only as a development test on a machine that has the key.
2. `gemini-3.8-flash`, the default, returned only HTTP 503 capacity errors during the proof; the successful answer came from `gemini-3.5-flash-lite`. There is no retry or fallback in the layer.
3. One task, one fixed prompt. The prompt has not been tuned or evaluated beyond this single request.
4. The wording check is a short substring list. It will reject some harmless text ("safely") and cannot catch every unwanted claim.
5. Nothing checks an answer against the context for invented facts beyond what the schema and instructions achieve. A model could still write a wrong figure in prose; this matters once answers are shown to users.
6. The context omits the conflict findings, prices and the budget spent, so the model cannot yet reason about cost.
7. The Interactions API request and reply shapes were taken from Google's documentation and confirmed by one successful live call, not by a contract test.
8. Sending allergies and health conditions to a third party is a privacy decision. Only made-up data was sent in this phase; consent and a privacy notice are needed before real profiles are.
9. Usage is billed to the developer's key; there is no quota handling.

## Tests

| File | Tests | Covers |
|---|---|---|
| `data/ai/AiFoundationTest` | 24 | Request serialization (context contents, nutrition figures, null nutrition, determinism, endpoint, header-only key, schema, `store: false`); response parsing (valid answer, text split across parts); malformed answers (not JSON, truncated, non-JSON envelope, missing or mistyped fields); empty answers; network failure; API errors and unfinished interactions; missing key sends nothing; validation (blank, over length, none or too many recommendations, unknown type, safety and medical wording); independence of the deterministic layer; no key and no network permission in the app |
| `data/ai/GeminiLiveCheck` | 1, skipped by default | The live call described in section 5 |

To run the live check (PowerShell, with `GEMINI_API_KEY` already set):

    $env:NUTRICART_AI_LIVE = "1"
    .\gradlew.bat testDebugUnitTest --tests "com.example.nutricart.data.ai.GeminiLiveCheck"

`$env:NUTRICART_AI_MODEL` optionally names another model. The answer is in `app/build/test-results/testDebugUnitTest/`.
