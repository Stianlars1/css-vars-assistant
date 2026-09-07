# Consolidated release-review fix report

## Result

R1–R5 are addressed in `5f1939bb714770a6d7133d4ebdb464e77d9e5512` (`fix: preserve variable use and declaration context`) and the root-requested R3 null-alias refinement in `4477901264dccb018590a2b41d36f4a976c3c94a` (`fix: resolve null aliases before Sass defaults`). The cache refinement is committed as `6419a657d69ae060d88e0d1ca27de27225d1c0be` (`fix: retain stylesheet snapshots across large catalogs`). Final full suite: **454 tests, 0 failures, 0 errors, 0 skipped**. This retains all 414 previously passing tests and adds the original six review regressions plus 30 semantic controls (36 cases in `ReleaseReviewRegressionTest`) and four snapshot-cache controls.

The semantic fixes commit only these five files; the two additional cache files are listed in the appended cache section:

- `src/main/kotlin/cssvarsassistant/index/PreprocessorLookup.kt`
- `src/main/kotlin/cssvarsassistant/index/VariableLookup.kt`
- `src/main/kotlin/cssvarsassistant/documentation/VariableResolver.kt`
- `src/main/kotlin/cssvarsassistant/documentation/CssVariableDocumentationService.kt`
- `src/test/kotlin/cssvarsassistant/index/ReleaseReviewRegressionTest.kt`

Root-owned `BUILD.MD` and `docs/reports/2026-09-07-release/` work remains untouched. No metadata, import-path resolution, color, PSI helper, publishing, or packaging changes were made.

## Fixes

### R1 — Sass declaration order and discovery

After use-site lookup rejects a Sass binding, project discovery cannot reintroduce a declaration from the active file or its import graph. This covers later declarations, later imports, and imports from an unrelated lexical scope. Undefined alias chains preserve the caller's expression instead of claiming an unavailable intermediate binding as a resolved value. The existing circular-alias expectation stays unchanged.

Unambiguous project discovery remains available for ordinary and legacy-import aliases. A request-only, defaulted `isModuleMember` flag on sourced preprocessor declarations records `@use`/`@forward` provenance. Alias evaluation retains that isolation through recursion, including `@use ... as *`. This flag is not indexed or serialized. The original two-argument `PreprocessorLookup.find` overload remains, and codec constructors and public helper overloads are unchanged.

### R2 — LESS lazy caller context

LESS alias recursion retains the caller's file, offset and scope whenever a caller file exists. Discovery without a caller file starts at the declaration. Sass continues to evaluate at its declaration offset. The cycle key and documentation attribution still use the actual declaration file, offset and name, independently from the LESS evaluation context.

### R3 — local Sass !default

A default assignment without an existing local selection looks up enclosing lexical scopes at that assignment's offset. This also reaches earlier imports. A non-null visible value is preserved; an undefined value, literal `null`, or alias chain resolving to `null` permits the default. Null detection follows declaration-site lookup and module provenance, uses an active declaration path bounded by `maxImportDepth`, and removes active entries in `finally`. Unknown, cyclic and exhausted paths do not count as null. Ordinary local assignments continue to shadow outer bindings. Imported default declarations use the same selection rule.

### R4 — CSS ambiguity

CSS selection first narrows to the active file and known import graph when those contain candidates, then applies context/default selection and established source order. If only unrelated files remain and their candidate values conflict, the resolver retains the original CSS expression. Uniform unrelated values still resolve. Same-file, import-order and local-over-import controls pass.

### R5 — sourced CSS hints

CSS hints call `VariableResolver.resolveCssVariable`, which uses the resolver's existing sourced selection and shared source-location helper. Raw preprocessor aliases are evaluated at the selected custom property's file/offset/context. The string-only local hint selector is no longer used. An ambiguous hint returns no guessed value. Existing hint text expectations remain unchanged.

## Verification evidence

All commands ran in `/Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07`.

1. Original regressions reproduced before production edits:
   `./gradlew test --tests cssvarsassistant.index.ReleaseReviewRegressionTest --no-build-cache`
   **6 executed, 6 expectation failures**. Log: `/tmp/cva-release-fix-initial-red.log`.
2. Expanded initial controls, before production edits: same command, **29 executed, 17 expectation failures, 12 passing controls**. Log: `/tmp/cva-release-fix-expanded-red.log`.
3. R1 focused pass: **8/8**. Log: `/tmp/cva-release-fix-r1.log`.
4. R2 focused pass: **9/9**. Log: `/tmp/cva-release-fix-r2.log`.
5. R3 focused pass: **12/12**. Log: `/tmp/cva-release-fix-r3.log`.
6. R4 pass before hint routing: **20 passed, 1 expected outstanding R5 hint failure** (the wildcard also selected that hint case). Log: `/tmp/cva-release-fix-r4.log`.
7. First consolidated focused pass: **127/127**. Log: `/tmp/cva-release-fix-focused.log`.
8. First full pass: **443/443**. Log: `/tmp/cva-release-fix-full.log`.
9. Self-review added legacy/project discovery controls plus wildcard-module isolation. The deliberately restrictive initial alias mode failed **2 of 4** selected controls; the two failures were legacy/project alias discovery. Log: `/tmp/cva-release-fix-discovery-red.log`. Narrowing isolation to module provenance fixed them.
10. Focused command before the null-alias refinement:
    `./gradlew test --tests cssvarsassistant.index.ReleaseReviewRegressionTest --tests cssvarsassistant.index.SourceAwareLookupTest --tests cssvarsassistant.documentation.DocHelpersResolveVarValueTest --tests cssvarsassistant.completion.CssVariableCompletionHarnessTest --no-build-cache`
    **130 tests, 0 failures/errors/skips**. Log: `/tmp/cva-release-fix-focused-final.log`.
