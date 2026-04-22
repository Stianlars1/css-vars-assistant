# CSS Variables Assistant **(--)** Changelog

All notable changes to this project will be documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/) and semantic-versioning.

## [1.8.2] – 2026-04-22

### Added
- **Compact Source column (on by default):** the hover popup's *Source* cell renders just `:220` instead of `variables.css:220`; the full path shows on hover via a `title` tooltip. Keeps the popup narrow enough to avoid wrapping at IntelliJ's max-width clamp. New setting under *Settings → Tools → CSS Variables Assistant → Documentation Popup Columns → "Compact source column"*. Existing 1.8.0/1.8.1 users get the new default on first launch; turn it off for the classic verbose display.
- **Wider popup by default:** the value table now hints at ~700 px minimum width via a `min-width` wrapper and an invisible spacer element, so multi-column rows (Context, Value, Type, Source, Hex, WCAG) don't wrap in the common case. Platform still caps around 950 px and users can resize manually — IntelliJ remembers the new size.

### Notes
- No index rebuild needed. Purely a rendering / UI polish release.
- Research spike on the v2 `DocumentationTarget` API confirmed there is no plugin-side API for programmatic popup sizing; the content-side width hints above are the platform-sanctioned workaround.

## [1.8.1] – 2026-04-22

