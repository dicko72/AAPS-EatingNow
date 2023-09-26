package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DstHelperPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var loop: Loop

    private lateinit var plugin: DstHelperPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }

    @BeforeEach
    fun mock() {
        plugin = DstHelperPlugin(injector, aapsLogger, rh, sp, activePlugin, loop)
    }

    @Test
    fun runTest() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        TimeZone.setDefault(tz)
        var cal = Calendar.getInstance(tz, Locale.ITALIAN)
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN)
        var dateBeforeDST = df.parse("2018-03-25 01:55")
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isTrue()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 03:05")
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 02:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 05:55") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 06:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isFalse()
    }
}
