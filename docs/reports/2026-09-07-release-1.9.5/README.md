# CSS Variables Assistant 1.9.5 — verification

Version 1.9.5 restores native color documentation inside custom-property declarations, prevents invalid custom-property completions in `var()` fallback values, and propagates cancellation from import debugging. Both editor defects were reproduced on 1.9.3 and 1.9.4 before fixing them.

## Results

| Check | Result |
| --- | --- |
| Clean full suite on IntelliJ IDEA Ultimate 2025.1 | 486 passed; no failures, errors or skips |
| Clean full suite on IntelliJ IDEA 2026.2.2, build 262.10315.125 | 486 passed; no failures, errors or skips |
| Plugin structure and project configuration | Passed |
| Plugin Verifier 1.410, exact signed archive | Compatible on all nine targets |
| Publisher signature, Marketplace ZIP Signer 0.1.43 | Verified, exit 0 |
| Signed ZIP compared with unsigned ZIP | Identical entry names and uncompressed contents |
| Archive metadata | cssvarsassistant / 1.9.5 / since-build 251 |

The nine targets are six 2025.1 products (IntelliJ IDEA Ultimate, WebStorm, GoLand, PhpStorm, PyCharm Professional and RubyMine), IntelliJ IDEA 2026.2.1 and 2026.2.2, and the installed WebStorm 2026.1.5. Experimental API notices remain; every verdict is `Compatible`, with no compatibility errors.

The 32 added tests supplement the existing 454 tests. [RED evidence](red-reproductions.json) records the initial four failures and the expanded context/ownership/cancellation failures. All subsequently pass. No existing expectations were weakened.

## Change review

- Documentation ownership follows the original caret token when available. The resolved declaration cannot turn an unrelated value or foreign-language token into a plugin target. Controls preserve declaration names, references, preprocessor variables, HTML style blocks, Java/JavaScript/TypeScript documentation, and declaration requests without an original element.
- Completion uses one small argument-boundary helper on the editor document, with comments and strings masked. It recognizes the first argument of the nearest `var()` call, preserving nested fallback calls and incomplete/multiline typing. The duplicate PSI/text probing paths and their broad exception catches were removed. Existing insertion and sorting tests remain intact.
- The import-debug task can be exercised directly in a fixture. Cancellation is rethrown before error logging, including during variable-count reads. The regression test first reproduced the IDE error and now receives the platform cancellation exception.
- Indexes, import resolution, value resolution, cache behavior, settings defaults and plugin dependencies are unchanged. Upgrading from 1.9.4 requires no index rebuild or settings change.

The latest-platform run uses an isolated copy with Gradle IntelliJ plugin 2.18.1/Kotlin 2.4.10 and the cached 2026.2.2 IDE. Production files are byte-identical to the baseline. Its separate build filename leaves the original release build configuration available to the metadata tests; the release archive still uses Gradle plugin 2.13.1/Kotlin 2.1.20 and JVM target 21.

Checks exercise real platform PSI, documentation selection, completion, editor insertion, indexing mode and cancellation. No separate manual GUI pass or exhaustive third-party plugin matrix is claimed. Review was performed in this task, without delegating an independent review.

## Evidence and distribution

- [Baseline test results](tests-251.json)
- [2026.2.2 test results](tests-262.json)
- [Compatibility verdicts](compatibility.json)
- [Artifact identity and SHA-256](artifact.json)

The signed 1.9.5 archive was uploaded successfully to the Stable channel on 8 September 2026. JetBrains assigned update **1164049** and the authenticated version page shows **Under review**. It will become publicly available after JetBrains approval; Hidden was left disabled. The GitHub release is public, and its asset SHA-256 matches the verified local ZIP. See [publication receipts](publication.json).

References: [CSS var() syntax](https://www.w3.org/TR/css-variables-1/#using-variables), [JetBrains cancellation contract](https://plugins.jetbrains.com/docs/intellij/background-processes.html#handling-cancellation), and [original Java Quick Documentation issue #35](https://github.com/Stianlars1/css-vars-assistant/issues/35).
