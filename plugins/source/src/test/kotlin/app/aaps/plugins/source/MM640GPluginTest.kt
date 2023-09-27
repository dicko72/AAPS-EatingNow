package app.aaps.plugins.source

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var rh: ResourceHelper

    @BeforeEach
    fun setup() {
        mM640gPlugin = MM640gPlugin({ AndroidInjector { } }, rh, aapsLogger)
    }

    @Test fun advancedFilteringSupported() {
        assertThat(mM640gPlugin.advancedFilteringSupported()).isFalse()
    }
}
