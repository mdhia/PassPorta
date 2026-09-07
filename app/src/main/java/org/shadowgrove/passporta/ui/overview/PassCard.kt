package org.shadowgrove.passporta.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.ui.components.PassLogo
import org.shadowgrove.passporta.ui.components.PassLogoSizeOnCard
import org.shadowgrove.passporta.ui.model.PassPalette
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.ui.theme.PassPortaTheme

/**
 * Height of an overview card.
 *
 * Fixed height instead of aspect ratio: with a row of logo and text, the logo size determines
 * the required height, not the column width. With an aspect ratio, the cards would become
 * unnecessarily tall on wide displays.
 */
private val CardHeight = 76.dp

/** Small star at the right edge - just a hint, not a control. */
private val FavoriteMarkerSize = 14.dp

/**
 * Tile of a pass in the overview.
 *
 * Compact row: logo on the left, vertically centered, next to it title and - if present -
 * subtitle. The background color comes from the pass, the text color from the contrast logic.
 */
@Composable
fun PassCard(
    pass: PassUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = pass.palette.background),
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PassLogo(pass = pass, size = PassLogoSizeOnCard)

            // The text block is centered as a whole. Without a subtitle, the title automatically
            // sits at logo height - no special case needed for that.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = pass.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pass.palette.content,
                    maxLines = if (pass.hasSubtitle) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pass.subtitle != null) {
                    Text(
                        text = pass.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = pass.palette.secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // The star sits outside the text block, so it doesn't wrap with the title. It
            // deliberately gets no click target of its own: on a 76 dp tall card that would be
            // under the minimum touch target size - toggling happens in the detail view.
            if (pass.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.overview_favorite_badge),
                    tint = pass.palette.secondaryContent,
                    modifier = Modifier
                        .align(Alignment.Top)
                        .padding(top = 10.dp)
                        .size(FavoriteMarkerSize),
                )
            }
        }
    }
}

@Preview(name = "Card with subtitle", widthDp = 180)
@Composable
private fun PassCardWithSubtitlePreview() {
    PassPortaTheme(dynamicColor = false) {
        PassCard(pass = previewPass(subtitle = "FRA → JFK"), onClick = {})
    }
}

@Preview(name = "Card without subtitle", widthDp = 180)
@Composable
private fun PassCardWithoutSubtitlePreview() {
    PassPortaTheme(dynamicColor = false) {
        PassCard(pass = previewPass(subtitle = null), onClick = {})
    }
}

@Preview(name = "Card on light background", widthDp = 180)
@Composable
private fun PassCardLightBackgroundPreview() {
    PassPortaTheme(dynamicColor = false) {
        PassCard(
            pass = previewPass(subtitle = "Gold-Status", backgroundColor = 0xFFFFEB3B.toInt()),
            onClick = {},
        )
    }
}

@Preview(name = "Card as favorite", widthDp = 180)
@Composable
private fun PassCardFavoritePreview() {
    PassPortaTheme(dynamicColor = false) {
        PassCard(pass = previewPass(subtitle = "Gold-Status", isFavorite = true), onClick = {})
    }
}


private fun previewPass(
    subtitle: String?,
    backgroundColor: Int = 0xFF5A3CC8.toInt(),
    isFavorite: Boolean = false,
) = PassUi(
    id = "preview",
    folderName = "Kundenkarten",
    title = "Beispiel Club",
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
    palette = PassPalette.from(backgroundColor),
    isFavorite = isFavorite,
)

