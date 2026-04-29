# Issue #22 — manual verification steps

GitHub issue: <https://github.com/Stianlars1/css-vars-assistant/issues/22>

## What broke

A value like `--accent: #7F80FF1A` (modern CSS Color Level 4 `#RRGGBBAA` syntax)
was rendered in the hover popup's `Hex` column as `#80FF1A`. Two compounding
bugs were responsible:

- `ColorParser.parseHexColor` treated the 8-digit form as Java's `#AARRGGBB`
  packed-int order (alpha *first*) instead of the CSS spec's `#RRGGBBAA`
  (alpha *last*). For `#7F80FF1A` that meant red was read as `80`, green as
  `FF`, blue as `1A`, and `7F` was assigned to alpha.
- `ColorParser.colorToHex` and the local `Color.toHex()` extension always
  formatted six digits, unconditionally dropping any alpha that *had* been
  parsed.

Combined, both bugs produced the visible `#80FF1A` truncation reported by
[@LordMaddhi](https://github.com/LordMaddhi).

## How to verify the 1.8.6 fix locally

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```
   The signed zip lands in `build/distributions/cssvarsassistant-<version>.zip`.

2. Sideload it into a fresh sandbox IDE:
   ```bash
   ./gradlew runIde
   ```
   Or install the zip manually via *Settings → Plugins → ⚙ → Install Plugin
   from Disk…* in your normal IDE.

3. Open this directory (`samples/issue-22`) as a project, or copy
   `hex-alpha.css` into any existing project.

4. Hover each `var(...)` reference inside `.card { … }`:

   | Hover target | Expected `Hex` column | Expected swatch |
   |---|---|---|
   | `var(--accent-translucent)` | `#7f80ff1a` | translucent blue |
   | `var(--backdrop-50)` | `#00000080` | half-transparent black |
   | `var(--primary-12)` | `#ff008080` | half-transparent magenta |
   | `var(--tooltip-bg)` | `#aabbccdd` | translucent grey-blue |
   | `var(--solid-blue)` | `#1e90ff` | opaque blue |
   | `var(--solid-with-FF-suffix)` | `#1e90ff` | opaque blue (8-digit `#1E90FFFF` collapses to 6 digits) |

   The translucent rows MUST keep all eight hex digits — that was the
   pre-fix bug. The opaque rows MUST still render as six digits so existing
   snapshots and the WebAIM contrast-checker URL aren't disturbed.

5. Trigger completion inside `background: var(--<caret>)`:
   - The popup must show a swatch and Hex column for `--accent-translucent`,
     `--backdrop-50`, `--tooltip-bg`, and `--primary-12`. Pre-fix the swatch
     was missing for `--tooltip-bg` because 4-digit `#RGBA` shorthand was
     silently rejected by the parser.

## Automated coverage

The same scenarios are locked in by
`src/test/kotlin/ColorParserTest.kt`, which runs in `./gradlew test`.
The file in this directory is for manual side-loaded validation only —
it isn't bundled into the published plugin.
