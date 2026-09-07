package org.shadowgrove.passporta.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.ui.model.PassFieldUi
import org.shadowgrove.passporta.ui.model.PassPalette
import org.shadowgrove.passporta.util.TextLinks

/**
 * Additional fields of a pass as a coherent list.
 *
 * Layout per row: label small and left-aligned on top, the value below it. The rows sit in a
 * shared surface and are separated by thin lines - this keeps the relation between label and
 * value unambiguous even when values span multiple lines.
 *
 * If a value contains an address, email or phone number, exactly that section is tappable and
 * hands off to the responsible app.
 */
@Composable
fun PassFieldList(
    fields: List<PassFieldUi>,
    palette: PassPalette,
    modifier: Modifier = Modifier,
) {
    if (fields.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceVariant),
    ) {
        fields.forEachIndexed { index, field ->
            if (index > 0) {
                HorizontalDivider(
                    color = palette.secondaryContent.copy(alpha = DIVIDER_ALPHA),
                    thickness = 1.dp,
                )
            }
            PassFieldRow(field = field, palette = palette)
        }
    }
}

@Composable
private fun PassFieldRow(
    field: PassFieldUi,
    palette: PassPalette,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Long press copies the value - the label serves as the entry's name.
            .copyOnLongPress(value = field.value, label = field.label)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!field.label.isNullOrBlank()) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryContent,
            )
        }
        Text(
            text = linkedValue(value = field.value, label = field.label),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = palette.content,
            modifier = Modifier.padding(top = if (field.label.isNullOrBlank()) 0.dp else 2.dp),
        )
    }
}

/**
 * Builds the field value with clickable sections.
 *
 * Links deliberately get no color of their own, but an underline: the background is the freely
 * chosen pass color, on which a fixed link blue would be unreadable depending on the card. An
 * underline, by contrast, carries on any background and adopts the text's contrast color.
 */
@Composable
private fun linkedValue(value: String, label: String?): AnnotatedString {
    val context = LocalContext.current

    return remember(value, label, context) {
        val links = TextLinks.find(value, label)
        if (links.isEmpty()) return@remember AnnotatedString(value)

        val styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

        buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                append(value.substring(cursor, link.range.first))
                withLink(
                    LinkAnnotation.Clickable(tag = link.uri, styles = styles) { annotation ->
                        val target = (annotation as? LinkAnnotation.Clickable)?.tag
                        if (target != null) openLink(context, target)
                    },
                ) {
                    append(value.substring(link.range))
                }
                cursor = link.range.last + 1
            }
            append(value.substring(cursor))
        }
    }
}

/**
 * Hands off an address to the responsible app.
 *
 * For phone numbers, [Intent.ACTION_DIAL] is deliberately used, not `ACTION_CALL`: the number
 * only lands in the dial field, dialing only happens on a key press - and PassPorta needs no
 * permission for that. A check with `resolveActivity` would be worthless here, because from
 * Android 11 onward it reports nothing without a `<queries>` entry; the failure is caught
 * instead.
 */
private fun openLink(context: Context, uri: String) {
    val target = uri.toUri()
    val action = when (target.scheme?.lowercase()) {
        "tel" -> Intent.ACTION_DIAL
        "mailto" -> Intent.ACTION_SENDTO
        else -> Intent.ACTION_VIEW
    }

    try {
        context.startActivity(Intent(action, target))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.detail_no_app_for_link, Toast.LENGTH_SHORT).show()
    }
}

/** Subtle divider: clearly visible, but without cutting through the pass color. */
private const val DIVIDER_ALPHA = 0.25f
