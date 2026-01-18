// Modified for Eating Now
package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ActionBasalCancel(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var uiInteraction: UiInteraction

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.cancel_temp
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.cancel_temp)
    override fun icon(): Int = app.aaps.core.objects.R.drawable.ic_cp_basal_no_tbr

    override fun doAction(callback: Callback) {
        commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error), app.aaps.core.ui.R.raw.boluserror)
                }
            }
        })
    }
    override fun isValid(): Boolean = true
}