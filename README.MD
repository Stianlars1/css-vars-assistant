<img width="150" height="150" src="https://raw.githubusercontent.com/Stianlars1/css-vars-assistant/refs/heads/main/src/main/resources/META-INF/pluginIcon.svg" /> <br/>

# CSS Variables Assistant 🔧🎨
![Downloads](https://img.shields.io/jetbrains/plugin/d/27392) ![Rating](https://img.shields.io/jetbrains/plugin/r/stars/27392) ![Version](https://img.shields.io/jetbrains/plugin/v/27392)


If you find CSS Variables Assistant helpful, please consider rating it on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant/reviews) ★★★★★ 🙏.

**Version:** 1.8.3<br/>
**Compatible IDEs:** JetBrains IDEs with bundled JavaScript + CSS support — IntelliJ IDEA Ultimate, WebStorm, GoLand, PhpStorm, PyCharm Professional, and RubyMine.  
**Author:** Stian Larsen  
**Repo:** [github.com/Stianlars1/css-vars-assistant](https://github.com/Stianlars1/css-vars-assistant)

**Links:**
- [Official Plugin Website](https://www.css-variables-assistant.dev)
- [Official JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant)
- [Stian Larsen](https://stianlarsen.com)


Supercharge your CSS custom properties and preprocessor variables in JetBrains IDEs with advanced autocomplete, documentation, and debugging tools.
---

## ✨ What's new in 1.8.3

Follow-up to issue [#19](https://github.com/Stianlars1/css-vars-assistant/issues/19) — polish pass for design systems with many theme variants. Inspired by [@LordMaddhi](https://github.com/LordMaddhi)'s follow-up question about how the plugin scales when you have 6-8+ themes.

| Category                                   | Highlights                                                                                                                                                                                                                                                  |
|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🏷 Readable theme labels** (new, default on) | `[data-theme="catppuccin"]` → "Catppuccin", `[data-theme="high-contrast"]` → "High contrast", `.dark` → "Dark" in the Context column. Full raw selector lives on hover. Works for any theme name. Toggle under *Documentation Popup Columns → "Prettify theme selector labels"* — off keeps the verbatim selector rendering for paste-back-to-CSS fidelity. Compound selectors and pseudo-classes stay verbatim so CSS semantics never get misrepresented. |
| **🎯 Collapse duplicate-value rows** (new, default on) | When several themes resolve a token to the same value, they merge into one row like `Light mode, Catppuccin, Sepia` rather than three near-identical rows. Settings toggle: *Documentation Popup Columns → "Collapse rows with identical values"*.                                                     |

[Full Changelog →](https://github.com/Stianlars1/css-vars-assistant/blob/main/CHANGELOG.MD)

---

## ✨ What's new in 1.8.2

Small polish release for the hover popup. IntelliJ's documentation popup clamps its max width, so the 1.8.1 Source column (`variables.css:220`) wrapped into two lines on busy tables. 1.8.2 both nudges the popup wider and adds a compact-source preference.

| Category                                 | Highlights                                                                                                                                                                                                                                                  |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🔎 Compact Source column** (new, default on) | *Source* cell renders just `:220`; the full `variables.css:220` is on hover. Toggle under *Settings → Tools → CSS Variables Assistant → Documentation Popup Columns → "Compact source column"* if you prefer the verbose 1.8.1 look. |
| **↔ Wider popup by default**             | The value table hints at ~700 px minimum width so multi-column rows (Context, Value, Type, Source, Hex, WCAG) don't wrap. Platform still caps around 950 px; users can resize manually and IntelliJ remembers the new size.                                        |

[Full Changelog →](https://github.com/Stianlars1/css-vars-assistant/blob/main/CHANGELOG.MD)

---

## ✨ What's new in 1.8.1

Small follow-up release targeted at theming workflows — closes issue [#19](https://github.com/Stianlars1/css-vars-assistant/issues/19) reported by [@LordMaddhi](https://github.com/LordMaddhi) and ships a related cascade-visibility improvement.

| Category                                   | Highlights                                                                                                                                                                                                                                                                               |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🎨 Attribute & class themes become their own contexts** | `[data-theme="dark"] { --bg: black }`, `.dark { --bg: black }`, `.theme-high-contrast`, `:hover`, `[dir="rtl"]` and other non-root selectors are now indexed as distinct contexts and show up as their own rows in the hover popup. Before 1.8.1 they collapsed into `default` and were silently overwritten by the last declaration. Nested `@media (prefers-color-scheme: dark) { .hc { … } }` combines labels as `(prefers-color-scheme: dark) .hc`. |
| **📍 Source file:line per declaration**    | The *Source* column in the hover popup now shows `tokens.css:42` for every row, so when `--bg` is redefined across shadcn/Radix/Tailwind/Material theme files you can see exactly where each value lives. Legacy index records fall back to the prior "first resolution step" label automatically. |
| **ℹ️ Cascade-ambiguity disclaimer**        | A small note under the value table reminds you that rows are shown in index order and the runtime-applied value still depends on stylesheet load order and specificity — we don't simulate the full CSS cascade engine.                                                                          |

> Upgrading from 1.8.0? The CSS variable index rebuilds once on first launch after install — the packed index record gained a 4th field (source line), and the rebuild surfaces the new theming contexts and line info for existing files.

**Thanks to [@LordMaddhi](https://github.com/LordMaddhi) for reporting [issue #19](https://github.com/Stianlars1/css-vars-assistant/issues/19) and testing the fix.**

[Full Changelog →](https://github.com/Stianlars1/css-vars-assistant/blob/main/CHANGELOG.MD)

---

## ✨ What's new in 1.8.0

Biggest quality release to date — closes issue [#18](https://github.com/Stianlars1/css-vars-assistant/issues/18) plus ~15 related completion-popup regressions that only surfaced during real IDE use. All fixes are locked in with regression tests.

| Category                                   | Highlights                                                                                                                                                                                                                                                                               |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🐛 Completion popup is reliable again**   | No more sticky lookup after typing a few chars. `--error` no longer hides behind `--error-foreground`. `var(--)` doesn't shove SIZE variables above everything. `hsl(var(--err))` surfaces the actual `--error` variable. `var(-` autopopup returns the full token list instead of filtering out every variable without an internal dash. |
| **🔢 Three-option completion sort order**   | *Completion sort order* now offers **By value ascending** (default), **By value descending**, and **Alphabetical by name**. The preference applies consistently in both the `var(--|)` autopopup flow and direct invocations, so `--padding-sm / --md / --lg` always appear in the chosen order.                                |
| **🐛 Value column shows the real value, not a comment** | Also issue #18. When a CSS property had a JSDoc/block comment above it, the popup could scrape a commented-out `--name: sample;` from elsewhere in the file and display it as the value. Comments are now stripped before the local-value scan runs.                                 |
| **🎯 Completion doesn't fire outside `var()`** | Parenthesis-depth tracking from each `var(` to the caret — typing after a closed `var(--x)` or between two sibling `var()` calls no longer surfaces every indexed variable. `myvar(` and other word-boundary false matches are out too.                                                    |
| **🧹 Parser tidies up inline comments**     | `--x: /* legacy */ 1px;` and `--x: 1px /* px */;` are parsed as `1px`. A leading `/* comment */ --y: 2;` on one line still indexes `--y`. Minified CSS that begins with a copyright comment is now fully indexed (thanks to [@pierreoa](https://github.com/pierreoa) via [PR #17](https://github.com/Stianlars1/css-vars-assistant/pull/17)).                         |
| **⚙️ Two new completion-popup settings**    | Toggle the description next to each completion item on/off, or clamp it to a custom length (0 hides, up to 120 chars). Under *Settings → Tools → CSS Variables Assistant → Display Options*.                                                                                        |
| **📦 Broader verifier coverage**            | Plugin verification now also runs against PyCharm Professional and RubyMine, in addition to IntelliJ IDEA Ultimate, WebStorm, GoLand, and PhpStorm.                                                                                                                                     |

> Upgrading from an earlier 1.7.x? The CSS variable index will rebuild once on first launch after install — the parser's value capture changed, and a rebuild ensures no stale values survive.

**Thanks to @pierreoa for [PR #17](https://github.com/Stianlars1/css-vars-assistant/pull/17) surfacing the minified-CSS indexing bug, and to @Blinks44 for the detailed bug report in issue #18 that drove the completion-popup hardening.**

[Full Changelog →](https://github.com/Stianlars1/css-vars-assistant/blob/main/CHANGELOG.md)

---

## 🏅 Previous highlights

| Category                    | Highlights                                                                                      |
|-----------------------------|-------------------------------------------------------------------------------------------------|
| **📚 IntelliJ 2024.1+ docs** | Full support for the new documentation API with rich popups and fallback for older IDEs.        |
| **📊 Dynamic docs table** | Docs auto-add *px Eq.*, *Hex* and *WCAG* columns whenever the value type warrants it. |
| **↗ Derived var indicator**  | Recursive/derived variable completions now show a special indicator (↗).                        |
| **🔎 Debug import resolution**| Improved DebugImportResolution helper for easier tracing of variable origins and import chains. |
| **🧠 Context/cascade logic** | Completions and docs now always use the last value per context (CSS cascade honored).           |
| **🎯 CSS cascade compliance** | Fixed critical bug where local declarations now correctly override imported values, following proper CSS cascade rules. |
| **🏆 Winner-first docs** | The actual winning value is now shown first in documentation tables for immediate clarity. |
| **📖 Derived variable legend**  | Documentation now includes an explanation of the ↗ symbol when variables are resolved through imports or references. |
| **⚡ Robustness & accuracy** | Smarter context labels, improved media query parsing, color parsing, and documentation rendering. |
| **🚀 Performance & refactors**| Faster, more maintainable codebase with centralized index versioning and better caching.        |
---

## Features

- **Instant variable lookup:** Blazing-fast completions and documentation for `CSS`, `SCSS`, `SASS` and `LESS` variables.
- **Smart autocomplete:** Context-aware suggestions for `var(--…)`, `@less`, and `$scss` with value/context-based sorting.
- **Rich documentation popups:**
    - **Customizable Columns:** Choose what you see (Context, Value, Source, etc.).
    - **Resolution Chain:** A tooltip shows the full resolution path of a variable.
    - Value tables (with px equivalents for rem/em/%/vh/vw/pt)
    - Context labels (Default, Dark, min-width, etc.)
    - Color swatches and contrast info
    - Dynamic columns: *px Eq.*, *Hex*, *WCAG* appear only when relevant
    - **CSS cascade compliance:** Shows the actual winning value first, following proper CSS cascade rules
    - **Legend for derived variables:** Explains the ↗ symbol for variables resolved through imports
- **JSDoc-style comments:** Auto-parsing and display of `@name`, `@description`, and `@example`.
- **Advanced import resolution:** Follows and indexes imports across `CSS`, `SCSS`, `SASS` & `LESS`.
- **Debugging tools:** Trace variable origins and import chains visually.
- **Configurable sorting:** Completion list sorted by value _(ascending/descending)_.
- **Works everywhere:** `CSS`, `SCSS`, `SASS`, `LESS`.

---

## Configuration

Open **Settings → Tools → CSS Variables Assistant**

| Option | Effect                                                                        |
|--------|-------------------------------------------------------------------------------|
| **Show context values** | Display dark/light & media-query variants                                     |
| **Allow IDE built-in completions** | Fall back to IntelliJ suggestions for misses                                  |
| **Indexing scope** | *Project only* • *Project + imports* (default) • *Full global* |
| **Max @import depth** | 1 – 20 (default 20)                                                           |
| **Completion sort order** | *By value ascending* (default) • *By value descending* • *Alphabetical by name* |
| **Show description in completion popup** | Toggle the description next to each completion item                       |
| **Description max length in completion popup** | 0 – 120 chars (0 hides)                                              |
| **🔄 Re-index Now** | Flush caches and rebuild the variable index immediately                       |

---

## 🐞 Debugging import chains

Need to verify *exactly* which files your `@import`s resolve to?
1. **Right-click** any *.css / .scss / .sass / .less* file in the Project view <br>
   *or* inside the editor.
2. Select **"Debug CSS Import Resolution"**.
3. A dialog shows:
    * full import tree (`@import` depth-limited)
    * circular-reference warnings
    * variable counts per file  
      A copy is also written to the IDE Log for later inspection.

> 💡 Tip You can keep the dialog open while you edit – rerun the action to refresh.

## Troubleshooting

| Symptom | Resolution |
|---------|------------|
| Variable shows `@lessVar` instead of real colour | Click **Re-index Now**, wait for *Updating indexes…* to finish. |
| Completions missing after changing scope | Re-index or restart IDE (button normally suffices). |
| Slow indexing | Use *Project + imports*, lower max-depth, exclude large folders. |
| Need to see where a variable came from | Right-click file → **Debug CSS Import Resolution**. |

---

## Also by this author

If you also write CSS with `rem`, `em`, or viewport units, try **[PxPeek](https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints)** — inline pixel-equivalent hints for every relative CSS unit, right in the editor.

---

## Building & Testing Locally

```bash
git clone https://github.com/stianlars1/css-vars-assistant.git
cd css-vars-assistant
./gradlew clean buildPlugin       # produces ZIP under build/distributions
./gradlew runIde                  # start sandbox IDE
```
