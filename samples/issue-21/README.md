# Issue #21 — manual verification steps

GitHub issue: <https://github.com/Stianlars1/css-vars-assistant/issues/21>

## What broke

`--space-2: calc(8px * var(--scaling))` (Radix Themes pattern) was rendered
in the hover popup as bare `0.9` (type: `number`). The resolver was picking
the first of five non-uniform `--scaling` definitions AND dropping the
surrounding `calc(...)` wrapper.

## How to verify the 1.8.5 fix locally

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```
   The signed zip lands in `build/distributions/cssvarsassistant-<version>.zip`.

2. Sideload it into a fresh sandbox IDE:
   ```bash
   ./gradlew runIde
   ```
   Or install the zip manually via *Settings → Plugins → ⚙ → Install Plugin
   from Disk…* in your normal IDE.

3. Open this directory (`samples/issue-21`) as a project, or copy
   `radix-scaling.css` into any existing project.

4. Hover the `var(...)` references inside `.card { … }`:

   | Hover target | Expected Value column | Expected Type column |
   |---|---|---|
   | `var(--space-2)` | `calc(8px * var(--scaling))` | `OTHER` (or empty) |
   | `var(--font-size-3)` | `calc(16px * var(--scaling))` | `OTHER` |
   | `var(--gap)` | `calc(4px * 2)` | `OTHER` |
   | `var(--bg)` | `4px` | `SIZE` |

   The `--space-*` and `--font-size-*` rows must NOT show a bare number
   like `0.9` — that was the pre-fix bug. The deterministic `--gap` and
   `--bg` rows must still substitute through `--unit` (regression check
   that the fix didn't break the substitution path).

5. Trigger completion inside `padding: var(--space|`:
   - The popup must list `--space-1` through `--space-9` with the calc
     expression in the value preview, not bare numbers.

6. Optional cross-check: click a completion to insert it, then hover the
   inserted reference. Same rendering rules apply.

## Automated coverage

The same scenario is locked in by
`src/test/kotlin/cssvarsassistant/documentation/DocHelpersResolveVarValueTest.kt`,
which runs in `./gradlew test`. The file in this directory is for manual
side-loaded validation only — it isn't bundled into the published plugin.
