package info.nightscout.shared.impl.sharedPreferences

import com.google.common.truth.Truth.assertThat
import android.content.Context
import info.nightscout.shared.impl.SharedPreferencesMock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SPImplementationTest {

    private val sharedPreferences: SharedPreferencesMock = SharedPreferencesMock()
    @Mock lateinit var context: Context

    private lateinit var sut: SPImplementation

    private val someResource = 1
    private val someResource2 = 2

    @BeforeEach
    fun setUp() {
        sut = SPImplementation(sharedPreferences, context)
        Mockito.`when`(context.getString(someResource)).thenReturn("some_resource")
        Mockito.`when`(context.getString(someResource2)).thenReturn("some_resource_2")
    }

    @Test
    fun edit() {
        sut.edit { putBoolean("test", true) }
        assertThat(sut.getBoolean("test", false)).isTrue()
        sut.edit { remove("test") }
        assertThat(sut.contains("test")).isFalse()
        sut.edit { putBoolean(someResource, true) }
        assertThat(sut.getBoolean(someResource, false)).isTrue()
        sut.edit { remove(someResource) }
        assertThat(sut.contains(someResource)).isFalse()

        sut.edit(commit = true) { putDouble("test", 1.0) }
        assertThat(sut.getDouble("test", 2.0)).isEqualTo(1.0)
        sut.edit { putDouble(someResource, 1.0) }
        assertThat(sut.getDouble(someResource, 2.0)).isEqualTo(1.0)
        sut.edit { clear() }
        assertThat(sut.contains(someResource2)).isFalse()

        sut.edit { putInt("test", 1) }
        assertThat(sut.getInt("test", 2)).isEqualTo(1)
        sut.edit { putInt(someResource, 1) }
        assertThat(sut.getInt(someResource, 2)).isEqualTo(1)
        sut.edit { clear() }

        sut.edit { putLong("test", 1L) }
        assertThat(sut.getLong("test", 2L)).isEqualTo(1L)
        sut.edit { putLong(someResource, 1) }
        assertThat(sut.getLong(someResource, 2L)).isEqualTo(1L)
        sut.edit { clear() }

        sut.edit { putString("test", "string") }
        assertThat(sut.getString("test", "a")).isEqualTo("string")
        sut.edit { putString(someResource, "string") }
        assertThat(sut.getString(someResource, "a")).isEqualTo("string")
        sut.edit { clear() }
    }

    @Test
    fun clear() {
        sut.putBoolean("test", true)
        assertThat(sut.getAll()).containsKey("test")
        sut.clear()
        assertThat(sut.getAll()).doesNotContainKey("test")
    }

    @Test
    fun contains() {
        sut.putBoolean("test", true)
        assertThat(sut.contains("test")).isTrue()
        sut.putBoolean(someResource, true)
        assertThat(sut.contains(someResource)).isTrue()
    }

    @Test
    fun remove() {
        sut.putBoolean("test", true)
        sut.remove("test")
        assertThat(sut.contains("test")).isFalse()
        sut.putBoolean(someResource, true)
        sut.remove(someResource)
        assertThat(sut.contains(someResource)).isFalse()
    }

    @Test
    fun getString() {
        sut.putString("test", "string")
        assertThat(sut.getString("test", "")).isEqualTo("string")
        assertThat(sut.getString("test1", "")).isEmpty()
        sut.putString(someResource, "string")
        assertThat(sut.getString(someResource, "")).isEqualTo("string")
        assertThat(sut.getString(someResource2, "")).isEmpty()
    }

    @Test
    fun getStringOrNull() {
        sut.putString("test", "string")
        assertThat(sut.getStringOrNull("test", "")).isEqualTo("string")
        assertThat(sut.getStringOrNull("test1", null)).isNull()
        sut.putString(someResource, "string")
        assertThat(sut.getStringOrNull(someResource, null)).isEqualTo("string")
        assertThat(sut.getStringOrNull(someResource2, null)).isNull()
    }

    @Test
    fun getBoolean() {
        sut.putBoolean("test", true)
        assertThat(sut.getBoolean("test", false)).isTrue()
        sut.putBoolean(someResource, true)
        assertThat(sut.getBoolean(someResource, false)).isTrue()
        sut.putString("string_key", "a")
        assertThat(sut.getBoolean("string_key", true)).isTrue()
        sut.putString(someResource, "a")
        assertThat(sut.getBoolean(someResource, true)).isTrue()
    }

    @Test
    fun getDouble() {
        sut.putDouble("test", 1.0)
        assertThat(sut.getDouble("test", 2.0)).isEqualTo(1.0)
        assertThat(sut.getDouble("test1", 2.0)).isEqualTo(2.0)
        sut.putDouble(someResource, 1.0)
        assertThat(sut.getDouble(someResource, 2.0)).isEqualTo(1.0)
        assertThat(sut.getDouble(someResource2, 2.0)).isEqualTo(2.0)
        sut.putString("string_key", "a")
        assertThat(sut.getDouble("string_key", 1.0)).isEqualTo(1.0)
        sut.putString(someResource, "a")
        assertThat(sut.getDouble(someResource, 1.0)).isEqualTo(1.0)
    }

    @Test
    fun getInt() {
        sut.putInt("test", 1)
        assertThat(sut.getInt("test", 2)).isEqualTo(1)
        assertThat(sut.getInt("test1", 2)).isEqualTo(2)
        sut.putInt(someResource, 1)
        assertThat(sut.getInt(someResource, 2)).isEqualTo(1)
        assertThat(sut.getInt(someResource2, 2)).isEqualTo(2)
        sut.putString("string_key", "a")
        assertThat(sut.getInt("string_key", 1)).isEqualTo(1)
        sut.putString(someResource, "a")
        assertThat(sut.getInt(someResource, 1)).isEqualTo(1)
    }

    @Test
    fun getLong() {
        sut.putLong("test", 1L)
        assertThat(sut.getLong("test", 2L)).isEqualTo(1L)
        assertThat(sut.getLong("test1", 2L)).isEqualTo(2L)
        sut.putLong(someResource, 1L)
        assertThat(sut.getLong(someResource, 2L)).isEqualTo(1L)
        assertThat(sut.getLong(someResource2, 2L)).isEqualTo(2L)
        sut.putString("string_key", "a")
        assertThat(sut.getLong("string_key", 1L)).isEqualTo(1L)
        sut.putString(someResource, "a")
        assertThat(sut.getLong(someResource, 1L)).isEqualTo(1L)
    }

    @Test
    fun incLong() {
        sut.incLong(someResource)
        assertThat(sut.getLong(someResource, 3L)).isEqualTo(1L)
        sut.incLong(someResource)
        assertThat(sut.getLong(someResource, 3L)).isEqualTo(2L)
    }

    @Test
    fun incInt() {
        sut.incInt(someResource)
        assertThat(sut.getInt(someResource, 3)).isEqualTo(1)
        sut.incInt(someResource)
        assertThat(sut.getInt(someResource, 3)).isEqualTo(2)
    }
}
