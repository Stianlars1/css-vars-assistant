# CSS Variables Assistant 1.9.4 — release verification

Source code approved at **6419a65**. The final source passed an independent release review after the discovered scope/hint issues and the large-cache reuse case were reproduced and fixed.

## Results

| Check | Result |
| --- | --- |
| Clean tests on IntelliJ IDEA Ultimate 2025.1 | 454 passed, 0 failed/errors/skipped |
| Clean tests on IntelliJ IDEA 2026.2.2 (262.10315.125) | 454 passed, 0 failed/errors/skipped |
| Plugin structure and project configuration | Passed |
| Plugin Verifier 1.410 | Compatible on all eight checked IDE builds |
| Publisher signature | Verified with JetBrains ZIP Signer 0.1.43, exit 0 |
| Archive identity | cssvarsassistant / 1.9.4 / since-build 251 |

The compatibility matrix covers six 2025.1 products (IntelliJ IDEA Ultimate, WebStorm, GoLand, PhpStorm, PyCharm Professional and RubyMine), plus IntelliJ IDEA 2026.2.1 and 2026.2.2. Experimental SDK notices remain, with no compatibility errors; the 262 notice is inherited through platform CssFunction/PsiExternalReferenceHost.

The baseline release artifact uses Gradle plugin 2.13.1, Kotlin 2.1.20 and JVM target 21. The separate 262 runtime test checkout uses Gradle plugin 2.18.1/Kotlin 2.4.10 to read current platform modules. Metadata tests inspect the actual release configuration. The exact signed baseline ZIP was also checked against both 262 IDE builds.

**Signed artifact:** cssvarsassistant-1.9.4-signed.zip (380063 bytes)

**SHA-256:** 8c5db4349d89f1adab5bc13a158e7f040a991bcc0e57ef888f8cd2fe664b2861

## Scope and evidence

The original 303 tests remain covered, with 151 added cases/control checks for the audit, implementation and final review. Index-contract tests now separately prove own-file indexing and actual imported query/source behavior. Test expectations were not weakened to accept incorrect variable values.

The plugin uses one file-local index model, explicit import queries, document/VFS-aware snapshots and shared source-aware resolution. It preserves native documentation outside applicable CSS-family tokens, retains raw ambiguous/unsupported expressions, and keeps existing completion matching, sorting and insertion behavior. An index rebuild occurs once after upgrade.

Editor behavior was exercised through real platform PSI/editor/completion fixtures. This record does not claim a separate manual visual GUI pass or full Sass/LESS compiler execution. Relative pixel equivalents remain estimates. See [the behavior contract](../../PLUGIN-CONTRACT.md) and [implementation decisions](DECISIONS.md).

Detailed evidence: [baseline tests](tests-251.json), [262 tests](tests-262.json), [compatibility matrix](compatibility.json), [artifact identity](artifact.json), [final review](release-review.md), [fix report](release-fix-report.md).

## Distribution

Uploaded successfully to the **Stable** channel on 7 September 2026. Marketplace update **1163704** is **Under review**; JetBrains approval/listing remains pending.

The [GitHub release v1.9.4](https://github.com/Stianlars1/css-vars-assistant/releases/tag/v1.9.4) is published with the signed ZIP. GitHub's asset digest matches the local verified SHA-256. The reviewed source and annotated v1.9.4 tag were pushed to main without force; the original dirty checkout was preserved.

[Marketplace update](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant/edit/versions/stable/1163704) · [Publication receipt](publication.json)
