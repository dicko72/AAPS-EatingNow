// Modified for Eating Now
package app.aaps.core.interfaces.aps

import app.aaps.core.data.model.TT
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ENConfig(
    //General
    var ENTimeStart: Long,
    var ENTimeEnd: Long,
    var ENActive: Boolean,
    var OvernightSMBRestrict: Double,
    var RespectISFIOB: Boolean,

    // ENW variables
    var ENWfirstMeal: Boolean,
    var ENWActive: TT.Reason?,
    var ENWStartTime: Long?,
    var ENWEndTime: Long?,
    var ENW_minutes: Int,
    var ENW_pct: Int,
    var ENW_cob_maxbolus: Double,
    var ENW_uam_maxbolus: Double,
    var ENWNetIOB: Double,
    var ENWNetIOBMax: Double,
    var ENW_prebolus: Double,
    var ENW_smb_pct: Int,
    var ENW_uamplus_maxbolus: Double,

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