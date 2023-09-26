package app.aaps.implementation.pump

import android.widget.TextView
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.isOlderThan
import app.aaps.database.entities.TherapyEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class WarnColorsImpl @Inject constructor(
    private val rh: ResourceHelper, private val dateUtil: DateUtil
) : WarnColors {

    override fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    value >= urgentLevel -> app.aaps.core.ui.R.attr.urgentColor
                    value >= warnLevel   -> app.aaps.core.ui.R.attr.warnColor
                    else                 -> app.aaps.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    value <= urgentLevel -> app.aaps.core.ui.R.attr.urgentColor
                    value <= warnLevel   -> app.aaps.core.ui.R.attr.warnColor
                    else                 -> app.aaps.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    therapyEvent.isOlderThan(urgentThreshold, dateUtil) -> app.aaps.core.ui.R.attr.lowColor
                    therapyEvent.isOlderThan(warnThreshold, dateUtil)   -> app.aaps.core.ui.R.attr.highColor
                    else                                                -> app.aaps.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }
}
