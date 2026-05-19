# Discussion #24 - manual verification steps

GitHub discussion: <https://github.com/Stianlars1/css-vars-assistant/discussions/24>

## What changed in 1.9.0

CSS Variables Assistant now completes and documents direct preprocessor
variables, not only CSS custom properties inside `var(...)`.

- SCSS/Sass: `$brand-primary`, `$space-lg`
- LESS: `@brand-primary`, `@space-lg`
- CSS custom properties that point at imported preprocessor variables still
  resolve through the same recursive resolver.

## How to verify locally

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```
   The zip lands in `build/distributions/cssvarsassistant-<version>.zip`.

2. Sideload it into a sandbox IDE:
   ```bash
   ./gradlew runIde
   ```
   Or install the zip manually via *Settings -> Plugins -> Gear -> Install
   Plugin from Disk...* in your normal IDE.

3. Open this directory (`samples/discussion-24`) as a project, or copy the
   sample files into an existing project.

4. In `preprocessor-tokens.scss`:
   - Trigger completion at `$brand<caret>`.
   - Expected suggestions: `$brand-primary`, `$brand-secondary`.
   - Hover `$space-lg` and expect a resolution chain through `$space-base`
     to `8px`.
   - Hover `var(--card-gap)` and expect it to resolve through `$space-lg`.

5. In `preprocessor-tokens.sass`:
   - Trigger completion at `$brand<caret>`.
   - Expected suggestions: `$brand-primary`, `$brand-secondary`.
   - Hover `$space-lg` and expect the same alias-chain resolution.

6. In `preprocessor-tokens.less`:
   - Trigger completion at `@brand<caret>`.
   - Expected suggestions: `@brand-primary`, `@brand-secondary`.
   - Hover `@space-lg` and expect a resolution chain through `@space-base`
     to `8px`.

## Automated coverage

The same behavior is locked in by:

- `CssVariableCompletionHarnessTest`
- `PreprocessorCompletionContextTest`
- `PreprocessorVariableIndexTest`
- `DocHelpersResolveVarValueTest`
- `DocHelpersTest`

This sample is for manual side-loaded validation only. It is not bundled into
the published plugin.
