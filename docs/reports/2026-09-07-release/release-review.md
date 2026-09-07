> Final status: approved at 6419a65 after the consolidated fixes. The initial findings below are retained as evidence; the definitive re-review verdict is at the end.

# Final branch review — CSS Variables Assistant 1.9.4

Reviewed immutable range `1c9ea8e..d916068` against README.MD Features/Configuration, docs/PLUGIN-CONTRACT.md, and the historical audit/fix plan. Production code remained unchanged. Root explicitly authorized the new `src/test/kotlin/cssvarsassistant/index/ReleaseReviewRegressionTest.kt` for targeted reproductions after the initial read-only brief.

**Requirement compliance: changes requested.** The own-file index boundary, source attribution, changed-file invalidation, scanner consolidation, language token boundaries, and preservation of ranking/insertion are substantial improvements. The promised use-site-aware Sass/LESS semantics and shared behavior across preview surfaces still have reproducible gaps.

**Quality / regression-risk verdict: do not upload this commit yet.** No critical crash, security defect, or destructive behavior was found in this review. Five important correctness findings below affect the central value-resolution workflow; six focused regression cases fail. Resolve them in the planned consolidated fix wave and rerun the existing regressions plus these cases before release. Baseline/latest-IDE verification and signing remain separate release checks owned by root.

## Confirmed important findings

### R1 — P2: Sass lookup can reintroduce a declaration rejected as being after the use

Location: `src/main/kotlin/cssvarsassistant/index/PreprocessorLookup.kt:19-24`.

```scss
.before { color: $brand; }
$brand: blue;
```

Resolving `$brand` at the first use returns `blue`; it must remain unresolved. `findInFile` correctly finds no earlier binding, but `find` then calls `preprocessorValues`, which uses `Int.MAX_VALUE` and returns the very same later declaration. This also exposes the future value to completion/documentation. The existing order test contains an earlier `red` declaration, so it never exercises this fallback.

Minimal remedy: distinguish a use-site lookup with no visible binding from an unconstrained project discovery lookup. Preserve project discovery where appropriate, but do not let fallback reintroduce declarations/imports rejected by the active file's declaration-order rules. Add equivalent Sass and later-import checks when adjusting this path. Sass documents top-level variables as available after declaration: [Sass variable scope](https://sass-lang.com/documentation/variables/#scope).

Red fixture: `testScssUseBeforeFirstDeclarationRemainsUnresolved` — expected `$brand`, actual `blue`.

### R2 — P2: LESS aliases are evaluated in their definition scope instead of the lazy caller scope

Location: `src/main/kotlin/cssvarsassistant/documentation/VariableResolver.kt:48-54`.

```less
.lazy-eval { width: @var; @a: 9%; }
@var: @a;
@a: 100%;
```

Resolving `@var` in `.lazy-eval` gives `100%`, whereas LESS gives `9%`. The resolver always replaces the caller location with the declaration file/offset before evaluating an alias. That is useful for Sass's eager assignments and module isolation, but wrong for LESS's lazy evaluation. Imported LESS aliases likewise ignore importer overrides: a library containing `@base: red; @alias: @base;` imported by an app that sets `@base: blue` still resolves `@alias` to `red`.

