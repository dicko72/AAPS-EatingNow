package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import org.joda.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertFailsWith

internal class AapsOmnipodErosManagerTest {

    @Test fun validProfile() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(18000, 1.0),
                ProfileValue(50400, 3.05)
            )
        )
        val basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)
        val entries = basalSchedule.entries
        assertThat(entries).hasSize(3)
        val entry1 = entries[0]
        assertThat(entry1.startTime).isEqualTo(Duration.standardSeconds(0))
        assertThat(entry1.rate).isWithin(0.000001).of(0.5)
        val entry2 = entries[1]
        assertThat(entry2.startTime).isEqualTo(Duration.standardSeconds(18000))
        assertThat(entry2.rate).isWithin(0.000001).of(1.0)
        val entry3 = entries[2]
        assertThat(entry3.startTime).isEqualTo(Duration.standardSeconds(50400))
        assertThat(entry3.rate).isWithin(0.000001).of(3.05)
    }

    @Test fun invalidProfileNullProfile() {
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(null) }
    }

    @Test fun invalidProfileNullEntries() {
        assertFailsWith<IllegalArgumentException> {
            AapsOmnipodErosManager.mapProfileToBasalSchedule(Mockito.mock(Profile::class.java))
        }
    }

    @Test fun invalidProfileZeroEntries() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(emptyArray())
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileNonZeroOffset() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(1800, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileMoreThan24Hours() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(86400, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileNegativeOffset() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(-1, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun roundsToSupportedPrecision() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.04)
            )
        )
        val basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)
        val basalScheduleEntry = basalSchedule.entries[0]
        assertThat(basalScheduleEntry.rate).isWithin(0.000001).of(0.05)
    }
}
