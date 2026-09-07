package cssvarsassistant.audit

import cssvarsassistant.documentation.resolveVarValue
import cssvarsassistant.documentation.findPreprocessorVariableValue
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ResolutionEdgeCaseAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testRepeatedSiblingReferencesAreNotCycles() {
        addProjectStylesheet("tokens.css", ":root { --gap: 4px; }")
        assertEquals("calc(4px + 4px)", resolveVarValue(project, "calc(var(--gap) + var(--gap))").resolved)
    }

    fun testLastDefaultDeclarationWinsResolution() {
        addProjectStylesheet("tokens.css", ":root { --gap: 4px; --gap: 8px; }")
        assertEquals("8px", resolveVarValue(project, "var(--gap)").resolved)
    }

    fun testNestedFallbackDoesNotPreventKnownOuterVariableResolution() {
        addProjectStylesheet("tokens.css", ":root { --gap: 4px; --fallback: 8px; }")
        assertEquals("4px", resolveVarValue(project, "var(--gap, var(--fallback))").resolved)
    }

    fun testQuotedVarTextRemainsLiteral() {
        addProjectStylesheet("tokens.css", ":root { --gap: 4px; }")
        assertEquals("\"var(--gap)\"", resolveVarValue(project, "\"var(--gap)\"").resolved)
    }

    fun testResolutionKeepsOriginalExpressionForDerivedIndicator() {
        addProjectStylesheet("tokens.css", ":root { --gap: 4px; }")
        val result = resolveVarValue(project, "var(--gap)")
        assertEquals("var(--gap)", result.original)
        assertEquals("4px", result.resolved)
    }

    fun testLocalLessScopeDoesNotOverwriteGlobalToken() {
        addProjectStylesheet("tokens.less", "@brand: red;\n.local { @brand: blue; }\n.consumer { color: @brand; }")
        assertEquals("red", findPreprocessorVariableValue(project, "@brand")?.resolved)
    }

    fun testSassHyphenAndUnderscoreNamesReferToSameVariable() {
        addProjectStylesheet("tokens.scss", "${'$'}font_size: 16px;")
        assertEquals("16px", findPreprocessorVariableValue(project, "${'$'}font-size")?.resolved)
    }

    fun testPureCssCycleTerminates() {
        addProjectStylesheet("tokens.css", ":root { --a: var(--b); --b: var(--a); }")
        assertTrue(resolveVarValue(project, "var(--a)").resolved.startsWith("var("))
    }

    fun testPureLessCycleTerminates() {
        addProjectStylesheet("tokens.less", "@a: @b;\n@b: @a;")
        assertTrue(findPreprocessorVariableValue(project, "@a")!!.resolved.startsWith("@"))
    }

    fun testMixedLessAndCssCycleTerminates() {
        addProjectStylesheet("tokens.less", "@alias: var(--loop);\n:root { --loop: @alias; }")
        // The public resolver must return safely even for malformed/cyclic project code.
        val result = resolveVarValue(project, "var(--loop)")
        assertTrue(result.resolved.isNotBlank())
    }
}
