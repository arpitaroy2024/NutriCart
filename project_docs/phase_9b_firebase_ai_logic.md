# Phase 9B: Firebase AI Logic and App Check Foundation

Written 2026-10-07. Covers Phase 9B only: the infrastructure for the Android app to reach Gemini through Firebase AI Logic, protected by Firebase App Check. No user-facing AI feature, screen, navigation or database change is included, and Phase 10 is not started.

**Status: the code is complete and builds; the connection to Firebase is not live.** No Firebase project exists yet, so no request has travelled from the app to Gemini. That needs the manual actions in section 4.

Each statement is marked as one of:

- **Requirement**: asked for by the project owner.
- **Design decision**: chosen during this phase; can be revisited.
- **Manual action**: something only the project owner can do, in the Firebase console or on a device.
- **Future possibility**: not built and not committed to.

## 1. Goal

**Requirement.** Give the app a secure, production-appropriate path to Gemini:

    Android NutriCart
        -> Firebase AI Logic  (App Check token verified here)
        -> Gemini
        -> structured answer
        -> AiResponseJson + AiResponseValidator
        -> existing deterministic NutriCart logic stays in charge

**Requirement.** No permanent Gemini API key in the APK. No AI feature in front of the user yet. The app keeps working offline.

## 2. Why Firebase AI Logic was selected

**Requirement.** The project owner chose Firebase AI Logic with App Check.

**Why it fits.** Phase 9A concluded that a key placed in an APK can always be extracted, so the app needed either its own backend proxy or a managed gateway. Firebase AI Logic is the managed gateway: Google runs the proxy, holds the authorisation for Gemini, and verifies that a request comes from the real app. NutriCart has no server to build, run or secure.

**What it costs.** The app is no longer free of Google services: it now includes Firebase libraries, declares the `INTERNET` permission and depends on Google Play services for release-build attestation (section 14).

**Checked against the current documentation (2026-10-06 and 07).** Firebase AI Logic is the current product; the earlier "Vertex AI in Firebase" SDK instructions were not used. The library is `com.google.firebase:firebase-ai`, used with `GenerativeBackend.googleAI()` for the Gemini Developer API, which is available on the no-cost Spark plan.

## 3. Architecture

    domain/ai/                       unchanged; no Firebase types
      AiReasoningEngine              the only thing callers may depend on

    data/ai/
      AiJson.kt                      unchanged: instructions, prompt, parser
      AiModels.kt                    NEW: the one place a model is named
      GeminiAiDataSource/Repository  Phase 9A development path, kept

    data/ai/firebase/                NEW
      FirebaseAiReasoningEngine.kt   AiReasoningEngine; also AiTextGateway and AiGatewayException
      FirebaseAiLogicGateway.kt      the only file that uses the Firebase SDK;
                                     also FirebaseAiSchema and FirebaseAiErrors
      AppCheckProvider.kt            one copy in src/debug, one in src/release

    AppContainer.aiReasoningEngine   created lazily; used by nothing yet

**Design decisions.**

- **Two classes, one seam.** `FirebaseAiReasoningEngine` builds the prompt, calls an `AiTextGateway`, and parses and validates the text. It imports nothing from Firebase. `FirebaseAiLogicGateway` implements the gateway with the SDK. Firebase's `GenerativeModel` is a final class that cannot be faked, so this split is what lets the engine be tested without Firebase.
- **Same prompt, same parser as Phase 9A.** Both provider paths use `AiRequestJson` and `AiResponseJson`, so an answer is judged identically whichever path produced it.
- **Lazy, and off the start-up path.** `NutriCartApp` and `MainActivity` do not mention Firebase. The gateway installs App Check and creates the model only when `analyze` is first called. Since no screen calls it, the app's behaviour at start-up and in every existing flow is unchanged.
- **Failures are values.** The engine returns `AiResult.Failure` and never throws for a missing configuration, a network problem, an App Check rejection or a bad answer. Coroutine cancellation is passed through.
- **Composition root only.** `AppContainer` is the single file outside the `ai` packages that names the AI layer. Tests enforce this.

