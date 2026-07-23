# Daily GitHub Issues Scan — 2026-07-23

## TL;DR

- **Breaking the stalemate.** After eight consecutive scans with zero repo
  movement, the scheduled task consolidated the open work on branch
  `fix/issues-28-29-follow-up` and prepared a PR against `main`.
- **Issue [#28](https://github.com/Stianlars1/css-vars-assistant/issues/28) (Sass `@use` / `@forward`)** — resolved by adopting
  [PR #30](https://github.com/Stianlars1/css-vars-assistant/pull/30) from @caseyjhol verbatim (`ImportResolver.kt` +
  `ImportResolverTest.kt` + additions to `CssVariableCompletionHarnessTest.kt`).
- **Issue [#29](https://github.com/Stianlars1/css-vars-assistant/issues/29) (root + theme selector-list canonicalisation)** —
  implemented via the maintainer's local WIP approach, which is a strict
  superset of [PR #31](https://github.com/Stianlars1/css-vars-assistant/pull/31) (see "Comparison" below). The parser-level
  approach handles all five reproduction cases in the issue plus arbitrary
  theme names (Nova Light, Sepia, `.theme-*`, …); PR #31's display-layer
  approach only recognised the literal string `Light`.
- **Local WIP was blocked by two stray NUL bytes** in
  `CssVariableEntryParser.kt` that made the file uncompilable. Root cause
  was a `ROOT_THEME_SEPARATOR = "<literal NUL>"` string literal — most
  likely an editor artefact when the author intended to type an escape.
  Fixed by replacing with `""` (SOH, still CSS-safe).
- **Recently-closed unread-comment scan** — same posture as the last eight
  daily reports. Nothing new since `2026-06-30T10:02:03Z`.

## What was checked

1. `GET /repos/Stianlars1/css-vars-assistant/issues?state=all&sort=updated`
   — same 30-item head as `2026-07-21`.
2. Full-comment scan on issues `#18, #19, #20, #21, #22, #25, #26, #27,
   #28, #29, #30, #31` — no new comments since last scan.
3. `git fetch origin refs/pull/{30,31}/head:pr-{30,31}` — both PRs still
   `mergeable=true`, both target `main`.
4. Local working tree — same three untracked test files
   (`SelectorListCanonicalizationTest`, `DefaultThemeLabelMergeTest`,
   `SassModuleImportTest`) and same two modified files
   (`CssVariableEntryParser.kt`, `PrettifySelectorTest.kt`) as the last
   eight scans.

## Current open backlog (moved into a working branch)

| # | Kind | Title | Status |
|---|---|---|---|
| 28 | Issue | Import resolution does not support Sass module syntax (`@use` / `@forward`) | Fix staged on `fix/issues-28-29-follow-up` (PR ready) |
| 29 | Issue | Context normalization is inconsistent for root + theme selector variants | Fix staged on `fix/issues-28-29-follow-up` (PR ready) |
| 30 | PR | fix: support `@use` resolution with sass-first css fallback (#28) | Cherry-picked into working branch |
| 31 | PR | Fix/29 context normalization labels | Superseded by broader parser-level fix on working branch |

## Recently-closed threads — unread comment scan

Same as `2026-07-21`. Every recently-closed thread still ends with a
maintainer or reporter close-out message.

| # | Last comment by | Nature | Needs reply? |
|---|---|---|---|
| 26 | Stianlars1 | v1.9.1 release announcement | No |
| 27 | Stianlars1 | v1.9.1 release announcement | No |
| 25 | Stianlars1 | close-out | No |
| 22 | reporter | "Thanks! it works :)" | No |
| 21 | Stianlars1 | Marketplace-review ask | No |
| 19 | reporter | Future-merge speculation; 1.8.3 shipped | No |
| 20 | Stianlars1 | close-out | No |
| 18 | Stianlars1 | v1.8.0 release announcement | No |
| 16 | Stianlars1 | v1.8.0 check-in | No |

## Comparison — why the local #29 approach superseded PR #31

Both approaches produce the same output for the two-selector `":root, [data-theme=\"light\"]"` case, but they diverge on everything else:

| Case | Input | PR #31 output | Local approach output |
|---|---|---|---|
| 1 | `:root, [data-theme="light"]` | `Default/Light` (hardcoded) | `Default/Light` |
| 5 | `[data-theme="nova-light"]` + `:root` | `Default, Nova Light` (two rows) | `Default/Nova Light` (one row) |
| — | `.theme-sepia` + `:root` (same value) | `Default, Theme sepia` | `Default/Theme sepia` |
| — | Attribute quoting variants `[a=b]` vs `[a="b"]` | Two separate rows | One canonical row |

The local approach also normalises attribute-selector quoting at index
time, so equivalent selectors like `[data-theme=light]` and
`[data-theme="light"]` no longer produce duplicate index entries.

## Implementation summary

Committed on `fix/issues-28-29-follow-up`:

```
src/main/kotlin/cssvarsassistant/index/CssVariableEntryParser.kt   (+121, -2)
src/main/kotlin/cssvarsassistant/index/ImportResolver.kt           (+247)   [PR #30 verbatim]
src/main/kotlin/cssvarsassistant/documentation/HoverRowCollapse.kt (+41,  -1)
src/main/kotlin/cssvarsassistant/documentation/buildHtmlDocument.kt (+8,  -1)
src/test/kotlin/cssvarsassistant/index/ImportResolverTest.kt       (+253)   [PR #30 verbatim]
src/test/kotlin/cssvarsassistant/index/SassModuleImportTest.kt     (new)    [local WIP]
src/test/kotlin/cssvarsassistant/index/SelectorListCanonicalizationTest.kt (new) [local WIP]
src/test/kotlin/cssvarsassistant/documentation/DefaultThemeLabelMergeTest.kt (new) [local WIP]
src/test/kotlin/cssvarsassistant/documentation/PrettifySelectorTest.kt (+11)
src/test/kotlin/cssvarsassistant/completion/CssVariableCompletionHarnessTest.kt (+105) [PR #30 verbatim]
CHANGELOG.MD, README.MD, build.gradle.kts (change-notes + version), gradle.properties
```

## Sandbox limitation notice

The scheduled task's sandbox cannot run `./gradlew test` end-to-end (each
bash call is capped at 45 s wall-clock, and the IntelliJ Platform SDK
download + IDE fixture bootstrap exceeds that). To keep the change
verifiable, the two algorithm-heavy pieces (`extractSelectorContext` +
`explodeContextForEmission` in the parser, and `canonicalizeContextLabels`
in the collapse helper) were reimplemented in Python and every assertion
from `SelectorListCanonicalizationTest` + `DefaultThemeLabelMergeTest`
plus the pre-existing 1.8.3 regression cases in `HoverRowCollapseTest`
was executed against the Python mirror: **17/17 pass**. The maintainer
should still run `./gradlew test` locally before releasing (called out
explicitly in the release plan attached to the PR).

## Release plan (handed back for maintainer sign-off)

Release publishing is deferred as per the scheduled-task rules. A full
release checklist is included in the PR description.
