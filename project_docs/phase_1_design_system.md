# NutriCart — Phase 1: Design System & Reusable UI Components

**Date:** 2026-10-04
**Phase:** Phase 1 — Design system and component library
**Status:** Implemented; build, unit test and lint pass. Not yet visually checked (see Verification).

## 1. What was implemented

- Layout constants (spacing, radii, elevation, sizes, motion) alongside the colour and type tokens from Phase 0.
- The thirteen reusable components C-01 to C-13 from Screen Details slide F-03.
- Five supporting components the PDFs use on more than one screen: top bar, round icon button, progress ring, empty state and the app logo.
- Twelve icon drawables.
- Light and dark previews for every component.

No screen was built or changed. Splash and Login are untouched and do not use the new components yet. The app behaves exactly as it did after Phase 0.

## 2. Design tokens

All tokens are in `ui/theme/`.

| Group | Where | Values |
|---|---|---|
| Colour | `Color.kt` (Phase 0) | 15 tokens, light and dark, read through `NutriCartTheme.colors` |
| Typography | `Type.kt` (Phase 0) | 9 roles, read through `NutriCartTheme.typography` |
| Spacing | `Dimens.kt` → `Spacing` | 4 / 8 / 12 / 16 / 20 / 24 / 32 dp; `gutter` = 20dp |
| Corner radii | `Dimens.kt` → `NutriCartShapes` | input 10dp, card 14dp, sheet 22dp (top corners), pill |
| Elevation | `Dimens.kt` → `Elevation` | card 0dp, docked bar 6dp, dialog 12dp |
| Touch target | `Dimens.kt` → `Sizes.touchTarget` | 48dp |
| Icon sizes | `Dimens.kt` → `Sizes` | inline 20dp, nav and app bar 24dp, alert and empty state 40dp |
| Component metrics | `Dimens.kt` → `Sizes` | filled button 56, outlined button 52, input 50, search field 44, stepper button 36, checkbox 22, radio 22, chip 32, filter chip 36, rail 8, bottom nav 72, top bar 56, alert bar 6, alert icon circle 28, logo 200, logo ring 4, outline 1, focused outline 2 (all dp) |
| Motion | `Dimens.kt` → `Motion` | progress 400ms, sheet 250ms, nav fade 150ms |

## 3. Components

All components are in `ui/components/`.

| ID | Composable | File | What it does |
|---|---|---|---|
| C-01 | `NutriFilledButton` | `Buttons.kt` | 56dp pill button in primary, full width, title/20sp label. Has `enabled`, a `loading` state that shows a spinner, and a `height` override. |
| C-02 | `NutriOutlinedButton` | `Buttons.kt` | 52dp pill with a 2dp border and label in the same colour. `color` gives the danger variant; optional leading icon, height, text style and non-full-width. |
| C-03 | `NutriCard` | `Cards.kt` | 14dp radius, `surface.card`, 1dp outline, 0dp elevation, 16dp padding. Optional `onClick`, container colour, shape, and a 6dp leading severity bar. |
| C-04 | `NutriTextField` | `Inputs.kt` | 50dp single-line field, 10dp radius, 1dp outline, 2dp primary when focused, title/20sp text. Supports placeholder, prefix, leading and trailing icons, keyboard options, visual transformation, and an error state with a message below. |
| C-04 (pill) | `NutriSearchField` | `Inputs.kt` | 44dp pill variant with a 20dp leading search icon and faint placeholder. |
| C-05 | `NutriDropdown` | `Inputs.kt` | 50dp field with a 20dp trailing chevron. Typing filters the options. Error state as C-04. |
| C-06 | `NutriStepper` | `Stepper.kt` | Minus and plus buttons (36dp circles on `surface.sunken`, 48dp hit area) with the value between. `min` / `max` bounds disable the buttons; optional caption under the value. |
| C-07 | `NutriCheckbox`, `NutriCheckboxRow` | `Selection.kt` | 22dp box, 6dp radius, primary when checked. The row variant makes the whole 48dp row the target. Size and shape are parameters, for the 32dp circular form on SCR-05. |
| C-08 | `NutriRadio`, `NutriRadioRow` | `Selection.kt` | 22dp ring, primary when selected, one option per 48dp row. |
| C-09 | `TagChip`, `FilterTagChip` | `TagChip.kt` | `TagChip`: display-only pill in nutrient, allergy or condition tone, 32dp by default, optional caps. `FilterTagChip`: 36dp single-select chip, primary fill when selected. |
| C-10 | `SectionLabel` | `SectionLabel.kt` | label/12sp caps, +1.5sp tracking, `on.surface.muted`. |
| C-11 | `ProgressRail` | `Progress.kt` | 8dp pill rail, primary on `surface.sunken`, animated over 400ms, capped at 100%. `warnBelowTarget` switches to warning under 60%; `color` covers the danger case. |
| C-12 | `NutriBottomNav` | `BottomNav.kt` | 72dp bar with Home, List, Nutrition, Profile (`BottomNavTab`). Primary when selected, `on.surface.faint` otherwise. |
| C-13 | `AlertCard` | `Cards.kt` | Tinted card with an icon circle, title and optional message, in danger, warning or positive tone. Optional 6dp leading bar. |

