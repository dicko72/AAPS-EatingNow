package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.ValueWrapper
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.plugins.R
import info.nightscout.plugins.pump.virtual.VirtualPumpPlugin
import javax.inject.Inject

class Objective0(injector: HasAndroidInjector) : Objective(injector, "config", R.string.objectives_0_objective, R.string.objectives_0_gate) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator

    init {
        tasks.add(object : Task(this, R.string.objectives_bgavailableinns) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectives_bg_is_available_in_ns, false)
            }
        })
        tasks.add(object : Task(this, R.string.synchaswritepermission) {
            override fun isCompleted(): Boolean {
                return activePlugin.firstActiveSync?.hasWritePermission == true
            }
        })
        tasks.add(object : Task(this, R.string.virtualpump_uploadstatus_title) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_virtual_pump_upload_status, false)
            }

            override fun shouldBeIgnored(): Boolean {
                return !virtualPumpPlugin.isEnabled()
            }
        })
        tasks.add(object : Task(this, R.string.objectives_pumpstatusavailableinns) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectives_pump_status_is_available_in_ns, false)
            }
        })
        tasks.add(object : Task(this, R.string.hasbgdata) {
            override fun isCompleted(): Boolean {
                return iobCobCalculator.ads.lastBg() != null
            }
        })
        tasks.add(object : Task(this, R.string.loopenabled) {
            override fun isCompleted(): Boolean {
                return (loop as PluginBase).isEnabled()
            }
        })
        tasks.add(object : Task(this, R.string.apsselected) {
            override fun isCompleted(): Boolean {
                val usedAPS = activePlugin.activeAPS
                return (usedAPS as PluginBase).isEnabled()
            }
        })
        tasks.add(object : Task(this, R.string.activate_profile) {
            override fun isCompleted(): Boolean {
                return repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing
            }
        })
    }
}