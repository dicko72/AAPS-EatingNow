// Modified for Eating Now
package app.aaps.core.interfaces.aps

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ENConfig(
    //General
    var EatingNowTimeStart: Int,
    var EatingNowTimeEnd: Int,
    var OvernightSMBRestrict: Double,
    var RespectISFIOB: Boolean,

    // ENW Breakfast
    var Eatingnow_bkfast_enw_minutes: Int,
    var Eatingnow_bkfast_enw_pct: Int,
    var Eatingnow_bkfast_enw_cob_maxbolus: Double,
    var Eatingnow_bkfast_enw_uam_maxbolus: Double,
    var Eatingnow_bkfast_enw_maxiob: Double,
    var Eatingnow_bkfast_enw_prebolus: Double,

    // ENW other Meals
    var Eatingnow_enw_minutes: Int,
    var Eatingnow_enw_pct: Int,
    var Eatingnow_enw_cob_maxbolus: Double,
    var Eatingnow_enw_uam_maxbolus: Double,
    var Eatingnow_enw_maxiob: Double,
    var Eatingnow_enw_prebolus: Double,
    var Eatingnow_enw_smb_pct: Int,
    var Eatingnow_enw_uamplus_maxbolus: Double,

    // Outside ENW
    // Outside ENW maxBolus
    // SMB Percentage outside ENW

    // ENW Advanced
    // ENW Min Bolus
    // Use GhostCOB always
    // Use GhostCOB after ENW
    // Min COB Limit
    // SMB Percentage within ENW (moved to ENW other meals)

    // UAM+
    // UAM+ Breakfast maxBolus
    // UAM+ maxBolus (moved to ENW other meals)

    // UAM+ Advanced Settings
    // Allow UAM+ SMB outside ENW
    // Deliver ENW insulin faster

    // ISF Settings
    // Use dynamic ISF - No longer required?
    // ISF BG Scaler - No longer required?
    // Use TDD for ISF - No longer required?
    // TDD ISF Scaling - No longer required?
    // Use TDD based Sensitivity Ratio - No longer required?

    // Sensitivity & Resistance Settings
    // TIRS% resistance per hour
    // Enable BG+
    // Postprandial ISF Duration

    // Experimental Variant
    // Use LCTDD for ISF - No longer required?
)