11. Full command before the null-alias refinement:
    `./gradlew test --no-build-cache`
    **446 tests, 0 failures/errors/skips**, `BUILD SUCCESSFUL in 21s`. Log: `/tmp/cva-release-fix-full-final.log`. Counts were summed from that run's `build/test-results/test/TEST-*.xml`; the review class reported **32/32**.
12. Root requested an additional outer alias-to-null R3 control. Four cases were added: outer/imported aliases to null, an alias to a non-null value, and a cross-file alias cycle. `./gradlew test --tests '*ReleaseReviewRegressionTest.testScssLocalDefault*' --no-build-cache` reproduced **2 expectation failures, 7 passing controls** before this refinement. Log: `/tmp/cva-release-fix-null-red.log`.
13. The same four-class focused command from item 10 passed **134 tests, 0 failures/errors/skips** after bounded null-alias lookup. Log: `/tmp/cva-release-fix-null-focused.log`.
14. Final command `./gradlew test --no-build-cache` passed **450 tests, 0 failures/errors/skips**, `BUILD SUCCESSFUL in 23s`. Log: `/tmp/cva-release-fix-null-full.log`. Final XML totals were read after completion; `ReleaseReviewRegressionTest` contains **36/36** passing cases.
15. `git diff --check` and staged `git diff --cached --check` passed. The two commits touch only the five owned files listed above.

The JVM emitted its existing CDS/class-loader warning during fixture startup; it did not cause a test failure. Final Gradle execution completed successfully.

## Self-review and boundaries

- All six original review assertions remain intact.
- Controls cover indented Sass, later imports in both Sass syntaxes, sibling import scope, ordinary shadowing, undefined/default, literal and alias null/default, non-null aliases, null-detection cycles, nearest outer scope, imported outer values, eager Sass definition order, namespaced and wildcard module isolation, legacy/project discovery, LESS local/importer overrides and cycles, CSS source/import precedence, uniform unrelated CSS values, and same-file/imported hint declaration offsets.
- Declaration attribution and cycle identity remain separate from LESS caller evaluation. No global cache, serialization, constructor codec format, index version, or existing recursion/operation budget changed.
- The implementation remains static preview resolution. Legacy project discovery remains an unambiguous-value heuristic; this fix does not claim complete Sass import execution, arbitrary Sass expression evaluation, module configuration/forward filters, mixin/control-flow execution, or a CSS runtime cascade.
- Module provenance is carried through sourced preprocessor lookup, not inferred from a global scan or persisted in an index.
- Final immutable review, IDE compatibility verification, signed packaging and Marketplace upload are root-owned follow-up gates. This report does not claim those operations occurred.

Language checks used official references: [Sass variables](https://sass-lang.com/documentation/variables/), [Sass imports](https://sass-lang.com/documentation/at-rules/import/), and [LESS lazy evaluation](https://lesscss.org/features/#variables-feature-lazy-evaluation).

## Appended cache refinement — deterministic churn above 512 files

Root requested this bounded refinement from the same final review after the semantic fixes. Commit: `6419a657d69ae060d88e0d1ca27de27225d1c0be`.

Owned files:

- `src/main/kotlin/cssvarsassistant/index/StylesheetSnapshots.kt`
- `src/test/kotlin/cssvarsassistant/index/StylesheetSnapshotsTest.kt`

The fixed-size `ConcurrentHashMap` and clear-all-at-512 branch are replaced by `CollectionFactory.createConcurrentWeakKeySoftValueMap`, already used by the plugin's key cache. The snapshot itself is the soft value. Its saved-file and document stamps move from the former wrapper into the immutable snapshot, preserving validation while allowing a strongly retained snapshot to protect the actual cached value during deterministic reuse tests. Invalid files/directories are still rejected; `dispose()` still clears the cache. Query-time parsing and all semantic resolution behavior are unchanged.

The regression loads **600 distinct LightVirtualFiles**, retains every returned snapshot strongly, and checks object identity when reading every unchanged file again. It uses no timing threshold, forced GC, or private-cache inspection. Three controls retain saved-file stamp invalidation, unsaved-document stamp invalidation while the file stamp stays unchanged, and disposal clearing.

Verification:

1. `./gradlew test --tests cssvarsassistant.index.StylesheetSnapshotsTest --no-build-cache` before production changes: **4 tests, 1 expected reuse failure, 3 controls passing**. Log: `/tmp/cva-release-fix-cache-red.log`.
2. `./gradlew test --tests cssvarsassistant.index.StylesheetSnapshotsTest --tests cssvarsassistant.index.SourceAwareLookupTest --no-build-cache` after the change: **16 tests, 0 failures/errors/skips**. Log: `/tmp/cva-release-fix-cache-focused.log`.
3. Final `./gradlew test --no-build-cache`: **454 tests, 0 failures/errors/skips**, `BUILD SUCCESSFUL in 20s`. Counts verified by summing the final XML results. Log: `/tmp/cva-release-fix-cache-full.log`.
4. `git diff --check` and staged `git diff --cached --check` passed. Only the two cache files were committed in this refinement. Root-owned build/docs changes remain untouched, and the audit-worktree Gradle slot is free.

Self-review: this removes deterministic threshold churn; it does not promise retention under memory pressure or claim timing results for full project import discovery. No global listeners, invalidation revision behavior, magic replacement threshold, or parser/compiler expansion was introduced. Source and document stamps remain independent, and soft-reference reclamation simply allows the existing query path to reparse when needed.
