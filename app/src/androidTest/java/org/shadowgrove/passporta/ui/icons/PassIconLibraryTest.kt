package org.shadowgrove.passporta.ui.icons

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies search and automatic assignment of the symbols.
 *
 * Deliberately an instrumented test: the library holds `ImageVector` instances from Compose that
 * are only built when the class is loaded. On a device this is guaranteed to work; in a plain
 * JVM test it depends on how the Compose artifacts are provided.
 */
@RunWith(AndroidJUnit4::class)
class PassIconLibraryTest {

    @Test
    fun suggestsTrainSymbolForTrainTicket() {
        val icon = PassIconLibrary.suggestFor(
            "Deutsche Bahn",
            "Fahrkarte ICE 599",
            "Wagen 12, Platz 41",
        )

        assertEquals("train", icon?.key)
    }

    @Test
    fun suggestsFlightSymbolForBoardingPass() {
        val icon = PassIconLibrary.suggestFor("Lufthansa", "Boarding Pass", "Gate A12")

        assertEquals("flight", icon?.key)
    }

    @Test
    fun suggestsLoyaltyCardSymbol() {
        val icon = PassIconLibrary.suggestFor("Payback", "Punkte sammeln")

        assertEquals("loyalty", icon?.key)
    }

    @Test
    fun ignoresPartialMatchesInOtherWords() {
        // "Bar" is contained in "Barcelona" - without a word boundary a travel ticket would get
        // a cocktail glass icon.
        val icon = PassIconLibrary.suggestFor("Flug nach Barcelona", "Barcelona El Prat")

        assertEquals("flight", icon?.key)
    }

    @Test
    fun returnsNoSuggestionWithoutAClue() {
        assertNull(PassIconLibrary.suggestFor("XZQ-2231", "   ", null))
    }

    @Test
    fun prefersTheLongerKeyword() {
        // "bahn" (4) beats "bus" (3) when both occur - the train reference is more specific.
        val icon = PassIconLibrary.suggestFor("Bahn und Bus Kombiticket")

        assertEquals("train", icon?.key)
    }

    @Test
    fun searchFindsByKeywords() {
        val results = PassIconLibrary.search("kaffee")

        assertEquals("cafe", results.firstOrNull()?.key)
    }

    @Test
    fun searchPutsNameMatchesFirst() {
        val results = PassIconLibrary.search("bus")

        assertEquals("bus", results.firstOrNull()?.key)
    }

    @Test
    fun emptySearchReturnsAllSymbols() {
        assertEquals(PassIconLibrary.all.size, PassIconLibrary.search("   ").size)
    }

    @Test
    fun keysAreUniqueAndResolvable() {
        val keys = PassIconLibrary.all.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { PassIconLibrary.byKey(it) != null })
        assertNull(PassIconLibrary.byKey("gibt-es-nicht"))
        assertNull(PassIconLibrary.byKey(null))
    }
}


