# Issue #36: completion sorting crash

The exception reported in [#36](https://github.com/Stianlars1/css-vars-assistant/issues/36) was reproduced against `main` at `4da08c7` using synthetic tokens. A 120-token catalog shuffled with deterministic seeds triggers `java.lang.IllegalArgumentException: Comparison method violates its general contract!` in `TimSort.mergeHi`. The reporter supplied no project or IDE/plugin version, so this establishes the same failure in current source without claiming to reproduce their exact environment.

## Cause and fix

The problematic rule was introduced in `73bc86e`, included in 1.8.0. `compareQuerySpecificity` skipped name comparisons when both entries were sizes or numbers, but used name specificity for mixed pairs. For query `pa`, the original comparator creates a cycle:

- `padding-really-super-x: 4px` precedes `pa-nu: 0.5` by value type.
- `pa-nu: 0.5` precedes `pa-color-x: #000000` by name length.
- `pa-color-x: #000000` precedes `padding-really-super-x: 4px` by name length.

The fix applies full-query prefix priority consistently, then places numeric entries in one group before skipping name specificity. Within equally relevant matches, sizes and numbers precede non-numeric values; sizes/numbers retain their existing value order, and colors/other values retain their existing specificity rules. Exact names, match tiers, numeric suffix families, blank queries and alphabetical preference keep their existing precedence.

This removes the circular mixed-type ordering without replacing semantic size sorting with name-length sorting. For example, ascending `spacing-2xs: 4px`, `spacing-xs: 8px`, `spacing-s: 12px`, `spacing-m: 16px` remains ordered by resolved value. The production diff changes only `compareQuerySpecificity`; existing tests and other production files are unchanged.

## Verification

| Check | Result |
| --- | --- |
| Original comparator: minimal cycle, broader contract check, 120-token sorting | Three expected failures, including the exact TimSort exception |
| Full suite on IntelliJ IDEA Ultimate 2025.1 | 499 passed, 0 failures/errors/skips |
| Full suite on IntelliJ IDEA 2026.2.2 | 499 passed, 0 failures/errors/skips |
| Plugin build, structure and project configuration | Passed |
| Plugin Verifier 1.410 on the built ZIP | Compatible on all 10 IDE targets |

The 13 added tests cover comparator transitivity, antisymmetry, equivalent-entry consistency, 64 catalog permutations in both value-sort directions, matching tiers, exact/full-prefix ranking, natural numeric suffixes, semantic sizes and unitless numbers. Real IntelliJ fixtures check 120-token completion results, value previews and actual insertion for CSS, SCSS, Sass and LESS in both ascending and descending modes. These four fixture tests are preservation controls; they also pass before the fix. The deterministic comparator tests establish the crash regression.

The full suite preserves the previous completion-boundary, insertion, documentation ownership, indexing, import resolution and cancellation checks. No existing expectation was weakened.

Baseline command:

```sh
./gradlew clean check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-build-cache --console=plain
```

The baseline uses Java 21, IntelliJ Platform Gradle plugin 2.13.1 and Kotlin 2.1.20. The 2026.2.2 run uses a separate temporary copy with IntelliJ Platform Gradle plugin 2.18.1/Kotlin 2.4.10, a local IDE dependency and an alternate build filename. All source and test files were checked byte-for-byte identical between the two runs. Release build configuration is unchanged.

Verifier targets include IntelliJ IDEA 2025.1 and 2025.1.7.2, the five other configured 2025.1 products, IntelliJ IDEA 2026.2.1/2026.2.2 and WebStorm 2026.1.5. Existing experimental API notices remain. Exact builds, verdicts, test counts, source hash and artifact SHA-256 are recorded in [verification.json](verification.json).

Review was performed in this task. No separate manual GUI pass or reporter-project reproduction is claimed. The original checkout and its 39 pre-existing modified/untracked files were preserved. The local unsigned verification ZIP retains version 1.9.5; this work does not publish a release.

Reference: [Java Comparator contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html#compare(T,T)).
