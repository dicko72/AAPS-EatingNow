// Modified for Eating Now
package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcNoTbr

/**
 * Cancels the current temporary basal from automation (Eating Now).
 * Ported from 3.4 to 4.0: constructor DI, suspend doAction, and the suspend commandQueue API.
 */
class ActionBasalCancel(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val commandQueue: CommandQueue,
    private val uiInteraction: UiInteraction
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    override fun friendlyName(): TextRef = CoreUiStrings.cancel_temp
    override fun shortDescription(): String = rh.gs(CoreUiStrings.cancel_temp)
    override fun composeIcon() = IcNoTbr
    override fun elementType() = ElementType.TEMP_BASAL

    override suspend fun doAction(): PumpEnactResult {
        val result = commandQueue.cancelTempBasal(enforceNew = true)
        if (!result.success)
            uiInteraction.runAlarm(result.comment, rh.gs(CoreUiStrings.temp_basal_delivery_error), AlarmSound.BOLUS_ERROR)
        return result
    }

    override fun isValid(): Boolean = true
}
