# CSS Variables Assistant 1.9.6

Fixes the completion sorting crash reported in [#36](https://github.com/Stianlars1/css-vars-assistant/issues/36). PR #37 is merged. Size and number families retain their value ordering, with consistent exact-name and full-prefix priority across mixed token catalogs.

The release updates the version, README, changelog, Marketplace description and embedded change notes. No settings change or index rebuild is required when upgrading from 1.9.5.

Verification of the final release:

- 499 tests passed on IntelliJ IDEA 2025.1 and 2026.2.2, with no failures, errors or skips.
- Plugin build, structure and project configuration checks passed.
- Signed with the existing publisher identity; signature verified with Marketplace ZIP Signer 0.1.43.
- Signed and unsigned archives have identical entry names and uncompressed contents.
- Plugin Verifier 1.410 found the exact signed ZIP compatible on all ten IDE targets. Existing experimental API notices remain.
- Source and tests are identical to the previously verified fix. Only release metadata and documentation changed after merge.

See [verification.json](verification.json) for test totals, IDE builds, artifact size and SHA-256. The [issue investigation](../2026-09-14-issue-36/README.md) contains the original failing reproduction and the change review. Runtime verification uses real IntelliJ fixtures; no separate manual GUI installation is claimed.

## Publication

PR #37 is merged. Release commit `e93ff2c` and tag `v1.9.6` are pushed. The [GitHub release](https://github.com/Stianlars1/css-vars-assistant/releases/tag/v1.9.6) is public; an independent download matches the signed local ZIP byte-for-byte by SHA-256.

The signed ZIP was uploaded successfully to Marketplace Stable as update [1169852](https://plugins.jetbrains.com/plugin/27392-css-variables-assistant/versions/stable/1169852). Hidden was left disabled, and the authenticated version page shows **Under review** with the 1.9.6 change notes. JetBrains approval and public IDE-update availability remain pending. See [publication.json](publication.json) for the receipts.
