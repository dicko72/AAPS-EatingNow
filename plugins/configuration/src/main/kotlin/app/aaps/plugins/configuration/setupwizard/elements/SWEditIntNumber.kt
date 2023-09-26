package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.ui.elements.NumberPicker
import dagger.android.HasAndroidInjector
import java.text.DecimalFormat

class SWEditIntNumber(injector: HasAndroidInjector, private val init: Int, private val min: Int, private val max: Int) : SWItem(injector, Type.NUMBER) {

    private val validator: (Int) -> Boolean = { value -> value in min..max }
    private var updateDelay = 0

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (validator.invoke(SafeParse.stringToInt(s.toString())))
                    save(s.toString(), updateDelay.toLong())
            }

            override fun afterTextChanged(s: Editable) {}
        }

        val l = TextView(context)
        l.id = View.generateViewId()
        label?.let { l.setText(it) }
        l.setTypeface(l.typeface, Typeface.BOLD)
        layout.addView(l)
        val initValue = sp.getInt(preferenceId, init)
        val numberPicker = NumberPicker(context)
        numberPicker.setParams(initValue.toDouble(), min.toDouble(), max.toDouble(), 1.0, DecimalFormat("0"), false, null, watcher)

        layout.addView(numberPicker)
        val c = TextView(context)
        c.id = View.generateViewId()
        comment?.let { c.setText(it) }
        c.setTypeface(c.typeface, Typeface.ITALIC)
        layout.addView(c)
        super.generateDialog(layout)
    }

    fun preferenceId(preferenceId: Int): SWEditIntNumber {
        this.preferenceId = preferenceId
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditIntNumber {
        this.updateDelay = updateDelay
        return this
    }

}