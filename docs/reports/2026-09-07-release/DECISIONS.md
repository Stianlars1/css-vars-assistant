# Implementation decisions

Ruling: Existing audit tests that assert an implementation detail rather than user behavior can change only where the index contract demands it, with equivalent or stronger observable-behavior coverage. Cost if wrong: missed regression; final whole-branch review must inspect these changes.

Ruling: Implement a conservative language-aware resolver, not a full Sass/LESS compiler. Unsupported expressions retain their raw text and native IDE features remain available. Cost if wrong: some expressions remain unresolved; document exact supported behavior.

Ruling: Explicitly imported files outside indexable roots use cached per-file query snapshots rather than new index roots. This avoids external reads inside DataIndexer and preserves excluded/external dependencies. Cost if wrong: additional query-time IO; performance and modification-stamp tests cover the tradeoff.
