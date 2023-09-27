package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputProfileName
import app.aaps.plugins.automation.elements.InputWeekDay
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionRunAutotune(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var autotunePlugin: Autotune
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var sp: SP

    private var defaultValue = 0
    private var inputProfileName = InputProfileName(rh, activePlugin, "", true)
    private var daysBack = InputDuration(0, InputDuration.TimeUnit.DAYS)
    private val days = InputWeekDay().also { it.setAll(true) }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.autotune_run
    override fun shortDescription(): String = resourceHelper.gs(app.aaps.core.ui.R.string.autotune_profile_name, inputProfileName.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        val autoSwitch = sp.getBoolean(app.aaps.core.utils.R.string.key_autotune_auto, false)
        val profileName = if (inputProfileName.value == rh.gs(app.aaps.core.ui.R.string.active)) "" else inputProfileName.value
        var message = if (autoSwitch) app.aaps.core.ui.R.string.autotune_run_with_autoswitch else app.aaps.core.ui.R.string.autotune_run_without_autoswitch
        Thread {
            if (!autotunePlugin.calculationRunning) {
                autotunePlugin.atLog("[Automation] Run Autotune $profileName, ${daysBack.value} days, Autoswitch $autoSwitch")
                autotunePlugin.aapsAutotune(daysBack.value, autoSwitch, profileName, days.weekdays)
                if (!autotunePlugin.lastRunSuccess) {
                    message = app.aaps.core.ui.R.string.autotune_run_with_error
                    aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
                }
                callback.result(PumpEnactResult(injector).success(autotunePlugin.lastRunSuccess).comment(message)).run()
            } else {
                message = app.aaps.core.ui.R.string.autotune_run_cancelled
                aapsLogger.debug(LTag.AUTOMATION, "Autotune run detected, Autotune Run Cancelled")
                callback.result(PumpEnactResult(injector).success(false).comment(message)).run()
            }
        }.start()
        return
    }

    override fun generateDialog(root: LinearLayout) {
        if (defaultValue == 0)
            defaultValue = sp.getInt(app.aaps.core.utils.R.string.key_autotune_default_tune_days, 5)
        daysBack.value = defaultValue
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.autotune_select_profile), "", inputProfileName))
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.autotune_tune_days), "", daysBack))
            .add(days)
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject()
            .put("profileToTune", inputProfileName.value)
            .put("tunedays", daysBack.value)
        for (i in days.weekdays.indices) {
            data.put(WeekDay.DayOfWeek.values()[i].name, days.weekdays[i])
        }
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        for (i in days.weekdays.indices)
            days.weekdays[i] = JsonHelper.safeGetBoolean(o, WeekDay.DayOfWeek.values()[i].name, true)
        inputProfileName.value = JsonHelper.safeGetString(o, "profileToTune", "")
        defaultValue = JsonHelper.safeGetInt(o, "tunedays")
        if (defaultValue == 0)
            defaultValue = sp.getInt(app.aaps.core.utils.R.string.key_autotune_default_tune_days, 5)
        daysBack.value = defaultValue
        return this
    }

    override fun isValid(): Boolean = profileFunction.getProfile() != null && activePlugin.getSpecificPluginsListByInterface(Autotune::class.java).first().isEnabled()
}