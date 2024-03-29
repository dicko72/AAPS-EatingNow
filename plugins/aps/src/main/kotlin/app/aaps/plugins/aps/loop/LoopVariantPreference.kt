package app.aaps.plugins.aps.loop

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import dagger.android.HasAndroidInjector
import app.aaps.core.interfaces.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

class LoopVariantPreference(context: Context, attrs: AttributeSet?)
    : DropDownPreference(context, attrs) {

    @Inject lateinit var sp: SP

    private var pluginFolder: String? = null

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val typedArray = context.obtainStyledAttributes(attrs, app.aaps.core.ui.R.styleable.LoopVariantPreference, 0, 0)
        pluginFolder = typedArray.getString(app.aaps.core.ui.R.styleable.LoopVariantPreference_pluginFolder)
        key = "key_${pluginFolder}_variant";
        val entries = Vector<CharSequence>()
        // entries.add(DEFAULT)

        val list = context.assets.list("$pluginFolder/")
        list?.forEach {
            if (!it.endsWith(".js"))
                entries.add(it)
        }

        entryValues = entries.toTypedArray()
        setEntries(entries.toTypedArray())
        setDefaultValue(sp.getString(key, DEFAULT))
    }

    companion object {
        const val DEFAULT = "stable"

        fun getVariantFileName(sp: SP, pluginFolder: String) : String
        {
            return when (val variant = sp.getString("key_${pluginFolder}_variant", DEFAULT)) {
                DEFAULT -> "$pluginFolder/stable/determine-basal.js"
                else    -> "$pluginFolder/$variant/determine-basal.js"
            }
        }
    }
}
