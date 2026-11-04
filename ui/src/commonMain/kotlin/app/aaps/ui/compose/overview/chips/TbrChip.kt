package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcNoTbr
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * @see TbrChipHighPreview
 * @see TbrChipLowPreview
 * @see TbrChipNonePreview
 */
@Composable
fun TbrChip(
    state: TbrState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "",
    progress: Float = 0f
) {
    val iconColor = AapsTheme.elementColors.tempBasal
    val containerColor = iconColor.copy(alpha = 0.2f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = LocalHapticFeedback.current

    // Whether a temp basal is running, and which way it goes, is still carried by the icon for a11y
    // (the text is a visual convenience). The state description keeps "above / below / none profile".
    val tbrState = stringResource(state.toDescription())
    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = containerColor,
        modifier = modifier
            .height(AapsSpacing.chipHeight * LocalAapsScale.current)
            .semantics { stateDescription = tbrState }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                // Progress bar along the bottom edge, like the TT chip: how much of a running temp
                // basal's duration has elapsed. Drawn here (not a fillMaxWidth child) so it spans the
                // chip's own content width and never forces the chip wider. No bar when progress is 0.
                .then(
                    if (progress > 0f) Modifier.drawBehind {
                        val stroke = AapsSpacing.chipProgressHeight.toPx()
                        val y = size.height - stroke / 2f
                        drawLine(iconColor.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), stroke)
                        drawLine(iconColor, Offset(0f, y), Offset(size.width * progress, y), stroke)
                    } else Modifier
                )
                .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
        ) {
            Icon(
                imageVector = state.toIcon(),
                contentDescription = stringResourceOrNull(ElementType.TEMP_BASAL.label()),
                tint = iconColor,
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
            // Icon then text: the current effective basal rate, e.g. "0.90 U/h". Empty text falls
            // back to the icon-only chip.
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
        }
    }
}

/**
 * What the chip's icon means in words. Kept beside [toIcon] so the two cannot drift, and exhaustive
 * with no `else` so a new [TbrState] fails to compile here rather than silently going unspoken.
 */
private fun TbrState.toDescription(): TextRef = when (this) {
    TbrState.HIGH -> CoreUiStrings.tbr_state_above_profile
    TbrState.LOW  -> CoreUiStrings.tbr_state_below_profile
    TbrState.NONE -> CoreUiStrings.tbr_state_none
}

internal fun TbrState.toIcon(): ImageVector = when (this) {
    TbrState.HIGH -> IcTbrHigh
    TbrState.LOW  -> IcTbrLow
    TbrState.NONE -> IcNoTbr
}