### Added
- **Theming selectors now appear as their own rows in the hover popup (issue [#19](https://github.com/Stianlars1/css-vars-assistant/issues/19), reported by [@LordMaddhi](https://github.com/LordMaddhi)):** Declarations inside `[data-theme="dark"]`, `.dark`, `.theme-high-contrast`, `:hover`, `[dir="rtl"]` and similar non-root selectors are now indexed as distinct contexts. Before 1.8.1 every non-`:root`/`:host`/`html`/`body`/`*` block collapsed into `default` and only the last declaration was shown, which hid dark-mode values from any theming setup that didn't use `@media (prefers-color-scheme: dark)`. Nested blocks combine labels, so `@media (prefers-color-scheme: dark) { .hc { … } }` now renders as `(prefers-color-scheme: dark) .hc`.
- **Source file and line per declaration in the hover popup:** the *Source* column now shows `tokens.css:42` for every indexed declaration, making it obvious which file and which line a value is coming from when the same variable is defined across multiple theme files (shadcn, Radix, Tailwind, Material). Legacy index records fall back to the previous "first resolution step" label automatically.
- **Cascade-ambiguity disclaimer:** a small note under the value table reminds that rows are shown in index order and the runtime-applied value still depends on stylesheet load order and specificity — we don't (and can't) simulate the full cascade engine in a hover popup.

### Changed
- **Bumped `INDEX_VERSION` to 1045** so existing installs re-index once on first launch after upgrade. The packed index record now carries a 4th field (source line); a legacy 3-part record decodes safely with `line = -1` so stale caches never crash the popup.
- **`CssVariableDocumentationService` now pulls values via `FileBasedIndex.processValues`** instead of `getValues` + `getContainingFiles`, so the `(VirtualFile, packed)` pair is available in one pass rather than an O(files × keys) re-query.

## [1.8.0] – 2026-04-22

### Fixed
- **Completion popup no longer loses suggestions during progressive typing (issue [#18](https://github.com/Stianlars1/css-vars-assistant/issues/18) follow-up):** Multiple regressions that only surfaced in real IDE use rather than in the harness were fixed:
  - IntelliJ's `DUMMY_IDENTIFIER` is now stripped from the captured query so `hsl(var(--err))` doesn't collapse to "show every variable".
  - The popup is no longer sticky — `cloneWithPrefix` now returns a fresh matcher on every keystroke, and `prefixMatches` consults our ranking logic instead of unconditionally passing every item.
  - Blank-query autopopups sort alphabetically, so `var(--)` no longer shoves every size variable to the top.
  - Tier ordering now ranks explicitly via `matchingDegree` and `isStartMatch`, so `--error` is no longer hidden behind `--error-foreground` when you type `--err`.
  - Alphabetical ordering from the blank-query auto-popup no longer freezes priorities when you keep typing.
  - Elements no longer carry a second stripped lookup string, which was causing IntelliJ's arranger to suppress the shorter of two sibling items (e.g. hiding `--foreground` when `--foreground-subtle` was also present).
  - Pure-dash prefixes (`-`, `--`, `---`) are treated as "no real query yet", so the autopopup firing at `var(-<caret>)` no longer filters out every variable without an internal dash.
- **Autocomplete value column no longer shows the description (issue [#18](https://github.com/Stianlars1/css-vars-assistant/issues/18)):** When a CSS custom property had a JSDoc/block comment above it, completion and hover documentation could display a commented-out `--name: sample;` snippet as if it were the real value. The local-value scan now strips `/* ... */` and `// ...` comments before it runs, so both the popup's value column and the cascade winner reflect the real declaration.
- **Autocompletion no longer fires outside `var()` (issue [#18](https://github.com/Stianlars1/css-vars-assistant/issues/18)):** The "caret is inside `var(…)`" gate now tracks parenthesis depth from each `var(` to the caret, so typing after a closed `var(--x)` on the same line (or between two sibling `var()` calls) stops surfacing every indexed variable. Identifiers ending in `var` (e.g. `myvar(`) also no longer trigger completions.
- **Inline `/* … */` in values and declarations:** The parser now strips inline CSS comments from captured values and still indexes variables even when a leading `/* comment */` and the declaration share one line (`/* blue */ --primary: #0000ff;`).
- **`PROJECT_ONLY` / `PROJECT_WITH_IMPORTS` now consistently exclude `node_modules`** at the CSS query layer, matching the settings panel's own description.
- **Minified CSS files that start with a copyright comment are now indexed (thanks to [@pierreoa](https://github.com/pierreoa) via [PR #17](https://github.com/Stianlars1/css-vars-assistant/pull/17)):** A minified block like `/* Copyright */:root{--a:red;--b:4px}` used to skip everything after the leading comment. Pierreoa's fix targeted the pre-refactor parsing path; the 1.7.x parser extraction had moved the same logic into `CssVariableEntryParser.kt`, where our F6 fix peels the comment correctly but the declaration-matching regex still required a trailing `;` on every declaration — so `--b:4px}` (no trailing semicolon before `}`) was still dropped. The regex now accepts `;` *or* a lookahead for `}`, so the last declaration of a minified block is captured too.
- **`DESC` sort no longer reverses the tier hierarchy:** Latent comparator bug where `.reversed()` flipped every step including PREFIX / TOKEN_PREFIX / SUBSTRING. Only the value-level comparison reverses now; tier ordering is always PREFIX first.

### Added
- **Three-option completion sort order:** *Completion sort order* now offers **By value ascending** (default — smallest → largest for sizes, hue ascending for colours), **By value descending**, and **Alphabetical by name**. The preference is now applied consistently in both the blank-query autopopup path and fresh invocations via a new `CompletionWeigher` that breaks `matchingDegree` ties per user preference, so `--padding-sm / --padding-md / --padding-lg` always appear in the order you asked for regardless of how the popup was opened.
- **Completion description toggle + length setting:** Two new options under *Display Options* — a checkbox to hide the description that appears next to each completion item, and a spinner to clamp its length (0 hides, up to 120 characters).
- **Optional feedback prompt:** After ~14 days and 20 uses of CSS variable completion, a single non-intrusive balloon asks if you'd like to rate the plugin on Marketplace. Includes **Rate on Marketplace**, **Try PxPeek**, **Remind me later** (14-day snooze) and **Don't show again** actions. Prompt conditions are conservative and it will never re-appear once dismissed.
- **Cross-promotion with [PxPeek](https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints):** sibling plugin by the same author that adds inline pixel-equivalent hints for `rem`, `em`, `vh`, `%` and modern viewport units. Linked from the Marketplace description and the README.
- **Regression tests** for every fix above, including:
  - Commented-out declarations no longer appear as local overrides (`DocHelpersTest`).
  - Leading inline block comments no longer drop their declaration (`CssVariableEntryParserTest`).
  - Embedded and trailing `/* ... */` in values are stripped (`CssVariableEntryParserTest`).
  - Completion does not fire after `)` on the same line or between two `var()` calls (`CssVariableCompletionHarnessTest`).
  - Comment text does not leak into the completion value column (`CssVariableCompletionHarnessTest`).
- **Broader plugin verifier coverage:** PyCharm Professional and RubyMine added to the verifier IDE list — both bundle the JavaScript and CSS plugins we depend on.

### Changed
- **CSS variable index version bumped** so all users re-index once on first launch after upgrade — the parser now normalises values differently (inline comments stripped, leading inline comments no longer eat the declaration), and a rebuild avoids stale cached values bleeding into the popup.

---

## [1.7.2] – 2026-04-15

### Fixed
- **Completion insertion no longer duplicates `--`:** Selecting a suggestion inside `var(...)` now always produces the correct `var(--name)` — the previous release could insert `var(----name)`, `var(---name)`, or a trailing dash (`var(--name-)`) depending on when the popup opened. The fix also covers `var()` nested inside any other CSS function, e.g. `hsl(var(--bg))`, `rgba(var(--bg), 0.5)`, `color-mix(in oklch, var(--bg) 50%, white)`, `calc(var(--size) * 2)`, and arbitrarily nested constructs such as `linear-gradient(hsl(var(--a)), hsl(var(--b)))`.

### Added
- **Insertion-based completion regression tests:** New tests exercise actual document state after selecting a completion (not just which items are offered), covering bare `var(...)` usage and `var()` nested inside `hsl` / `hsla` / `rgb` / `rgba` / `oklch` / `color-mix` / `calc` / `clamp` / `linear-gradient`, plus multi-`var()` declarations and non-`var()` contexts.

---

## [1.7.1] – 2026-04-08

### Added
- **Completion scope regression tests:** Added tests to ensure CSS variable suggestions stay out of TypeScript files and CSS property-name completion.

### Fixed
- **Stylesheet-only completion registration:** The plugin no longer registers CSS variable completions for JavaScript, TypeScript, JSX, or TSX.
- **Value-context completion gating:** CSS variable suggestions now require a real `var(...)` context, preventing them from hijacking CSS property-name completion such as `background`.

---

## [1.7.0] – 2026-03-29

### Added
- **Marketplace compatibility regression tests:** Added tests to lock the manifest and Gradle metadata required for broader JetBrains IDE support.

### Changed
- **Broader IDE targeting:** The plugin now declares JavaScript + CSS dependencies explicitly and builds from an IntelliJ IDEA Ultimate base instead of a WebStorm-only target.
- **Verifier coverage:** Plugin verification is now configured for IntelliJ IDEA Ultimate, WebStorm, GoLand, and PhpStorm.
- **Release metadata:** Updated README and plugin change notes to reflect the expanded compatibility contract.

---

## [1.6.0] – 2025-06-20

### Added
- **Customizable Documentation Columns:** Users can now select which columns (e.g., Context, Value, Source, WCAG Contrast) are visible in the documentation popup via the settings panel.
- **Resolution Chain Tooltip:** Hovering over a variable in the documentation now displays a detailed tooltip showing the full resolution chain, explaining how the final value was derived.

### Changed
- **Performance and Memory Management:** Caching mechanisms have been overhauled for better performance and reduced memory usage, especially in large projects.
- **Dynamic Documentation Table:** The documentation popup now dynamically generates columns based on user settings and the variable's value type, improving clarity and relevance.
- **Settings UI:** The settings panel has been updated to include controls for the new column visibility feature.

### Fixed
- **Resolution Accuracy:** Corrected a bug where the resolution chain was not always fully preserved in the cache, ensuring documentation is always accurate.
- **UI Styling:** Fixed minor styling issues in the documentation table for better readability.

---

## [1.5.0] – 2025‑06‑15

### Added

- **IntelliJ 2024.1+ documentation API support** with rich popups and fallback for older builds.
- **Dynamic value-table columns** – documentation now auto-adds *px Eq.*, *Hex* and *WCAG* columns when relevant.
- **Recursively/derived variable completions** are now marked with (↗).
- **Improved DebugImportResolution helper** for tracing variable origins and import chains.
- **Legend for derived variables** – documentation now includes an explanation of the ↗ symbol when variables are
  resolved through imports or references.

### Changed

- **Completions and docs cascading logic** – now use the last value per context (cascading logic).
- **Improved context labeling** – media query parsing, color handling, and documentation rendering.
- **Centralized index versioning** – improved caching, and greater maintainability.
- **Winner-first documentation** – the actual winning value is now shown first in documentation tables for immediate
  clarity.

### Fixed

- **CSS cascade compliance** – local declarations now correctly override imported values, following proper CSS cascade
  rules.
- **Minor bugs in arithmetic resolution** for preprocessors.
- **Improved context collapsing** – color parsing, and documentation bugs.
- **Improved UX hierarchy** – better visual distinction between winning values (bold) and overridden declarations.

---

## [1.4.2] – 2025‑06‑12

### Added

- **Preprocessor variable index**: LESS and SCSS variables are now indexed for instant look‑ups using a new
  `PreprocessorVariableIndex`.
- **Value-based completion sorting**: Variable completions are sorted by their numeric value, with an option to choose
  ascending or descending order.
- **Pixel equivalent column**: Variable documentation now shows the pixel equivalent for rem/em/%/vh/vw/pt values.
- **Settings UI for sorting**: Added a configuration option to choose value-based variable sorting order (ascending or
  descending).
- **Comprehensive value-type utilities**: Added `ValueUtil` for classifying, comparing, and converting variable values
  for size, color, and number types.
- **Smarter context ranking**: Added `RankUtil` for logical sorting and ranking of context labels (e.g., default,
  min-width).
- **Extensive automated tests**: Added `ValueUtilTest` and `RankingTest` for robust value handling and context ranking.

### Changed

- **Improved performance and scope caching**: Variable key cache and scope caching now use stable, scope-aware maps for
  reliable completion and documentation.
- **Smarter var() detection**: Completion now works while typing incomplete `var(` and resolves aliases more quickly,
  even with missing parenthesis.
- **Documentation improvements**: Documentation tables conditionally display pixel equivalents and improved context
  sorting, with more robust and readable HTML output.
- **Refactored preprocessor resolution**: Preprocessor variable resolution is faster and now leverages the new index
  instead of scanning files directly.
- **Improved logging and error handling**: More robust cancellation checks and error reporting in completion and
  documentation providers.
- **Code cleanup**: Removed legacy and duplicated code, improved code comments, and enhanced maintainability.

### Fixed

- **Scope caching race conditions**: Caches for variable keys and preprocessor scopes are now properly invalidated and
  synchronized on changes, preventing duplicate completions and stale results.
- **Duplicate IDE completions**: Improved completion logic eliminates repeated suggestions.
- **Robustness**: Improved handling of edge cases in value parsing, prefix extraction, and context detection.
- **IDE startup and indexing**: Now compatible with latest IntelliJ indexing and plugin APIs.

### Removed

- Legacy file scan-based preprocessor variable resolution logic (now replaced by index-based resolution).
- Unused or obsolete code in tests and main sources.

---

## [1.4.1] – 2025‑06‑05

### Improved

* Completion suggestions load faster with a project-level cache of variable names.
  The cache is cleared whenever the index is rebuilt.

---

## [1.4.0] – 2025‑06‑02

### Changed

* **New defaults:** indexing scope now defaults to **GLOBAL** and **maxImportDepth** is **20** (was 10).
* Legacy settings are clamped to the new 1–20 range on load.
* Settings dialog pre‑selects **Global scope** on first run.

### Fixed

* LESS/SCSS alias chains ending in arithmetic expressions now resolve to their literal values (e.g.
  `--less-variable-spacing--md → 24px` not `--less-variable-spacing--md → @less-variable-spacing-md`).
* Minor NPEs in completion popup when evaluating invalid pre‑processor refs.

### Migration notes

Existing users keep their previous scope; switch manually via **Settings → Tools → CSS Variables Assistant** or hit *
*Reset to Defaults** to adopt the new behaviour.

---

## [1.3.1] – 2025-05-28

### Added

* **Enhanced Debug CSS Import Resolution** action – comprehensive import chain analysis with tree visualization,
  variable counting, and detailed resolution paths.
* **Dedicated completion cache** (`CssVarCompletionCache`) – separate caching system for LESS/SCSS variable resolution
  with improved performance.
* **Index rebuilder utility** (`CssVariableIndexRebuilder`) – centralized index management for better code organization.
* **Dynamic completion popup width** – automatically adjusts popup width based on longest variable name for better
  readability.
* **Enhanced progress reporting** – detailed progress indicators for re-indexing operations with step-by-step feedback.

### Changed

* **Code architecture improvements** – extracted cache and index management into dedicated utility classes for better
  maintainability.
* **Re-index process enhancement** – more detailed progress reporting with visual feedback and error handling.
* **Import resolution debugging** – comprehensive analysis shows full import trees, variable counts, and resolution
  failures.

### Fixed

* **Cache management** – resolved issues with stale cache entries affecting completion accuracy.
* **Index rebuilding reliability** – improved error handling and progress tracking during index operations.
* **Threading improvements** – better handling of background tasks and cancellation scenarios.
* **Memory optimization** – more efficient cache management reducing memory footprint.

---

## [1.3.0] – 2025-05-27

### Added

* **Dynamic pre-processor resolution** – fully resolves chained `@/ $/ --` references across LESS, SCSS, SASS & CSS,
  even inside imported packages.
* **Import-cache** – remembers every file reached through `@import`, giving instant look-ups after the first index pass.
* **🔄 Re-index Now** button – available in **Settings → Tools → CSS Variables Assistant**. Rebuilds the index without "
  Invalidate Caches / Restart".
* **Debug CSS Import Resolution** context-menu action – prints the exact chain of resolved `@import`s for the selected
  file.
* **IDE progress integration** – all long-running tasks are cancellable (Progress Manager).

### Changed

* **New default maxImportDepth** ⇢ `10` (was `3`).  
  Still user-configurable.
* **Icons** – always use the plugin shield for variables originating from the assistant, regardless of scope.
* **Scope utilities** – `ScopeUtil.effectiveCssIndexingScope()` and `currentPreprocessorScope()` consolidated; always
  compute a fresh scope for resolution paths.

### Fixed

* **Project + Imports scope** now resolves real values (e.g. `--theme-primary → #1a73e8`) instead of showing the
  intermediate `@lessVar`.
* Missing completions when switching from *Project Only* to *Project + Imports* without restarting.
* Race condition that occasionally produced `ProcessCanceledException` in large projects.
* Import-depth recursion guard respected across mixed preprocessor stacks.
* Global scope no longer double-indexes the same node_modules files.

---

## [1.2.0] – 2025‑05‑22

### Added

* **Advanced** `@import` resolution: intelligent resolution of `@import` statements across `CSS`, `SCSS`, `SASS`, and
  `LESS` files.
* **Smart** node_modules handling: properly resolves scoped packages like @company/package/path.
* **Configurable** indexing scope: choose between `project‑only`, `project + imports`, or `full global` indexing via
  Settings.
* **Import depth control:** configurable **maximum depth** for `@import` chains to **prevent** infinite recursion.
* **Enhanced settings panel:** fine‑tune plugin behavior with expanded configuration options under
  `Settings → Tools → CSS Variables Assistant`.

### Improved

* **Multi‑extension support:** automatically tries `.css`, `.scss`, `.sass`, `.less` extensions when resolving imports
  without explicit extensions.
* **Relative path resolution:** better handling of `./` and `../` import paths with proper extension priority based on
  importing file type.
* **Performance optimizations:** smarter file filtering and caching for **faster completion** suggestions in large
  projects.
* __Error handling:__ improved stability when processing malformed import statements or missing files.

### Fixed

* Import resolution now correctly distinguishes between relative paths and node_modules packages.
* Deprecated API usage replaced with modern IntelliJ Platform APIs.
* Better handling of circular import dependencies with visited file tracking.
* Improved compatibility with latest WebStorm/IntelliJ versions.

## [1.1.0] – 2025‑05‑19

### Added

- **Alias resolution:** `--alias: var(--real);` now resolves to the literal value in completion & docs.
- **Duplicate squashing:** identical `(context,value)` pairs collapse into one row.
- **Smart sorting in docs:** order is Default / Light → Dark → `max‑width` (desktop‑first, large → small) →
  `min‑width` (mobile‑first, small → large) → other media queries.
- **Light counts as primary:** `prefers-color-scheme: light` is treated the same as `default`.
- **Stricter index filter:** template/text files are ignored.

### Changed

- Completion “type text” mirrors the new smart order.
- Documentation shows context values instead of “(+N)” when context display is enabled.
- `DoubleColorIcon` now appears even when light/dark values alias to colours.

### Fixed

- No more duplicate “Default” rows for repeated declarations with the same value.
- Docs no longer show raw `var(--xyz)` when it can be resolved.
- Colour swatches & WebAIM links work for resolved aliases too.

## [1.0.2] – 2025‑05‑19

### Added

- **Settings page:** Plugin is now user-configurable (toggle context-based variable values).
- **Smarter completions:**
    - Dual color swatch if a variable has both light and dark color values.
    - All original value syntaxes shown (e.g., `#fff`, `hsl(0 0% 100%)`, `rgb(…))`).
    - Context-aware completions: e.g. “🌙” for dark mode, no overlays.
- **Color swatches:**
    - Now supports **shadcn/ui** color variables format, `--foreground: 0 0% 100%;`.
- **Robust comment parsing:** All major CSS comment styles now supported for variable docs (JSDoc, plain, single-line,
  multiline).
- **Context tracking:** Indexer now properly tracks context (media queries, dark/light, nested queries, etc).
- **Documentation enhancements:**
    - Multi-context variables shown as a table.
    - Color swatches always use original value syntax.
    - WebAIM contrast checker link for color variables.

### Changed

- **Consistent UI:**
    - No overlay/context icons on completion swatch—context info is right-aligned only.
    - Completions/docs now fully respect user settings.

### Fixed

- **Comment parsing bugs:** One-line, multi-line, and mixed-format doc-comments now always recognized.
- **Threading issues:** Docs and completions no longer trigger read-access warnings.

## [1.0.1] – 2025‑05‑18

### Added

- logo for the plugin

### Changed

- Alphabetical sorting when all variable values are non‑numeric
- Miscellaneous bugfixes and performance tweaks

### Fixed

- Completions now trigger immediately after typing `var(--`
- Completions now trigger without needing `--` inside the `var()`
- Completions now trigger when clicking inside an existing `var(--your-var)`

## [1.0.0] – 2025‑05‑16

### Added

- Production release: **v1.0.0**
- Autocomplete inside `var(…)`, sorted by CSS value (high → low)
- Hover & Quick‑Docs popups with color swatch and comment parsing
- Removed `@value` override. tags **always** shows the **real** CSS **value**

## [0.1.2 → 1.0.0] – 2025‑05‑15

### Changed

- Flipped suggestion order: largest values first
- Improved context checks and minor bugfixes
- Improved performance and reduced memory usage
- Improved documentation and code comments
]()
