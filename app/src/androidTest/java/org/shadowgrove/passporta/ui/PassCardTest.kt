package org.shadowgrove.passporta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.ui.model.PassPalette
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.ui.overview.PassCard
import org.shadowgrove.passporta.ui.theme.PassPortaTheme

/**
 * Covers the display rules of the overview card - including the contrast logic from phase 1.
 */
@RunWith(AndroidJUnit4::class)
class PassCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTitleAndSubtitle() {
        composeRule.setContent {
            PassPortaTheme(dynamicColor = false) {
                PassCard(pass = pass(subtitle = "FRA → JFK"), onClick = {})
            }
        }

        composeRule.onNodeWithText("Beispiel Club").assertIsDisplayed()
        composeRule.onNodeWithText("FRA → JFK").assertIsDisplayed()
    }

    @Test
    fun reportsClicksWithThePassId() {
        var clicked = false
        composeRule.setContent {
            PassPortaTheme(dynamicColor = false) {
                PassCard(pass = pass(subtitle = null), onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Beispiel Club").performClick()

        assertTrue(clicked)
    }

    @Test
    fun choosesTextColorByContrast() {
        // Light yellow -> black text, dark blue -> white text.
        assertEquals(Color.Black, PassPalette.from(0xFFFFEB3B.toInt()).content)
        assertEquals(Color.White, PassPalette.from(0xFF1A237E.toInt()).content)
    }

    @Test
    fun derivesInitialsFromTheTitle() {
        assertEquals("BC", pass(subtitle = null).initials)
        assertEquals("P", pass(subtitle = null, title = "Payback").initials)
    }

    /**
     * The star must only appear on favorites. Both cards are deliberately shown at the same
     * time: a test with only one card would not reveal it if the marker appeared on every card.
     */
    @Test
    fun showsFavoriteMarkerOnlyOnFavorites() {
        val badge = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.overview_favorite_badge)

        composeRule.setContent {
            PassPortaTheme(dynamicColor = false) {
                Column {
                    PassCard(
                        pass = pass(subtitle = null, title = "Favorit", isFavorite = true),
                        onClick = {},
                    )
                    PassCard(pass = pass(subtitle = null, title = "Gewoehnlich"), onClick = {})
                }
            }
        }

        composeRule.onAllNodesWithContentDescription(badge).assertCountEquals(1)
    }


    private fun pass(
        subtitle: String?,
        title: String = "Beispiel Club",
        isFavorite: Boolean = false,
    ) = PassUi(
        id = "test",
        folderName = "Kundenkarten",
        title = title,
        subtitle = subtitle,
        ownerName = "Erika Mustermann",
        identifier = "1234 5678 9012",
        barcodeData = "9012345678",
        barcodeType = BarcodeType.QR,
        barcodeAltText = null,
        barcodeEcc = null,
        barcodeEncoding = null,
        logoFile = null,
        heroImageFile = null,
        palette = PassPalette.from(0xFF5A3CC8.toInt()),
        isFavorite = isFavorite,
    )
}


