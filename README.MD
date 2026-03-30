# CSS Variables Assistant  🔧🎨

**Version:** 1.7.0<br/>
**Compatible IDEs:** JetBrains IDEs with bundled JavaScript + CSS support, including WebStorm, IntelliJ IDEA Ultimate, GoLand, and PhpStorm.  
**Author:** Stian Larsen  
**Repo:** [github.com/Stianlars1/css-vars-assistant](https://github.com/Stianlars1/css-vars-assistant)

**Links:**
- [Official Plugin Website](https://www.css-variables-assistant.dev)
- [Official JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant)
- [Stian Larsen](https://stianlarsen.com)


Supercharge your CSS custom properties and preprocessor variables in JetBrains IDEs with advanced autocomplete, documentation, and debugging tools.
---

## ✨ What's new in 1.7.0

| Category                    | Highlights                                                                                      |
|-----------------------------|-------------------------------------------------------------------------------------------------|
| **🧩 Broader IDE Support**   | Marketplace metadata now targets the wider JetBrains JavaScript/CSS IDE family.                 |
| **🧪 Compatibility Tests**   | Added regression tests to prevent future Marketplace compatibility drift.                       |
| **🔎 Verifier Coverage**     | Verification is now configured for IntelliJ IDEA Ultimate, WebStorm, GoLand, and PhpStorm.     |
| **📦 Release Metadata**      | Plugin release notes and README now reflect the broader compatibility contract.                 |

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
| **Indexing scope** | *Project only* • *Project + imports* (experimental) • *Full global* (default) |
| **Max @import depth** | 1 – 20 (default 20)                                                           |
| **Completion sorting** | Ascending or descending value order                                           |
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

## Building & Testing Locally

```bash
git clone https://github.com/stianlars1/css-vars-assistant.git
cd css-vars-assistant
./gradlew clean buildPlugin       # produces ZIP under build/distributions
./gradlew runIde                  # start sandbox IDE
```
