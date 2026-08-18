# Task 7 Step 6: Observables tab manual acceptance

`OBSERVABLES_ACCEPTANCE: pending-human`

Branch: `observables-selection`.
Commit under test: `da5d7fb` ("Build the observable picker, stop walking it").
Automated gates run on Linux x86_64, OpenJDK 11 source/target, Apache Ant.

This agent has no eyes on a screen, so per the precedent in
`picker-acceptance-results.md`, every row needing a human at a display is
recorded PENDING rather than inferred from a headless check.

## Verified without a display

| # | Step 6 item | Result | Evidence |
|---|---|---|---|
| 1 | Eight rows, in order Buses, Shunts, Impedance loads, Branches, Synchronous machines, Injectors, Two-port injectors, Discrete controllers | PASS (data) | `ObservableCategory.Kind` declares exactly these eight labels in this order; `checkTheEightKeywords` pins all eight keywords and tags |
| 2 | Impedance loads names IMPLOAD and LOAD; Two-port injectors names TWOP and LINK | PASS (data) | `tooltip()` appends the DYNGRAPH spelling whenever `keyword != tag`, which is true for IMPLOAD/LOAD, TWOP/LINK and INJEC/INJ |
| 3 | Remove greyed until a name is listed | PASS | `checkRemoveIsSafe`, `checkClearEmptiesARow` |
| 4 | Duplicate raises the banner and leaves the field text alone | PASS | `checkAddValidates` ("field keeps the text"), `checkInstalledButtonsAreWired` ("duplicate reported through the sink") |
| 5 | All greys that row's field, list and Add | PASS | `checkAllTogglesTheRow` |
| 6 | Clear empties eight rows, three runtime rows including dropdowns, four checkboxes | PASS | `checkResetThroughANestedTree` (53 assertions) |
| 7 | Dropdown lists fourteen, including Two-port injector Observable and Injector solutions | PASS (data) | `grep -c types.put` = 14; both labels present |

Whole-tree gates at `da5d7fb`: `ant compile` BUILD SUCCESSFUL (one pre-existing
warning), `ant jar` BUILD SUCCESSFUL, `tools/observables-harness.sh` 123 PASS /
0 FAIL, `tools/scenario-harness.sh` ALL CHECKS PASSED, the deleted-control grep
returns nothing, and `StepssUI.form` still parses as XML.

## PENDING a human at a display

The one thing no headless check reaches: that the eight rows **render**
correctly once `layoutObservablesTab` builds them. `pickerRow` is new layout
code, and the harness constructs the controls without ever realising them, so
row geometry, the 150px label column, the two equal halves and the visibility
toggle on **Show observable dialog** are unproven. Launch `dist/stepss.jar`,
open Observables, tick Show observable dialog, and confirm items 1 to 7 by eye.