Minimal remedy: carry the LESS evaluation/caller context through alias recursion and imported bindings while retaining the declaration source separately for attribution and cycle identity. Keep Sass evaluation at its defining assignment/module location. This exact local-scope example and importer-overrides behavior are covered by the [official LESS variable documentation](https://lesscss.org/features/#variables-feature-lazy-evaluation).

Red fixtures: `testLessAliasUsesCallerScopeForLazyEvaluation` — expected `9%`, actual `100%`; `testImportedLessAliasUsesImporterOverride` — expected `blue`, actual `red`.

### R3 — P2: Local Sass !default overlooks an already visible outer value

Location: `src/main/kotlin/cssvarsassistant/index/PreprocessorLookup.kt:49-56`.

```scss
$brand: red;
.local { $brand: blue !default; color: $brand; }
```

The plugin resolves the local use to `blue`; Sass retains `red`. Each lexical level starts with `selected = null`, so the local `!default` is accepted before the outer visible value is considered.

Minimal remedy: for a default-only assignment, determine the already visible value at that assignment, including enclosing lexical scopes/imports, and only assign when undefined or null. Preserve ordinary local shadowing and the existing null/default cases. Besides [Sass's default rule](https://sass-lang.com/documentation/variables/#default-values), an independent `npx --yes --package=sass sass --stdin` compilation of this fixture produced `.local { color: red; }` during this review.

Red fixture: `testScssLocalDefaultPreservesVisibleGlobal` — expected `red`, actual `blue`.

### R4 — P2: CSS use-site lookup arbitrarily chooses between unrelated conflicting files

Location: `src/main/kotlin/cssvarsassistant/documentation/VariableResolver.kt:122-128`.

`red.css` declares `:root { --base: red; }`, `blue.css` declares `:root { --base: blue; }`, and an unrelated `app.css` declares `:root { --brand: var(--base); }`. With a real app-file location, resolving `var(--base)` returns `blue`. No import/cascade relationship establishes that winner. The ambiguity check only runs when `location.file` is null; real completion/documentation callers supply a file. Both unrelated files receive order `-1`, and line/offset/index enumeration breaks the tie.

Minimal remedy: select among declarations in the established file/import context first. If only unrelated candidates remain and their values conflict, retain the original expression or return explicit alternatives instead of using line/offset to declare a winner. Preserve the existing successful same-file/import-order and theme cases. This follows the new product contract's explicit ambiguity/static-analysis boundary.

Red fixture: `testUnrelatedConflictingCssValuesRemainUnresolvedAtUseSite` — expected `var(--base)`, actual `blue`.

### R5 — P2: CSS quick hints discard the declaration location before resolving its raw value

Location: `src/main/kotlin/cssvarsassistant/documentation/CssVariableDocumentationService.kt:259-264`.

Within the plugin's existing support for preprocessor-valued custom properties, consider `$size: 10px; :root { --gap: $size; } $size: 20px; .consumer { padding: var(--gap); }`. Completion/documentation evaluate the `--gap` entry with its declaration offset. The hint picks just the raw `$size` string, discards its file/offset/context, and evaluates it at the consumer's position, producing `Resolution: $size → 20px`.

This is a disagreement between the plugin's supported preview surfaces, not a claim that the plugin executes a Sass compiler. It also affects imported entries because their file context is discarded in the same code path.

Minimal remedy: route CSS hints through the same sourced-entry selection and resolution used by the other surfaces, retaining declaration file, offset, and context. Avoid a separate string-only local selector.

Red fixture: `testCssHintResolvesPreprocessorAliasAtDeclarationPosition` — expected the declaration-context `10px` insight, actual `Resolution: $size → 20px`.

## Verification and broad-pass assessment

- Root supplied fresh clean-suite evidence of 414/414 passing, with `check buildPlugin verifyPlugin` and six baseline IDE products compatible. I did not rerun that full matrix or treat it as proof against new counterexamples.
- Fresh targeted command: `./gradlew test --tests cssvarsassistant.index.ReleaseReviewRegressionTest --no-build-cache`. Final result: **6 executed, 6 failed**, all expectation failures rather than fixture/startup errors. Log: `/tmp/cva-release-review-red.log`; detailed values also appear in `build/test-results/test/TEST-cssvarsassistant.index.ReleaseReviewRegressionTest.xml`.
- Reviewed the stronger own-file assertions in `ImportResolverTest` and `SourceAwareLookupTest`, plus actual query helpers after the ownership change. They check that dependencies are absent from importer index entries and present in sourced query results. Updating the helper to query the new layer is justified; these assertions prevent merely hiding the ownership change behind helper expectations.
- Both DataIndexers now depend on their own FileContent, without import traversal or application-scope settings. Project-only excludes node_modules; project-plus-imports/global queries union explicit imports and include snapshots for unindexed dependencies. File/document stamps and project services address the old stale-value/static-cache lifecycle failures. No new source-attribution or index-format defect was found; the codec protects separators and the index version changes.
- Reviewed the unchanged completion matcher/ranking/insertion integration and the new gathering path, native language/at-rule fallback, bounded shared alias paths, parser/source offsets, import ordering/package fallbacks, color/value changes, metadata, and removed unload-time global cleanup. No additional release-blocking finding in those areas from this pass.
- Performance remains a bounded-evidence area: the checked-in diagnostic exercises 100/1,000 local tokens under PROJECT_ONLY. It does not establish default project-plus-imports behavior on a large import graph. `StylesheetChanges` invalidates on every document event, so the next import query can traverse the project again, and the 512-snapshot clear-all policy can churn on larger graphs. These are source-based follow-up risks, not reproduced timing regressions and not additional blockers in this review.
- Full compiler semantics such as Sass module configuration/forward filters and execution of mixins/control flow are beyond the verified contract. The five findings above use simpler already-targeted behavior or direct disagreement between current callers; they do not require implementing a compiler.

## Re-review gate

Make the six new regressions pass without weakening their assertions, retain the existing own-file/query checks, and verify ordinary Sass shadowing/module isolation, LESS cycles/import scopes, CSS theme rows, native fallback, and completion ranking/insertion. Re-review the final immutable diff after the consolidated changes. A successful upload remains distinct from Marketplace approval/listing.

## Definitive re-review — d916068..6419a65

**Code-review release gate: PASSED at `6419a657d69ae060d88e0d1ca27de27225d1c0be`. Requirement compliance: approved for the documented static-preview scope. Quality/regression-risk verdict: approved; no remaining critical or important finding in R1–R5 or the directly related null/default and snapshot refinements.** This supersedes the initial hold at `d916068` above.

The re-review used the immutable diff, the final production code, all 36 review regression cases, the four cache controls, and the appended `release-fix-report.md`. No source changes or test reruns were made during re-review. Root is separately running the clean baseline/current-IDE checks and preparing release artifacts; this verdict approves the code-review gate and does not claim those checks, signing, upload, or Marketplace approval have completed.

- **R1 closed:** use-site failures cannot reintroduce the same file's later declaration or an import binding rejected for order/scope. The original failure is unchanged, with added indented-Sass, later-import and sibling-scope controls. Explicit controls preserve unambiguous project/legacy discovery, while module provenance keeps namespaced/wildcard aliases isolated. The changes remain query-only and do not alter index ownership or serialized data.
- **R2 closed:** LESS aliases retain a real caller's evaluation location; source file/offset still identify the declaration for attribution and cycle detection. Sass keeps its defining assignment/module location. Local, imported, importer-overridden and cyclic LESS cases are covered alongside eager Sass/module controls.
- **R3 closed:** default-only selection consults visible enclosing scopes at the assignment position. The related alias-to-null evaluation is conservative and bounded: it checks cancellation, limits the active declaration path, retains module isolation, and removes path entries in `finally`. Literal/alias null, non-null aliases, nearest outer scope, imports, ordinary shadowing and cyclic unknowns have explicit controls.
- **R4 closed:** CSS selection gives established file/import relationships priority and preserves the expression for conflicting unrelated candidates. Same-file/import order, local override and uniform unrelated-value controls preserve the intended successful paths.
- **R5 closed:** CSS quick hints now use the resolver's sourced selection and shared declaration-location construction. The separate string-only hint path is gone. Same-file and imported offsets are covered, and an ambiguous hint supplies no guessed value.
- **Snapshot follow-up closed:** the deterministic 512-entry clear-all behavior is removed. The platform weak-key/soft-value cache retains snapshots as its actual values, and independent file/document stamps still validate them. The 600-file test holds returned snapshots strongly and asserts public result identity rather than timing, GC behavior or private-map state; saved edits, unsaved edits and disposal remain covered. Retention under memory pressure is intentionally not guaranteed.

The original six regression assertions are preserved. I read the reported red logs for the added null-alias and 600-file cases and independently counted the final `/tmp/cva-release-fix-cache-full.log`: **454 passed, 0 failed, 0 skipped**, including **36 review controls** and **4 snapshot controls**; the log ends `BUILD SUCCESSFUL`. `git diff --check d916068..6419a65` also passed. These results support closure of the specific review findings; no further test run was warranted during this scoped re-review.

The earlier source-based concern about rebuilding import discovery after document events remains an unmeasured follow-up, not an open blocker or a completion-latency guarantee. The release may proceed through root's remaining compatibility, packaging/signature and upload steps once those independent checks succeed. No additional code-review fix wave is requested.