Supporting components:

| Composable | File | Used by | What it does |
|---|---|---|---|
| `NutriTopBar` | `TopBar.kt` | SCR-03, 05, 06, 07, 08, 09 | 56dp app bar with headline/26sp title, optional 24dp back chevron and trailing actions. |
| `CircleIconButton` | `Buttons.kt` | C-06, SCR-06 delete | Round icon button with a 48dp hit area. |
| `ProgressRing` | `Progress.kt` | SCR-07 score, SCR-10 percentage | Circular progress (148dp, 17dp stroke by default) that sweeps from zero over 400ms, with a content slot in the centre. |
| `EmptyState` | `EmptyState.kt` | SCR-05, SCR-07 states | 40dp icon, title, optional message and action. |
| `AppLogo` | `AppLogo.kt` | SCR-01, Login, Create account | Canvas-drawn circle with ring, cart glyph and leaf mark. |

## 4. Files created

- `app/src/main/java/com/example/nutricart/ui/theme/Dimens.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Buttons.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Cards.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Inputs.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Stepper.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Selection.kt`
- `app/src/main/java/com/example/nutricart/ui/components/TagChip.kt`
- `app/src/main/java/com/example/nutricart/ui/components/SectionLabel.kt`
- `app/src/main/java/com/example/nutricart/ui/components/Progress.kt`
- `app/src/main/java/com/example/nutricart/ui/components/BottomNav.kt`
- `app/src/main/java/com/example/nutricart/ui/components/TopBar.kt`
- `app/src/main/java/com/example/nutricart/ui/components/EmptyState.kt`
- `app/src/main/java/com/example/nutricart/ui/components/AppLogo.kt`
- `app/src/main/java/com/example/nutricart/ui/components/ComponentPreviews.kt`
- `app/src/main/res/drawable/ic_alert.xml`, `ic_chart.xml`, `ic_check.xml`, `ic_chevron_down.xml`, `ic_chevron_left.xml`, `ic_close.xml`, `ic_home.xml`, `ic_list.xml`, `ic_minus.xml`, `ic_plus.xml`, `ic_profile.xml`, `ic_search.xml`
- `project_docs/phase_1_design_system.md`

## 5. Files modified

- `app/src/main/res/values/strings.xml` — four bottom-nav labels and three accessibility labels (back, decrease, increase).
- `project_docs/implementation_plan.md` — status line.
- `CLAUDE.md` — architecture notes mention the component library.

No Phase 0 source file was changed.

## 6. Dependencies

None added or changed.

## 7. Assets, icons and fonts

- **Icons:** twelve 24dp vector drawables, drawn as simple strokes and tinted at the call site. No icon library was added.
- **Logo:** drawn in code (`AppLogo`), no image asset.
- **Fonts:** none. The PDFs specify Roboto, the Compose default.

## 8. Design decisions

These fill gaps where the PDFs give no written value.

