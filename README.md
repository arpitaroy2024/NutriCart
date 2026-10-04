# NutriCart

NutriCart is an Android application designed to help users plan groceries and make more informed food and nutrition decisions based on their personal preferences, household information, budget, allergies, and health-related considerations.

The project is being developed as a local-first Android application with a focus on a clean, accessible, and practical user experience.

## 🚧 Development Status

> **Status: Active Development**

NutriCart is being developed in multiple implementation phases.

### Progress

- [x] Phase 0 — Project Foundation
- [ ] Phase 1 — Design System & Reusable Components
- [ ] Phase 2 — Local Storage
- [ ] Phase 3 — Authentication & Onboarding
- [ ] Phase 4 — Profile & Home
- [ ] Phase 5 — Grocery Generation
- [ ] Phase 6 — Grocery List Editing
- [ ] Phase 7 — Nutrition Analysis
- [ ] Phase 8 — Allergy & Health Alerts
- [ ] Phase 9 — Polish & Final Testing

## ✨ Planned Features

- Personalized grocery planning
- Budget-aware grocery recommendations
- Grocery list generation and management
- Grocery item details
- Nutrition analysis
- Allergy and health alerts
- Personalized user profiles
- Local/offline account management
- Household and dietary preferences
- Bangladesh-focused grocery data and pricing
- AI-inspired grocery recommendations using an on-device rule-based approach

## 🛠️ Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **Material 3**
- **Android SDK**
- **Gradle**
- **Android Studio**
- **Room** — planned local database
- **DataStore** — planned local preferences/session storage

## 🏗️ Architecture

NutriCart is being developed with a local-first architecture.

The planned application structure separates:

- UI and reusable Compose components
- Navigation
- Local data storage
- Authentication
- Domain/business logic
- Nutrition and recommendation logic

The application does not depend on Firebase or a remote backend for its core account and grocery functionality.

## 🔐 Authentication & Privacy

NutriCart is planned to support local/offline accounts.

The planned authentication flow includes:

- Local account registration
- Email and password login
- Local session persistence
- Account-specific grocery lists and budgets
- Logout
- Password hashing rather than storing plaintext passwords

No Firebase or external authentication service is planned for the core implementation.

## 📱 Platform

**Android**

The application is built using Jetpack Compose rather than XML-based layouts.

## 📚 Project Documentation

The `project_docs/` directory contains the project's design and implementation documentation, including:

- NutriCart screen specifications
- UI flow documentation
- Implementation plan
- Phase-specific implementation documentation

The two primary design documents are:

- `406_NutriCart_Screen_Details.pdf`
- `406_NutriCart_UI_Flow.pdf`

These documents define the intended screens, UI behavior, navigation, dimensions, and design requirements.

## 📂 Project Structure

```text
NutriCart/
├── app/
│   └── src/
├── gradle/
├── project_docs/
├── CLAUDE.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```
