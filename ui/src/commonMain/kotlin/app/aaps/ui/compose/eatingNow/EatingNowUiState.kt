package app.aaps.ui.compose.eatingNow

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit

/**
 * UI state for the Eating Now entry (opens the Eating Now window).
 *
 * The user sets an Eating Now temp target (optionally with an up-front prebolus). All values are held
 * in user units for display; the view model converts the target to mg/dL when it writes the batch.
 */
@Immutable
data class EatingNowUiState(
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    // Target in user units.
    val target: Double = 0.0,
    // Duration in minutes.
    val durationMinutes: Int = 0,
    // ENW-IOB limit (units of insulin) and its upper bound (maxIobSMB).
    val enwIob: Double = 0.0,
    val maxIob: Double = 0.0,
    // Prebolus toggle and amount (units of insulin). The amount only applies when the toggle is on.
    val prebolusChecked: Boolean = false,
    val prebolus: Double = 0.0,
    val maxPrebolus: Double = 0.0,
    val bolusStep: Double = 0.1,
    // True when a temp target is already running: the entry then offers to cancel it and hides the
    // prebolus controls (there is nothing to prebolus for when cancelling).
    val hasActiveTT: Boolean = false,
    val isLoading: Boolean = true
)