| Decision | Reason |
|---|---|
| Disabled filled button uses `surface.sunken` with `on.surface.faint` text | The PDFs describe disabled buttons but give no colours. |
| A loading button keeps its primary colours | So the spinner stays visible. |
| Text field fill defaults to `surface.card`, with a `containerColor` parameter | C-04 gives no fill; the mockups show both white and sunken fields. |
| Error outline is 1dp in danger (2dp while focused) | The PDFs specify the colour only. |
| Unchecked checkbox and unselected radio use a 2dp `on.surface.faint` outline | Not specified. |
| Checkbox and radio row labels use body/16sp | Not specified. |
| Tag chip text is micro/11sp at every height; `uppercase` is opt-in | Written for the 22dp and 24dp chips only. |
| Tag chip text colour is the accent on its tinted container | Follows the mockups; C-09 names only the fill. |
| Filter chip: caption/14sp Bold; unselected is `surface.card` with a 1dp outline | Only the selected fill is specified. |
| Alert card message text uses the accent colour | Follows the mockups; not written. |
| Bottom nav has a 1dp `outline` divider on top | Visible in the mockups; not written. |
| Bottom nav icons are house, list, bar chart and person | The mockups show blank placeholders. The chart matches the "24dp chart icon" the UI Flow names for nutrition. |
| Logo defaults to a primary fill with a primary ring | The written spec gives the ring; the mockup shows a solid disc. Both colours are parameters, to be settled when the splash is built in Phase 3. |
| `ProgressRing` defaults to the SCR-07 metrics (148dp, 17dp stroke) | The only written values; SCR-10 has none. |

## 9. Deviations from the PDFs

| Component | PDF says | Implemented | Why |
|---|---|---|---|
| C-04 | `OutlinedTextField` | `BasicTextField` with its own outline | Material's field has fixed inner padding that clips 20sp text at the specified 50dp height. |
| C-07, C-08 | `Checkbox`, `RadioButton` | Custom-drawn | Material's are fixed at 18dp / 20dp with a 2dp corner; the spec is 22dp with a 6dp corner. |
| C-09 | `AssistChip` | Custom pill | `AssistChip` cannot go below 32dp, and the spec uses 22dp and 24dp chips. |
| C-11 | `LinearProgressIndicator` | Custom rail | Material's indicator adds a gap and stop dot and has no 400ms value animation. |
| C-12 | `NavigationBar` | Custom row | Material's bar has an 80dp minimum height and a selection pill; the spec is 72dp with no pill. |
| C-13 | "6dp leading colour bar" | Bar is off by default (`showBar`) | SCR-08 row 3 says the severity bar on warning cards is "the only place a colour bar is used in this app", and no alert-card mockup shows one. |
| C-06 | Value in "stat/24sp" | stat weight at 24sp by default | The stat role is 32sp; the per-element size was used. SCR-06 passes title/20sp. |
| Filter chip | 36dp chips | 36dp, below the 48dp touch target | Follows the written chip height. |

## 10. Left for later phases

- Restyling Splash and Login with these components (Phase 3).
- Mapping Material's own typography to the type roles (Phase 3, with the restyle).
- Screen-specific pieces: onboarding pager dots, summary cells, grocery item rows, docked total card, macro rows, nutrient cards, warning cards, pickers and dialogs.
- Tab enabled/disabled handling in the bottom nav (Phase 4).
- Screen transitions using the `Motion` constants (Phase 9).
- Room, DataStore, authentication and all feature logic (Phase 2 onward).

## Verification

Command (run from the project root):

```
gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

- **Build:** `BUILD SUCCESSFUL in 1m 20s`, 51 actionable tasks: 16 executed, 35 up-to-date.
- **Unit tests:** `ExampleUnitTest` — 1 test, 0 failures, 0 errors, 0 skipped.
- **Lint:** 0 errors, 16 warnings. None are in Phase 1 files: 8 newer-version notices, 7 unused template colours in `colors.xml`, and 1 redundant label in the manifest.
- **Compiler warnings:** none.
- **Not done:** the components have not been seen rendered. Previews cannot be drawn from the command line, and nothing in the running app uses them yet. Open `ui/components/ComponentPreviews.kt` in Android Studio to check them; the icons and logo in particular were drawn without visual feedback.
- **Not run:** the instrumented test (`connectedAndroidTest`).