Dependency direction: `ui` and `domain` know nothing of `data/ai`; `data/ai/firebase` depends on `domain/ai` and on Firebase; Firebase types appear nowhere else.

## 4. Firebase configuration

**What is in the project.**

| Item | Detail |
|---|---|
| Firebase BoM | `com.google.firebase:firebase-bom:34.19.0` |
| Libraries | `firebase-ai` (all builds), `firebase-appcheck-debug` (debug only), `firebase-appcheck-playintegrity` (release only) |
| Gradle plugin | `com.google.gms.google-services` 4.5.0, declared in the root build file and applied in `app/build.gradle.kts` **only when `app/google-services.json` exists** |
| Effect on existing libraries | One transitive change: `androidx.concurrent:concurrent-futures` 1.1.0 to 1.2.0. No version in `libs.versions.toml` was changed and there were no conflicts |
| Compatibility | Firebase needs min SDK 23; the project is 24. The plugin was run once against a placeholder configuration to confirm it works with AGP 9.3.1; the placeholder was deleted |

**Design decision: the conditional plugin.** The google-services plugin fails the build when its configuration file is missing. Applying it only when the file exists keeps a fresh clone buildable. Without the file there is no Firebase app at run time and the engine returns `AiError.NotConfigured`.

**`google-services.json`.**

- It belongs at `app/google-services.json` (the module root, next to `app/build.gradle.kts`).
- It must be the file for an Android app registered with package name `com.example.nutricart`; the plugin fails the build if the package does not match.
- It holds project identifiers and a **Firebase API key**. That is not a Gemini key: it identifies the Firebase project and cannot call Gemini on its own authority. Firebase describes the file as "unique, but non-secret identifiers".
- **Design decision:** it stays in `.gitignore` (added in Phase 9A). The repository is public, and until App Check is enforced the identifiers in the file are enough for someone else to send requests against the project's quota. This can be relaxed once enforcement is on.

**Manual actions (Firebase console).** Nothing below was done or can be done from this repository.

1. Go to the Firebase console and create a project (or pick an existing one). Analytics is not needed.
2. Add an Android app. Package name: `com.example.nutricart`. Nickname is optional. The debug signing certificate's SHA-1 is not needed for these features.
3. Download `google-services.json` and put it at `E:\NutriCart\app\google-services.json`. Skip the console's "add the SDK" steps; they are already done. Do not commit the file.
4. In the console open **AI Services > AI Logic**, click **Get started**, choose **Gemini Developer API**, and complete the workflow. It enables the two APIs that are needed (Gemini Developer API and Firebase AI Logic API). **Do not copy any Gemini key into the app or the repository**; none is needed.
5. Open **Security > App Check > Apps**, select the Android app and register it. For now choose **Play Integrity** and enter the SHA-256 of the debug keystore if the console requires one; the debug provider below is what debug builds actually use.
6. Build and run the debug app, trigger one AI request with the live check (section 10), find the line `DebugAppCheckProvider: Enter this debug secret into the allow list ...` in logcat, and add that token under **App Check > Apps > (the app) > overflow menu > Manage debug tokens**.
7. Leave App Check for Firebase AI Logic in **monitoring (unenforced)** mode until the request metrics show verified requests (section 6).

## 5. Gemini authorisation

**Requirement.** No Gemini API key in the APK.

