package app.aaps.plugins.automation.actions

import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ActionLoopDisable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.disableloop
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.disableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if (loopPlugin.isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, false)
            configBuilder.storeSettings("ActionLoopDisable")
            uel.log(UserEntry.Action.LOOP_DISABLED, Sources.Automation, title)
            commandQueue.cancelTempBasal(true, object : Callback() {
                override fun run() {
                    rxBus.send(EventRefreshOverview("ActionLoopDisable"))
                    callback.result(result).run()
                }
            })
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadydisabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}
