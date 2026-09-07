package org.shadowgrove.passporta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.ui.model.PassUi

/**
 * Round logo crop of a pass.
 *
 * Order: stored logo, otherwise the chosen symbol from the icon library, otherwise the title's
 * initials. This keeps cards distinguishable even for passes from wallet links (which only
 * contain logo URLs).
 */
@Composable
fun PassLogo(
    pass: PassUi,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(pass.palette.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val logoFile = pass.logoFile
        val icon = pass.icon
        when {
            logoFile != null -> AsyncImage(
                model = logoFile,
                contentDescription = stringResource(R.string.detail_logo, pass.title),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * LOGO_INSET_FRACTION),
            )

            icon != null -> Icon(
                imageVector = icon.image,
                contentDescription = stringResource(R.string.detail_logo, pass.title),
                tint = pass.palette.content,
                modifier = Modifier.size(size * ICON_SIZE_FRACTION),
            )

            else -> Text(
                text = pass.initials,
                color = pass.palette.content,
                fontWeight = FontWeight.SemiBold,
                fontSize = size.toInitialsFontSize(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Margin of the logo within the circle - prevents square logos from looking cropped. */
private const val LOGO_INSET_FRACTION = 0.16f

/** Fraction of the circle that a symbol occupies. */
private const val ICON_SIZE_FRACTION = 0.56f

/** Font size of the initials proportional to the circle. */
private fun Dp.toInitialsFontSize(): TextUnit = (value * 0.38f).sp

/** Base size of the logo on an overview card. */
val PassLogoSizeOnCard: Dp = 40.dp

/** Base size of the logo in the detail view. */
val PassLogoSizeOnDetail: Dp = 96.dp
