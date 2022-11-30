package info.nightscout.ui.dialogs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.aps.R
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import info.nightscout.interfaces.aps.ENDefaults

class ENVariantPreference(context: Context, attrs: AttributeSet?)
    : DropDownPreference(context, attrs) {

    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        val entries = Vector<CharSequence>()
        val list = context.assets.list("EN/")
        list?.forEach {
            if (!it.equals("determine-basal.js"))
                entries.add(it)
        }

        entryValues = entries.toTypedArray()
        setEntries(entries.toTypedArray())
        setDefaultValue(sp.getString(R.string.key_en_variant, ENDefaults.variant))
    }
}