**How Firebase handles it (verified in Firebase's documentation).** "When using the Firebase AI Logic SDKs, you do not need a Gemini API key for authorizing use of the Gemini Developer API, and you should not add a Gemini API key into your app's codebase." Firebase AI Logic authorises its calls to the Gemini Developer API with a Google-managed service account that Firebase provisions in the project. The app sends its request to Firebase with the Firebase project identifiers and an App Check token; the credential that reaches Gemini never leaves Google's servers.

**Consequences.**

- The `GEMINI_API_KEY` environment variable from Phase 9A has no role in the app. It is read only by `GeminiLiveCheck`, on a development machine.
- There is nothing to rotate inside a released APK.
- What protects the project's quota is App Check, not secrecy of the configuration file.

## 6. App Check

**Requirement.** Set up App Check, separate development from production, commit no debug token, and do not enforce blindly.

| | Development (debug builds) | Production (release builds) |
|---|---|---|
| Provider | `DebugAppCheckProviderFactory` | `PlayIntegrityAppCheckProviderFactory` |
| Source | `app/src/debug/.../AppCheckProvider.kt` | `app/src/release/.../AppCheckProvider.kt` |
| Library scope | `debugImplementation` | `releaseImplementation` |
| How it proves itself | A token generated on the device and registered by hand in the console | Google Play attests the app binary and the device |
| Works on | Any device or emulator | Devices with Google Play services |

**Design decisions.**

- The provider is chosen by the build type's source set, not by an `if (BuildConfig.DEBUG)`. A release build does not contain the debug provider class at all; a test checks the Gradle scopes.
- The provider is installed inside the gateway, once per process, immediately before the first Firebase AI call. It is not installed at app start.
- `useLimitedUseAppCheckTokens = true` is set, as Firebase recommends for AI Logic, so the project is ready for replay protection.
- **The debug token is never in a file.** The SDK generates it on the device, stores it in the app's private storage and prints it to logcat once. Treat it as a secret: anyone holding it can pass App Check as this app.

**Enforcement: the documentation differs from the brief.** The brief says not to enable enforcement blindly, and that is followed: nothing was enforced. However, Firebase states: "Starting November 2, 2026, Firebase App Check enforcement will be required to use Firebase AI Logic." So leaving it unenforced is possible only for a few more weeks.

**Manual action.** After the debug token is registered and one request succeeds, check **App Check > APIs > Firebase AI Logic** for verified requests, then set it to **Enforced**. Enforcement can be switched off again in the console; it is not irreversible, but while it is on, any build without a valid token is refused.

**Manual action for a release build (later).** Register the release signing certificate's SHA-256 in App Check, and link the Google Cloud project in Play Console (**Release > App integrity > Play Integrity API**). Play Integrity does not require the app to be published on Google Play, but the app must be configured there.

## 7. Model selection

**Requirement.** A stable, currently supported Flash-class model available through Firebase AI Logic; do not assume Phase 9A's choice; keep the name in one place.

**Stable models Firebase AI Logic currently lists for the Gemini Developer API:**

| Model | Notes | Observed |
|---|---|---|
| `gemini-3.8-flash` | The recommended general default; described as built for long-horizon software engineering. No retirement date announced | HTTP 503 "high demand" on 2026-10-06 (three attempts) and again on 2026-10-07 |
| `gemini-3.5-flash` | Retirement no earlier than 2027-05-19 | HTTP 503 on 2026-10-07 |
| `gemini-3.5-flash-lite` | Lowest cost and highest availability; retirement no earlier than 2027-07-21 | Answered with a valid structured response on both days |

**Design decision: `gemini-3.5-flash-lite`.** It is stable, supported by both provider paths, and the only one of the three that has actually answered NutriCart's request, twice. The task is a short structured commentary over a small context, which does not need the most capable model. Phase 9A's default (`gemini-3.8-flash`) was replaced.

**Caveat.** The observations were made through the Phase 9A direct path with a personal developer key. Availability through Firebase AI Logic may differ, and it has not been measured.

**Centralised.** `data/ai/AiModels.GEMINI_FLASH` is the only model name in the source; both `GeminiAiDataSource` and `FirebaseAiLogicGateway` read it, and a test fails if another file names a model. No fallback logic was added.

**Future possibility.** Firebase recommends Remote Config for changing the model name without an app release. Not added: it is another Firebase product and there is no feature to protect yet.

## 8. Structured response handling

**Requirement.** Every answer becomes an `AiResponse` and passes validation before anything else sees it.

- The gateway asks for `application/json` with a response schema (`FirebaseAiSchema`), built from `AiRecommendationType` and `AiResponseValidator.MAX_RECOMMENDATIONS`: `summary`, `reasoning`, and 1 to 5 `recommendations` of `{type, title, explanation}`, all required.
- The schema is a request to the model. The check that counts is the app's own: `FirebaseAiReasoningEngine` has one exit for model text, `AiResponseJson.parse`, which runs `AiResponseValidator`. A rejected answer is discarded whole.
- Tests feed the engine answers with safety or medical wording, an unknown type, no recommendations, a missing field and plain prose; each is rejected with the same error kinds as in Phase 9A.

**Requirement, unchanged from Phase 9A.** Gemini is not the source of truth for prices, allergens, nutrition calculations, medical facts, catalog availability or hard constraints. The context sent to it contains none of the catalog's prices or allergen data, an answer has no numeric field, and no code path leads from an answer to stored data.

## 9. Security

| Check | Result |
|---|---|
| Gemini API key in Kotlin, Gradle, resources, manifest, `BuildConfig`, `local.properties` | None. `AiFoundationTest.app_hasNoKey` scans `src/main` and the app build file |
| Key in the built APK | The debug APK was scanned: no Google-style API key and not the development key |
| Secrets committed | None. `google-services.json`, `.env` files and keystores are ignored |
| Debug App Check token | Generated on the device; in no file |
| Debug provider in release builds | Not on the release classpath |
| Logging | The layer logs nothing. A failure's detail is the exception's class name, never its message |
| Personal data | The request type has no name, email, account id or region. No real profile has been sent anywhere |

**Permissions changed.** The merged manifest now has `INTERNET` and `ACCESS_NETWORK_STATE`, which the Firebase libraries declare and the feature needs. Phase 9A's statement that the app has no `INTERNET` permission is no longer true. `firebase-ai` also declares `RECORD_AUDIO` for its voice features; NutriCart removes it with `tools:node="remove"` and a test checks that.

**What still stands between an attacker and the quota.** Until App Check is enforced, the project identifiers alone are enough to call Firebase AI Logic. Keep the configuration file out of the repository and enforce App Check once it is verified.

## 10. Development configuration

**Building without Firebase** (the state of the repository): everything builds and runs; the engine returns `NotConfigured`; logcat shows one line from Firebase saying no default options were found.

**Building with Firebase** (after section 4, steps 1 to 4): put `google-services.json` in `app/` and build. Nothing else changes.

**Proving the path on a device (manual action, after section 4).** `app/src/androidTest/.../FirebaseAiLiveCheck.kt` sends one made-up request through the app's own engine. It is skipped unless asked for:

    .\gradlew.bat connectedDebugAndroidTest `
        "-Pandroid.testInstrumentationRunnerArguments.class=com.example.nutricart.FirebaseAiLiveCheck" `
        "-Pandroid.testInstrumentationRunnerArguments.nutricartAiLive=1"

The first run prints the debug token (`adb logcat -s DebugAppCheckProvider`); register it (step 6) and run again. The answer is logged under the tag `NutriCartAiLive`. This check compiles but **has never been run**, because there is no Firebase project.

**Unit tests** never use the network. They delete any Firebase app before each test, so they behave the same whether or not `google-services.json` is present; this was confirmed with a placeholder file.

## 11. Production considerations

- **Manual action:** enforce App Check before the date in section 6, and register the release certificate and Play Integrity before shipping a release build.
- **Future possibility:** user consent and a privacy notice before any profile data (allergies, health conditions) leaves the device. Nothing sends real data today.
- **Future possibility:** quotas and cost. The Spark plan has rate limits; per-user limits can be set in the console. Billing needs the Blaze plan.
- **Future possibility:** retry with backoff for capacity errors, and Remote Config for the model name.
- **Design decision to revisit:** release builds have code shrinking off, so the Firebase libraries add roughly 3.4 MB to the debug APK (13.8 MB to 17.2 MB) and a similar amount to a release. Enabling shrinking would recover much of it.
- Devices without Google Play services cannot pass Play Integrity. On them the AI layer would fail and the rest of the app would work.

## 12. Phase 9A relationship

Phase 9A's code is kept and still passes its tests.

| | Phase 9A path | Phase 9B path |
|---|---|---|
| Engine | `GeminiAiRepository` | `FirebaseAiReasoningEngine` |
| Transport | REST to Google's Interactions API | Firebase AI Logic SDK |
| Authorisation | A developer key from the environment | Firebase service account plus App Check |
| Where it runs | A development machine, from `GeminiLiveCheck` | The app, through `AppContainer` |
| Shared | `AiReasoningEngine`, `AiRequestJson`, `AiResponseJson`, `AiResponseValidator`, `AiModels` | the same |

Changes to Phase 9A files: the model constant now reads `AiModels.GEMINI_FLASH`; a comment in `GeminiAiDataSource` was corrected; two guard tests were adjusted (`AppContainer` may now name the engine, and the `INTERNET` assertion was dropped because it is no longer true). The Firebase SDK uses Google's `generateContent` API internally rather than the Interactions API that Phase 9A calls; that is Firebase's choice and is hidden behind the gateway.

## 13. What Phase 10 will eventually do

All **future possibilities**; nothing here is designed or agreed.

- A first user-facing use of `AppContainer.aiReasoningEngine`, through a ViewModel that depends on `AiReasoningEngine` only, with a visible state for "AI unavailable".
- A context built from the user's real list by a mapper, including the `ConflictAnalysis` findings so the model explains what the rules flagged.
- Consent, and a clear label on AI-written text.
- A deterministic re-check of anything actionable before it is shown.

## 14. Known limitations

1. **Not live.** No request has gone from the app to Firebase or Gemini. `FirebaseAiLogicGateway`'s SDK calls compile against `firebase-ai` 17.17.0 but are unexercised.
2. **Device check incomplete.** The debug build was installed and launched on a physical device without a crash, and Firebase's "not configured" line was seen. Login, navigation, profile, generation, editing, nutrition and the allergy review were not checked by hand on the device in this phase; they are covered by the unit suite, which drives the real navigation graph.
3. The app now requires the `INTERNET` permission and bundles Google libraries, although no user flow uses the network.
4. The chosen model was picked partly on availability seen through a different access path (section 7).
5. The validator's wording check is a short substring list. A live answer on 2026-10-07 suggested "higher fiber alternatives" to "align the plan with the user's health conditions": it passed validation and is close to dietary advice. Stronger checks are needed before answers are shown to users.
6. No retry, fallback model, caching or rate limiting.
7. `FirebaseAiErrors` groups most Firebase failures as `Api`; an App Check rejection is not distinguished from a quota error.
8. The App Check provider is installed lazily. If another Firebase product is ever added and used earlier, installation must move ahead of it.
9. App Check enforcement becomes mandatory on 2026-11-02 (section 6).

## Tests

| File | Tests | Covers |
|---|---|---|
| `data/ai/firebase/FirebaseAiLayerTest` | 20 | Provider abstraction (use through the domain interface, same prompt as Phase 9A, the container's engine); validation cannot be bypassed (wording, schema, empty text); failure handling (every error kind, unexpected exceptions, translation of Firebase's exceptions, no message leakage); Firebase unavailable (the real engine returns `NotConfigured`, deterministic analysis unchanged); one model name; boundaries (Firebase types only in `data/ai/firebase`, none in `domain`, `ui` or `navigation`, engine free of Firebase imports, nothing at app start); debug provider only in debug builds; microphone permission removed |
| `data/ai/AiFoundationTest` | 24 | Phase 9A, with two guard tests adjusted (section 12) |
| `androidTest/FirebaseAiLiveCheck` | 1, opt-in | The device check in section 10; not yet run |
