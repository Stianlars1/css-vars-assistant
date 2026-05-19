package cssvarsassistant.documentation

import cssvarsassistant.settings.CssVarsAssistantSettings
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

    fun testImportedScssCustomPropertyResolvesThroughNestedPreprocessorAliasChain() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_foundation.scss",
            """
            ${'$'}space-base: 8px;
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/design/_tokens.scss",
            """
            @import "./foundation";
            ${'$'}space-lg: ${'$'}space-base;

            :root {
              --space-lg: ${'$'}space-lg;
            }
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        val info = resolveVarValue(project, "var(--space-lg)")

        assertEquals("8px", info.resolved)
        assertEquals(listOf("var(--space-lg)", "\$space-lg", "\$space-base"), info.steps)
    }

    fun testImportedScssCustomPropertyRespectsMaxImportDepth() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 1
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_foundation.scss",
            """
            ${'$'}space-base: 8px;
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/design/_tokens.scss",
            """
            @import "./foundation";
            ${'$'}space-lg: ${'$'}space-base;

            :root {
              --space-lg: ${'$'}space-lg;
            }
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        val info = resolveVarValue(project, "var(--space-lg)")

        assertEquals("\$space-base", info.resolved)
        assertEquals(listOf("var(--space-lg)", "\$space-lg"), info.steps)
    }

    fun testImportedScssArithmeticAliasChainResolvesRecursively() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_foundation.scss",
            """
            ${'$'}space-base: 8px;
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/design/_tokens.scss",
            """
            @import "./foundation";
            ${'$'}space-lg: (${'$'}space-base * 2);

            :root {
              --space-lg: ${'$'}space-lg;
            }
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        val info = resolveVarValue(project, "var(--space-lg)")

        assertEquals("16px", info.resolved)
        assertEquals(
            listOf("var(--space-lg)", "\$space-lg", "\$space-base", "(8px * 2) = 16px"),
            info.steps
        )
    }

    fun testPrefixedScssReferenceDoesNotFallbackToLessVariableWithSameBareName() {
        addProjectStylesheet(
            "tokens.less",
            """
            @brand-primary: #7f80ff;
            """
        )

        val info = resolveVarValue(project, "\$brand-primary")

        assertEquals("\$brand-primary", info.resolved)
        assertTrue(info.steps.isEmpty())
    }

    fun testCircularPreprocessorAliasesReturnWithoutInfiniteRecursion() {
        addProjectStylesheet(
            "tokens.scss",
            """
            ${'$'}a: ${'$'}b;
            ${'$'}b: ${'$'}a;
            """
        )

        val info = resolveVarValue(project, "\$a")

        assertEquals("\$a", info.resolved)
        assertTrue(info.steps.isEmpty())
    }

    fun testImportedSassCustomPropertyResolvesThroughNestedPreprocessorAliasChain() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/sass/_foundation.sass",
            """
            ${'$'}space-base: 8px
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/sass/_tokens.sass",
            """
            @import "./foundation"
            ${'$'}space-lg: ${'$'}space-base

            :root
              --space-lg: ${'$'}space-lg
            """
        )
        addProjectStylesheet(
            "styles/app.sass",
            """
            @import "@vendor/sass/tokens"
            """
        )

        val info = resolveVarValue(project, "var(--space-lg)")

        assertEquals("8px", info.resolved)
        assertEquals(listOf("var(--space-lg)", "\$space-lg", "\$space-base"), info.steps)
    }

    fun testImportedLessCustomPropertyResolvesThroughNestedPreprocessorAliasChain() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/less/base.less",
            """
            @space-base: 8px;
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/less/tokens.less",
            """
            @import "./base";
            @space-lg: @space-base;

            :root {
              --space-lg: @space-lg;
            }
            """
        )
        addProjectStylesheet(
            "styles/app.less",
            """
            @import "@vendor/less/tokens";
            """
        )

        val info = resolveVarValue(project, "var(--space-lg)")

        assertEquals("8px", info.resolved)
        assertEquals(listOf("var(--space-lg)", "@space-lg", "@space-base"), info.steps)
    }
}
