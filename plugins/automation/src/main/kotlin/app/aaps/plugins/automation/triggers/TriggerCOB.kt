package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerCOB(injector: HasAndroidInjector) : Trigger(injector) {

    private val minValue = 0
    private val maxValue = sp.getInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, 48)
    var cob: InputDouble = InputDouble(0.0, minValue.toDouble(), maxValue.toDouble(), 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerCOB: TriggerCOB) : this(injector) {
        cob = InputDouble(triggerCOB.cob)
        comparator = Comparator(rh, triggerCOB.comparator.value)
    }

    fun setValue(value: Double): TriggerCOB {
        cob.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerCOB {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val cobInfo = iobCobCalculator.getCobInfo("AutomationTriggerCOB")
        if (cobInfo.displayCob == null) {
            return if (comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        }
        if (comparator.value.check(cobInfo.displayCob!!, cob.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("carbs", cob.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        cob.setValue(safeGetDouble(d, "carbs"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggercoblabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.cobcompared, rh.gs(comparator.value.stringRes), cob.value)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.main.R.drawable.ic_cp_bolus_carbs)

    override fun duplicate(): Trigger = TriggerCOB(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggercoblabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.triggercoblabel) + ": ", "", cob))
            .build(root)
    }
}