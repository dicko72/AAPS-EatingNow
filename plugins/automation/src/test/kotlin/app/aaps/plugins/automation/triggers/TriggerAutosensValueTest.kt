package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataObject
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class TriggerAutosensValueTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        `when`(sp.getDouble(Mockito.eq(app.aaps.core.utils.R.string.key_openapsama_autosens_max), ArgumentMatchers.anyDouble())).thenReturn(1.2)
        `when`(sp.getDouble(Mockito.eq(app.aaps.core.utils.R.string.key_openapsama_autosens_min), ArgumentMatchers.anyDouble())).thenReturn(0.7)
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(generateAutosensData())
        var t = TriggerAutosensValue(injector)
        t.autosens.value = 110.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.autosens.value).isWithin(0.01).of(110.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 100.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.autosens.value).isWithin(0.01).of(100.0)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 50.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 310.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 420.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 20.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(AutosensDataObject(injector))
        t = TriggerAutosensValue(injector)
        t.autosens.value = 80.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isFalse()

        // Test autosensData == null and Comparator == IS_NOT_AVAILABLE
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(null)
        t = TriggerAutosensValue(injector)
        t.comparator.value = Comparator.Compare.IS_NOT_AVAILABLE
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 213.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        val t1 = t.duplicate() as TriggerAutosensValue
        assertThat(t1.autosens.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var asJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"value\":410},\"type\":\"TriggerAutosensValue\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        JSONAssert.assertEquals(asJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerAutosensValue
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.autosens.value).isWithin(0.01).of(410.0)
    }

    @Test fun iconTest() {
        assertThat(TriggerAutosensValue(injector).icon().get()).isEqualTo(R.drawable.ic_as)
    }

    private fun generateAutosensData() = AutosensDataObject(injector)
}
