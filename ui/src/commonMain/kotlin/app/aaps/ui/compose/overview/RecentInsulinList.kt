package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.stringResource

/** How many of the most recent boluses the list shows. A fixed, glanceable number. */
private const val MAX_RECENT_INSULIN_ROWS = 5

/**
 * A small list of the most recent insulin deliveries (SMB and manual boluses) shown under the charts.
 *
 * It exists so the recent doses can be read at a glance — when assessing a low, for example — without
 * press-and-hold on the chart. The [boluses] come from the treatment data already loaded for the
 * graph, so this adds no query. SMB and manual doses share the insulin color; the type word tells
 * them apart. Renders nothing when there are no recent boluses.
 */
@Composable
fun RecentInsulinList(
    boluses: List<BolusGraphPoint>,
    modifier: Modifier = Modifier
) {
    val recent = boluses
        .filter { it.isValid }
        .sortedByDescending { it.timestamp }
        .take(MAX_RECENT_INSULIN_ROWS)
    if (recent.isEmpty()) return

    val dateUtil = LocalDateUtil.current
    val insulinColor = AapsTheme.elementColors.insulin

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(CoreUiStrings.recent_insulin),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        recent.forEach { bolus ->
            val typeLabel = if (bolus.bolusType == BolusType.SMB) {
                stringResource(CoreUiStrings.smb_shortname)
            } else {
                stringResource(InterfacesStrings.bolus)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type then amount read as one unit ("SMB 0.50 U"); the time sits on the right.
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = insulinColor,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = bolus.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = insulinColor,
                    modifier = Modifier.weight(1f)
                )
                // Signed minutes from now (negative = in the past), shown compact as e.g. "-8m".
                val minutesAgo = ((bolus.timestamp - dateUtil.now()) / 60_000L).toInt()
                Text(
                    text = stringResource(CoreUiStrings.format_mins_compact, minutesAgo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
