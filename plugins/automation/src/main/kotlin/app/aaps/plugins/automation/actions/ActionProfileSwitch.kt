package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputProfileName
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitch(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger

    var inputProfileName: InputProfileName = InputProfileName(rh, activePlugin, "")

    override fun friendlyName(): Int = R.string.profilename
    override fun shortDescription(): String = rh.gs(R.string.changengetoprofilename, inputProfileName.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        val activeProfileName = profileFunction.getProfileName()
        //Check for uninitialized profileName
        if (inputProfileName.value == "") {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile not initialized")
            callback.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.validators.R.string.error_field_must_not_be_empty)).run()
            return
        }
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            callback.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.noprofile)).run()
            return
        }
        if (inputProfileName.value == activeProfileName) {
            aapsLogger.debug(LTag.AUTOMATION, "Profile is already switched")
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadyset)).run()
            return
        }
        val profileStore = activePlugin.activeProfileSource.profile ?: return
        if (profileStore.getSpecificProfile(inputProfileName.value) == null) {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile does not exist! - ${inputProfileName.value}")
            callback.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.notexists)).run()
            return
        }
        uel.log(
            UserEntry.Action.PROFILE_SWITCH, Sources.Automation, title,
            ValueWithUnit.SimpleString(inputProfileName.value),
            ValueWithUnit.Percent(100)
        )
        val result = profileFunction.createProfileSwitch(profileStore, inputProfileName.value, 0, 100, 0, dateUtil.now())
        callback.result(PumpEnactResult(injector).success(result).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.profilename), "", inputProfileName))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("profileToSwitchTo", inputProfileName.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        inputProfileName.value = JsonHelper.safeGetString(o, "profileToSwitchTo", "")
        return this
    }

    override fun isValid(): Boolean = activePlugin.activeProfileSource.profile?.getSpecificProfile(inputProfileName.value) != null
}