# CSS Variables Assistant **(--)** Changelog

All notable changes to this project will be documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/) and semantic-versioning.

---

## [1.4.2] – 2025‑06‑12
### Added
* **Preprocessor variable index** – LESS and SCSS variables are indexed for instant look‑ups.
* **Value‑based completion sorting** – variables sort by numeric value with ascending/descending option.
* **Pixel equivalent column** – documentation now shows the px value when a size isn't in px.

### Improved
* More reliable `var()` detection and prefix extraction while typing.
* Preprocessor variable resolution uses the new index for faster results.

### Fixed
* Scope caching race conditions and duplicate IDE completions.

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
* LESS/SCSS alias chains ending in arithmetic expressions now resolve to their literal values (e.g. `--less-variable-spacing--md → 24px` not `--less-variable-spacing--md → @less-variable-spacing-md`).
* Minor NPEs in completion popup when evaluating invalid pre‑processor refs.

### Migration notes
Existing users keep their previous scope; switch manually via **Settings → Tools → CSS Variables Assistant** or hit **Reset to Defaults** to adopt the new behaviour.

---

## [1.3.1] – 2025-05-28
### Added
* **Enhanced Debug CSS Import Resolution** action – comprehensive import chain analysis with tree visualization, variable counting, and detailed resolution paths.
* **Dedicated completion cache** (`CssVarCompletionCache`) – separate caching system for LESS/SCSS variable resolution with improved performance.
* **Index rebuilder utility** (`CssVariableIndexRebuilder`) – centralized index management for better code organization.
* **Dynamic completion popup width** – automatically adjusts popup width based on longest variable name for better readability.
* **Enhanced progress reporting** – detailed progress indicators for re-indexing operations with step-by-step feedback.

### Changed
* **Code architecture improvements** – extracted cache and index management into dedicated utility classes for better maintainability.
* **Re-index process enhancement** – more detailed progress reporting with visual feedback and error handling.
* **Import resolution debugging** – comprehensive analysis shows full import trees, variable counts, and resolution failures.

### Fixed
* **Cache management** – resolved issues with stale cache entries affecting completion accuracy.
* **Index rebuilding reliability** – improved error handling and progress tracking during index operations.
* **Threading improvements** – better handling of background tasks and cancellation scenarios.
* **Memory optimization** – more efficient cache management reducing memory footprint.

---

## [1.3.0] – 2025-05-27
### Added
* **Dynamic pre-processor resolution** – fully resolves chained `@/ $/ --` references across LESS, SCSS, SASS & CSS, even inside imported packages.
* **Import-cache** – remembers every file reached through `@import`, giving instant look-ups after the first index pass.
* **🔄 Re-index Now** button – available in **Settings → Tools → CSS Variables Assistant**. Rebuilds the index without "Invalidate Caches / Restart".
* **Debug CSS Import Resolution** context-menu action – prints the exact chain of resolved `@import`s for the selected file.
* **IDE progress integration** – all long-running tasks are cancellable (Progress Manager).

### Changed
* **New default maxImportDepth** ⇢ `10` (was `3`).  
  Still user-configurable.
* **Icons** – always use the plugin shield for variables originating from the assistant, regardless of scope.
* **Scope utilities** – `ScopeUtil.effectiveCssIndexingScope()` and `currentPreprocessorScope()` consolidated; always compute a fresh scope for resolution paths.

### Fixed
* **Project + Imports scope** now resolves real values (e.g. `--theme-primary → #1a73e8`) instead of showing the intermediate `@lessVar`.
* Missing completions when switching from *Project Only* to *Project + Imports* without restarting.
* Race condition that occasionally produced `ProcessCanceledException` in large projects.
* Import-depth recursion guard respected across mixed preprocessor stacks.
* Global scope no longer double-indexes the same node_modules files.

---

## [1.2.0] – 2025‑05‑22
### Added
* **Advanced** `@import` resolution: intelligent resolution of `@import` statements across `CSS`, `SCSS`, `SASS`, and `LESS` files.
* **Smart** node_modules handling: properly resolves scoped packages like @company/package/path.
* **Configurable** indexing scope: choose between `project‑only`, `project + imports`, or `full global` indexing via Settings.
* **Import depth control:** configurable **maximum depth** for `@import` chains to **prevent** infinite recursion.
* **Enhanced settings panel:** fine‑tune plugin behavior with expanded configuration options under `Settings → Tools → CSS Variables Assistant`.

### Improved
* **Multi‑extension support:** automatically tries `.css`, `.scss`, `.sass`, `.less` extensions when resolving imports without explicit extensions.
* **Relative path resolution:** better handling of `./` and `../` import paths with proper extension priority based on importing file type.
* **Performance optimizations:** smarter file filtering and caching for **faster completion** suggestions in large projects.
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
- **Smart sorting in docs:** order is Default / Light → Dark → `max‑width` (desktop‑first, large → small) → `min‑width` (mobile‑first, small → large) → other media queries.
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
- **Robust comment parsing:** All major CSS comment styles now supported for variable docs (JSDoc, plain, single-line, multiline).
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
