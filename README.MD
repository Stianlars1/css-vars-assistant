# CSS Variables Assistant  🔧🎨

**Version:** 1.4.2<br/>
**Compatible IDEs:** WebStorm / IntelliJ Ultimate **2025.1+**<br/>
**Author:** Stian Larsen<br/>
**Links:** 
- [Official Plugin Website](https://www.css-variables-assistant.dev)
- [Official JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant)
- [Stian Larsen](https://stianlarsen.com)



Boosts CSS Custom Properties with smart completion, rich documentation and full pre-processor support.
---
## ✨ What's new in 1.4.2

| Category | Highlights |
|----------|------------|
| **🛠️ Preprocessor index** | LESS/SCSS variables are indexed for instant resolution. |
| **🔍 Smarter var() detection** | Completion works while typing incomplete `var(` and resolves aliases quicker. |
| **📏 Value-based sorting** | Completions sort by numeric value with ascending/descending option. |
| **📐 Docs show px equivalents** | Non‑px size values display their pixel conversion. |

---
## ✨ What's new in 1.4.1

| Category | Highlights |
|----------|------------|
| **⚡ Faster completions** | CSS variable names are cached per project to avoid repeated index lookups. Caches clear automatically on index rebuild. |

---
## ✨ What's new in 1.4.0

| Category | Highlights                                                                                                                               |
|----------|------------------------------------------------------------------------------------------------------------------------------------------|
| **🌍 Global by default** | Plugin now indexes <code>node_modules</code> automatically – variables from external design-system packages resolve without extra setup. |
| **🧮 Smarter arithmetic** | LESS arithmetic (e.g. <code>@var: (@base * 3)</code>) is evaluated, so completion & docs show the final numeric value.                   |
| **🔄 Seamless migration** | Your previous scope choice is preserved; only new projects start with the global scope.                                                  |
---

### Previous highlights from 1.3.1

| Category | Highlights |
|----------|------------|
| **🔍 Enhanced debugging** | Comprehensive **Debug CSS Import Resolution** action with tree visualization, variable counting, and detailed resolution paths. |
| **⚡ Performance improvements** | Dedicated completion cache (`CssVarCompletionCache`) and optimized memory management for faster variable resolution. |
| **📐 Dynamic UI** | Completion popup width automatically adjusts based on longest variable name for better readability. |
| **🔄 Better re-indexing** | Enhanced progress reporting with step-by-step feedback and improved error handling during index rebuilds. |
| **🏗️ Architecture cleanup** | Code refactoring with extracted utility classes for better maintainability and stability. |

### Previous highlights from 1.3.0

| Category | Highlights |
|----------|------------|
| **🚀 Value resolution** | Follows chained `@less`, `$scss` and alias `var(--foo)` references – even across imported packages – until it reaches a literal value. |
| **📦 Import cache** | Remembers every file reached via `@import`, giving instant look-ups after the first pass. |
| **🔄 Re-index Now button** | One-click rebuild in **Settings → Tools → CSS Variables Assistant** – no more *Invalidate Caches / Restart*. |
| **🧭 Debug action** | *Debug CSS Import Resolution* (context menu) prints the full import chain and indicates skipped circular references. |
| **🛡️ Stability** | Race conditions fixed, proper cancellation handling, fewer `ProcessCanceledException`s in large repos. |

See full details in [`CHANGELOG.MD`](./CHANGELOG.MD).

---

## Core Features

* **Context-aware completion** inside `var(-- … )`
* **Quick Documentation** (<kbd>Ctrl Q</kbd> / hover) with table, swatches, description & examples
* **@import traversal** for CSS, SCSS, SASS & LESS with smart extension inference
* **Recursive var() & pre-processor alias resolution**
* **JSDoc-style tags** `@name`, `@description`, `@example`
* Works inside **CSS / SCSS / SASS / LESS / JS / TS / JSX / TSX**

---

## Configuration

Open **Settings → Tools → CSS Variables Assistant**

| Option | Effect                                                             |
|--------|--------------------------------------------------------------------|
| **Indexing scope** | *Project only* • *Project + imports* (recommended) • *Full global* |
| **Max @import depth** | 1 – 20 (default 20)                                                |
| **Show context values** | Display dark/light & media-query variants                          |
| **Allow IDE built-in completions** | Fall back to IntelliJ suggestions for misses                       |
| **Completion sorting** | Ascending or descending value order |
| **🔄 Re-index Now** | Flush caches and rebuild the variable index immediately            |

---

---

## 🐞 Debugging import chains

Need to verify *exactly* which files your `@import`s resolve to?
1. **Right-click** any *.css / .scss / .sass / .less* file in the Project view <br>
   *or* inside the editor.
2. Select **“Debug CSS Import Resolution”**.
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
