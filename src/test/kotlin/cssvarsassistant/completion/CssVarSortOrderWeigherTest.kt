package cssvarsassistant.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CssVarSortOrderWeigherTest {

    // --- computeSortRank -----------------------------------------------------

    @Test
    fun `computeSortRank returns increasing values for increasing pixel sizes`() {
        val sm = CssVarSortOrderWeigher.computeSortRank("4px")
        val md = CssVarSortOrderWeigher.computeSortRank("8px")
        val lg = CssVarSortOrderWeigher.computeSortRank("16px")

        assertNotNull(sm); assertNotNull(md); assertNotNull(lg)
        assertTrue(sm!! < md!!, "4px rank should be less than 8px rank (was $sm vs $md)")
        assertTrue(md < lg!!, "8px rank should be less than 16px rank (was $md vs $lg)")
    }

    @Test
    fun `computeSortRank normalises across units via ValueUtil convertToPixels`() {
        val px = CssVarSortOrderWeigher.computeSortRank("16px")
        val rem = CssVarSortOrderWeigher.computeSortRank("1rem")

        assertNotNull(px)
        assertNotNull(rem)
        assertEquals(
            px, rem,
            "1rem resolves to 16px through ValueUtil.convertToPixels, so the ranks must match"
        )
    }

    @Test
    fun `computeSortRank returns increasing values for increasing raw numbers`() {
        val low = CssVarSortOrderWeigher.computeSortRank("100")
        val mid = CssVarSortOrderWeigher.computeSortRank("200")
        val high = CssVarSortOrderWeigher.computeSortRank("1000")

        assertNotNull(low); assertNotNull(mid); assertNotNull(high)
        assertTrue(low!! < mid!!)
        assertTrue(mid < high!!)
    }

    @Test
    fun `computeSortRank orders colours by hue ascending`() {
        val red = CssVarSortOrderWeigher.computeSortRank("#ff0000")
        val green = CssVarSortOrderWeigher.computeSortRank("#00ff00")
        val blue = CssVarSortOrderWeigher.computeSortRank("#0000ff")

        assertNotNull(red); assertNotNull(green); assertNotNull(blue)
        // Hue order in HSB: red (0°) < green (120°) < blue (240°).
        assertTrue(red!! < green!!, "red (hue 0) should rank less than green (hue 120)")
        assertTrue(green < blue!!, "green (hue 120) should rank less than blue (hue 240)")
    }

    @Test
    fun `computeSortRank returns null for non-orderable values`() {
        assertNull(CssVarSortOrderWeigher.computeSortRank("solid"))
        assertNull(CssVarSortOrderWeigher.computeSortRank("0 1px 2px rgba(0, 0, 0, 0.1)"))
    }

    // --- rank comparison invariants -----------------------------------------
    //
    // The weigher returns -rank for ASC and +rank for DESC. IntelliJ sorts
    // weights DESCENDING. So:
    //   - ASC  → smaller rank yields larger weight → ranks first.
    //   - DESC → larger rank yields larger weight → ranks first.
    // These tests lock the sign contract in so future refactors can't flip it.

    @Test
    fun `ASC weight order reverses rank order`() {
        val small = -CssVarSortOrderWeigher.computeSortRank("4px")!!
        val large = -CssVarSortOrderWeigher.computeSortRank("16px")!!

        // IntelliJ sorts DESC by weight; we want 4px ranked first → 4px's
        // weight must be LARGER than 16px's weight.
        assertTrue(
            small > large,
            "ASC preference: small pixel's weight must exceed large pixel's weight so the arranger ranks it first (was $small vs $large)"
        )
    }

    @Test
    fun `DESC weight order matches rank order`() {
        val small = CssVarSortOrderWeigher.computeSortRank("4px")!!
        val large = CssVarSortOrderWeigher.computeSortRank("16px")!!

        // DESC: large pixel first → large's weight must exceed small's.
        assertTrue(
            large > small,
            "DESC preference: large pixel's weight must exceed small pixel's weight (was $large vs $small)"
        )
    }
}
