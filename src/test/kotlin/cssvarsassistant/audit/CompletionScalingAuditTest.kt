package cssvarsassistant.audit

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase
import kotlin.system.measureNanoTime

/** Diagnostic measurements only: machine-dependent timings are not pass/fail gates. */
class CompletionScalingAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testCompletionScalingDiagnostic() {
        for (count in listOf(100, 1000)) {
            val source = buildString {
                append(":root {\n")
                repeat(count) { append("--audit-$count-$it: ${it + 1}px;\n") }
                append("}\n.a { padding: var(--audit-$count-<caret>); }")
            }
            configureProjectFile("tokens$count.css", source)
            assertFalse(readIndexedCssEntries("--audit-$count-0").isEmpty())
            repeat(3) { run ->
                var itemCount = 0
                val elapsed = measureNanoTime {
                    itemCount = myFixture.completeBasic()?.size ?: 0
                }
                assertTrue("Expected plugin suggestions", itemCount > 0)
                myFixture.lookup?.hideLookup(true)
                println("AUDIT_COMPLETION count=$count run=$run items=$itemCount elapsedMs=${elapsed / 1_000_000.0}")
            }
        }
    }
}
