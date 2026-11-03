/*
 * Adaptive Double Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.keys.decimalPlaces
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.step
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.isDuration
import app.aaps.core.ui.compose.rangeText
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.core.ui.compose.unitLabel
import app.aaps.core.ui.compose.valueFormat
import kotlin.math.roundToInt

/**
 * Composable double preference for use inside card sections.
 *
 * @param title Optional title override. If null, uses doubleKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 *
 * @see AdaptiveDoublePreferencePreview
 */
@Composable
fun AdaptiveDoublePreferenceItem(
    doubleKey: DoublePreferenceKey,
    title: TextRef? = null,
    unit: String = "",
    visibilityContext: VisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val effectiveTitle = title ?: doubleKey.title

    val visibility = calculatePreferenceVisibility(
        preferenceKey = doubleKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible || (preferences.simpleMode && doubleKey.calculatedBySM)) return

    val state = rememberPreferenceDoubleState(doubleKey)
    val value = state.value
    val theme = LocalPreferenceTheme.current

    // Glucose delta: stored in mg/dl, shown and edited in the profile's current units.
    // Conversion uses the known units (not the magnitude auto-detect), so it is safe for the
    // small near-zero values these deltas hold. See [DoublePreferenceKey.glucoseDelta].
    if (doubleKey.glucoseDelta) {
        GlucoseDeltaPreference(
            doubleKey = doubleKey,
            effectiveTitle = effectiveTitle,
            state = state,
            enabled = visibility.enabled,
            theme = theme
        )
        return
    }

    // Get formatting info from UnitType
    val unitType = doubleKey.unitType
    val decimalPlaces = unitType.decimalPlaces()
    val step = unitType.step()
    val valueFormatRef = unitType.valueFormat()

    // Get unit label from UnitType (for dialog input suffix)
    val unitLabelRef = unitType.unitLabel() ?: unit.takeIf { it.isNotEmpty() }?.let { TextRef.Literal(it) }
    val unitLabelText = unitLabelRef?.let { stringResource(it) } ?: ""

    val valueFormat = NumberFormat.withDecimals(decimalPlaces)

    // Get summary if available
    val summary = stringResourceOrNull(doubleKey.summary)

    // Use slider if min/max range is specified (not default extreme values)
    // Note: Double.MIN_VALUE is smallest positive value, not most negative
    val hasValidRange = doubleKey.min != -Double.MAX_VALUE && doubleKey.max != Double.MAX_VALUE

    if (hasValidRange) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.padding)
        ) {
            TextWithSyncBadge(
                text = stringResource(effectiveTitle),
                key = doubleKey,
                style = theme.titleTextStyle,
                // Mirror Preference's disabled styling (the switch row greys the same way) since this
                // slider branch builds its own row instead of going through Preference.
                color = theme.titleColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = theme.summaryTextStyle,
                    color = theme.summaryColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
                )
            }
            PreferenceSliderWithButtons(
                value = value,
                onValueChange = { newValue ->
                    if (visibility.enabled) {
                        state.value = newValue
                    }
                },
                valueRange = doubleKey.min..doubleKey.max,
                step = step,
                showValue = true,
                valueFormatRef = valueFormatRef,
                valueFormat = valueFormat,
                unitLabel = unitLabelRef,
                asDuration = unitType.isDuration(),
                dialogLabel = stringResource(effectiveTitle),
                dialogSummary = summary,
                enabled = visibility.enabled
            )
        }
    } else {
        // For unspecified ranges, use text field with range summary
        val rangeRef = unitType.rangeText(value, doubleKey.min, doubleKey.max)
        val summaryText = if (rangeRef != null) {
            stringResource(rangeRef)
        } else {
            stringResource(CoreUiStrings.preference_range_summary, valueFormat.format(value), unitLabelText, valueFormat.format(doubleKey.min), valueFormat.format(doubleKey.max))
        }
        TextFieldPreference(
            state = state,
            title = { PreferenceTitleWithSyncBadge(effectiveTitle, doubleKey) },
            textToValue = { text ->
                text.toDoubleOrNull()?.coerceIn(doubleKey.min, doubleKey.max)
            },
            enabled = visibility.enabled,
            summary = { Text(summaryText) }
        )
    }
}

/**
 * Slider row for a [DoublePreferenceKey.glucoseDelta] value.
 *
 * The value is stored in mg/dl. Here it is shown and edited in the profile's current units, with
 * the step and decimals that suit those units (mmol/L: 0.1 and one decimal; mg/dl: 1 and integer).
 * Conversion uses the known [units] in both directions, so the small near-zero values these deltas
 * hold are never run through the magnitude auto-detect that `UnitDoublePreferenceKey` uses. This
 * mirrors how the temp-target editor handles a target.
 */
@Composable
private fun GlucoseDeltaPreference(
    doubleKey: DoublePreferenceKey,
    effectiveTitle: TextRef,
    state: MutableState<Double>,
    enabled: Boolean,
    theme: PreferenceTheme
) {
    val profileUtil = LocalProfileUtil.current
    val units = profileUtil.units
    val isMgdl = units == GlucoseUnit.MGDL

    val step = if (isMgdl) 1.0 else 0.1
    val valueFormat = if (isMgdl) NumberFormat.INTEGER else NumberFormat.DECIMAL_1

    // min/max are stored in mg/dl; show the range in the current units.
    val displayMin = profileUtil.fromMgdlToUnits(doubleKey.min, units)
    val displayMax = profileUtil.fromMgdlToUnits(doubleKey.max, units)

    // Round the stored mg/dl to what the user can actually set, so the display round trip is stable.
    val displayRaw = profileUtil.fromMgdlToUnits(state.value, units)
    val displayValue = if (isMgdl) displayRaw.roundToInt().toDouble() else (displayRaw * 10).roundToInt() / 10.0

    val summary = stringResourceOrNull(doubleKey.summary)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(theme.padding)
    ) {
        TextWithSyncBadge(
            text = stringResource(effectiveTitle),
            key = doubleKey,
            style = theme.titleTextStyle,
            color = theme.titleColor.let { if (enabled) it else it.copy(alpha = theme.disabledOpacity) }
        )
        if (summary != null) {
            Text(
                text = summary,
                style = theme.summaryTextStyle,
                color = theme.summaryColor.let { if (enabled) it else it.copy(alpha = theme.disabledOpacity) }
            )
        }
        PreferenceSliderWithButtons(
            value = displayValue,
            onValueChange = { newValue ->
                if (enabled) state.value = profileUtil.convertToMgdl(newValue, units)
            },
            valueRange = displayMin..displayMax,
            step = step,
            showValue = true,
            valueFormat = valueFormat,
            unitLabel = TextRef.Literal(units.displayLabel),
            dialogLabel = stringResource(effectiveTitle),
            dialogSummary = summary,
            enabled = enabled
        )
    }
}
