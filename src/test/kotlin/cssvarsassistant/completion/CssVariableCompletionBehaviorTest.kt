package cssvarsassistant.completion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CssVariableCompletionBehaviorTest {

    @Test
    fun `stops completion when plugin has matches`() {
        assertTrue(shouldStopAfterCssVarCompletion(entryCount = 2, allowIdeCompletions = true))
    }

    @Test
    fun `allows IDE fallback when plugin has no matches and fallback is enabled`() {
        assertFalse(shouldStopAfterCssVarCompletion(entryCount = 0, allowIdeCompletions = true))
    }

    @Test
    fun `stops completion when plugin has no matches and fallback is disabled`() {
        assertTrue(shouldStopAfterCssVarCompletion(entryCount = 0, allowIdeCompletions = false))
    }
}
