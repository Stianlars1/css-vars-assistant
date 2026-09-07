# CSS Variables Assistant: behavior and implementation contract

The plugin helps developers discover design tokens and understand their values while editing CSS, SCSS, indented Sass and LESS. Its user-facing surfaces are variable completion, quick documentation, value/color previews and import-chain debugging.

## Preserve these workflows

- CSS custom-property declarations and var() references, including theme/media alternatives and declaration comments.
- Direct $variables in SCSS/Sass and @variables in LESS, imported aliases and aliases to CSS custom properties.
- The existing prefix matching, exact-match priority, insertion behavior and three completion sort modes.
- Project-only, project-plus-imports and global discovery settings. Project-only excludes node_modules. Project-plus-imports includes explicitly reached dependencies, including files the IDE does not index itself.
- Source attribution to the real declaration file and line.
- Native IDE documentation/completion when the plugin has no applicable variable result, especially in other languages and at stylesheet at-rules.
- Safe handling of incomplete code, cycles and canceled editor work.

## Architecture boundaries

File-based indexes derive data only from the FileContent they receive. They never load imported files or depend on application scope settings. Import scope is a query concern. This follows the [IntelliJ file-based index contract](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html#implementing-a-file-based-index).

Shared text scanning distinguishes strings, escapes, comments, balanced values and structural delimiters. CSS block context and Sass indentation are handled separately. Preprocessor declaration data retains scope and source position; it is not a global last-value-wins map.

Imported files outside indexable roots are read through a per-file snapshot keyed by VFS and document modification stamps. Query state is invalidated on file/document changes. Resolution results are reused only within a single request, so caller-specific paths and stale values cannot leak to later requests.

Completion, documentation and hints use the same source-aware lookup/resolution layer. Presentation preserves literal value spelling and never changes a case-sensitive URL or string to lowercase.

## Analysis limits

The plugin is not a Sass/LESS compiler or browser rendering engine. Preserve raw expressions where static evaluation is not supported or is ambiguous. It must not claim to determine the runtime CSS cascade without DOM, matching selectors and stylesheet load order.

Relative-unit pixel equivalents remain estimates based on documented default font/viewport assumptions. Color previews support the parser's tested formats; unsupported color functions remain visible as raw values. New syntax support must come with a small reproducible fixture and native-fallback coverage.

## Release evidence

Before release, run a clean full test suite, plugin structure/configuration validation, Plugin Verifier against the supported baseline and current IDE targets, and focused editor behavior checks. Preserve previous issue regressions when refactoring. A successful upload means the artifact reached Marketplace; JetBrains approval/listing is a separate state.
