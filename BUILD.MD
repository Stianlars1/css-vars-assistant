# Build, test and release

The release build targets IntelliJ IDEA Ultimate 2025.1 (platform 251) and Java 21. The Gradle wrapper selects the repository's Gradle version. CSS/JavaScript are required plugin dependencies; Sass and LESS must not become hard class-loading dependencies of the global documentation provider.

See [the behavior contract](docs/PLUGIN-CONTRACT.md) before changing indexing, resolution or editor integration.

## Local verification

```sh
./gradlew clean test --no-build-cache
./gradlew check buildPlugin
./gradlew verifyPlugin
```

The first command exercises the complete suite from clean outputs. It is particularly useful after changes to Kotlin default-argument or constructor signatures, where stale instrumented classes can otherwise give misleading linkage errors.

The verification matrix in build.gradle.kts selects supported release builds in the 2025.1 family for IntelliJ IDEA Ultimate, WebStorm, GoLand, PhpStorm, PyCharm Professional and RubyMine. Inspect the actual verdict files under build/reports/pluginVerifier; successful compilation alone is not a compatibility result. The documented experimental DocumentationTarget API is allowed; compatibility errors and other configured failure levels must be resolved.

For newer platform runtime tests, use a separate temporary checkout with tooling capable of reading that platform's module descriptors. Keep the release artifact built against the supported baseline, and validate that exact ZIP against newer IDEs with Plugin Verifier. Metadata tests must still inspect the actual release build configuration, not the temporary compatibility-tooling override.

## Editor behavior

The fixture suite tests real CSS/SCSS/Sass/LESS PSI and completion, inserted document text, hover/hint content, source scope, aliases, Java documentation fallback, imported files and saved/unsaved changes. Preserve existing issue regressions and add a failing fixture before fixing a new case.

For a visual sandbox check:

```sh
./gradlew runIde
```

Open a disposable project containing representative stylesheets. Check completion/Enter/Tab, quick documentation, local and imported tokens, theme rows, edits after first lookup and plugin unload/reload. Describe fixture verification and manual GUI checks separately in release evidence.

## Signing

Use the existing publisher certificate and private key. Do not generate a new identity for routine releases, commit keys, echo credentials or put secrets in command arguments. The build reads these environment variables:

- JETBRAINS_CERTIFICATE_CHAIN
- JETBRAINS_PRIVATE_KEY
- JETBRAINS_PRIVATE_KEY_PASSWORD

With those supplied securely to the process:

```sh
./gradlew signPlugin --no-configuration-cache
```

The signed archive is written under build/distributions. Verify it with the JetBrains Marketplace ZIP Signer CLI using its `verify -in SIGNED_ZIP -cert CERTIFICATE_FILE` command. The Gradle plugin's 2.13.1 verifyPluginSignature task has a known certificate-content/path mismatch; the standalone CLI avoids that tooling issue.

## Publishing

1. Update the version, README, CHANGELOG.MD and Marketplace description/change notes.
2. Complete tests, code review, structure/configuration checks and the IDE compatibility matrix.
3. Build and sign the final artifact; verify its signature and record its SHA-256.
4. Push the reviewed source and release tag without force.
5. Upload the exact signed ZIP through the authenticated [Marketplace version page](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant/edit/versions), or use publishPlugin with a securely supplied JETBRAINS_MARKETPLACE_TOKEN.
6. Create the matching GitHub release with the signed ZIP.
7. Confirm the upload/version ID and distinguish uploaded, awaiting review, and approved/listed states.

Retain the previous release artifact as a rollback reference. Uploading an artifact does not prove that JetBrains has approved or listed it.
