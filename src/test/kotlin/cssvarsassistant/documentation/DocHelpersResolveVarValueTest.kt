package cssvarsassistant.documentation

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class DocHelpersResolveVarValueTest : CssVarsAssistantPlatformTestCase() {

    // Issue #21 — Radix Themes scenario: `--scaling` is defined only under
    // five non-default selectors with non-uniform values. The previous
    // resolver picked the first one (0.9) AND dropped the surrounding
    // calc(...) wrapper, displaying bare `0.9` instead of the calc
    // expression. Now the var reference must be left intact.
    fun testAmbiguousNestedVarLeavesReferenceIntact() {
        addProjectStylesheet(
            "radix-themes.css",
            """
            .radix-themes:where([data-scaling='90%'])  { --scaling: 0.9;  }
            .radix-themes:where([data-scaling='95%'])  { --scaling: 0.95; }
            .radix-themes:where([data-scaling='100%']) { --scaling: 1;    }
            .radix-themes:where([data-scaling='105%']) { --scaling: 1.05; }
            .radix-themes:where([data-scaling='110%']) { --scaling: 1.1;  }
            """
        )

        val info = resolveVarValue(project, "calc(8px * var(--scaling))")
        assertEquals("calc(8px * var(--scaling))", info.resolved)
    }

    // When every entry across all selectors holds the same value, the
    // resolution is unambiguous regardless of context labels — substitute it.
    fun testNestedVarWithUniformValuesStillSubstitutes() {
        addProjectStylesheet(
            "uniform-scaling.css",
            """
            .a { --scaling: 1; }
            .b { --scaling: 1; }
            """
        )

        val info = resolveVarValue(project, "calc(8px * var(--scaling))")
        assertEquals("calc(8px * 1)", info.resolved)
    }

    // A `default` (`:root`) declaration anchors the value even when other
    // themed overrides exist — preserves the historical cascade-winner
    // semantics for the common single-theme case.
    fun testNestedVarWithDefaultContextStillSubstitutes() {
        addProjectStylesheet(
            "default-anchored.css",
            """
            :root { --scaling: 1; }
            .radix-themes:where([data-scaling='90%']) { --scaling: 0.9; }
            """
        )

        val info = resolveVarValue(project, "calc(8px * var(--scaling))")
        assertEquals("calc(8px * 1)", info.resolved)
    }

    // Explicit regression for the "drops the calc wrapper" half of issue #21:
    // even when substitution is unambiguous, the surrounding text must
    // survive. Previously the recursive call passed `defVal` as the new raw
    // and `calc(8px * var(--unit))` resolved to `4px` instead of
    // `calc(8px * 4px)`.
    fun testCalcWrapperPreservedAroundResolvedVar() {
        addProjectStylesheet(
            "unit.css",
            """
            :root { --unit: 4px; }
            """
        )

        val info = resolveVarValue(project, "calc(8px * var(--unit))")
        assertEquals("calc(8px * 4px)", info.resolved)
    }
}
