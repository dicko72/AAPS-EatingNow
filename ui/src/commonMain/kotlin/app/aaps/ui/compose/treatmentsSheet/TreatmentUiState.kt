package app.aaps.ui.compose.treatmentsSheet

import androidx.compose.runtime.Immutable
import app.aaps.ui.compose.main.QuickWizardItem

/**
 * UI state for the Treatment bottom sheet
 */
@Immutable
data class TreatmentUiState(
    // Visibility states (computed from preferences)
    val showCgm: Boolean = false,
    val showCalibration: Boolean = false,
    val showTreatment: Boolean = true,
    val showInsulin: Boolean = true,
    val showCarbs: Boolean = true,
    val showCalculator: Boolean = true,
    // Eating Now entry — shown only when the Eating Now plugin is the active APS.
    val showEatingNow: Boolean = false,

    // Source info
    val isDexcomSource: Boolean = false,

    // QuickWizard items
    val quickWizardItems: List<QuickWizardItem> = emptyList(),

    // Settings visibility
    val showSettingsIcon: Boolean = false
)
