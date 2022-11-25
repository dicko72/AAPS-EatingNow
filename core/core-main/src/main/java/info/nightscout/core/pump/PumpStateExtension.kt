package info.nightscout.core.pump

import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val PumpSync.PumpState.TemporaryBasal.plannedRemainingMinutesRoundedUp: Int
    get() = max(ceil((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

val PumpSync.PumpState.TemporaryBasal.durationInMinutes: Int
    get() = T.msecs(duration).mins().toInt()

fun PumpSync.PumpState.TemporaryBasal.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun PumpSync.PumpState.TemporaryBasal.convertedToAbsolute(time: Long, profile: Profile): Double =
    if (isAbsolute) rate
    else profile.getBasal(time) * rate / 100

fun PumpSync.PumpState.TemporaryBasal.toStringFull(dateUtil: DateUtil): String {
    return when {
        isAbsolute -> {
            DecimalFormatter.to2Decimal(rate) + "U/h @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }

        else       -> { // percent
            rate.toString() + "% @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }
    }
}

val PumpSync.PumpState.ExtendedBolus.end: Long
    get() = timestamp + duration

val PumpSync.PumpState.ExtendedBolus.plannedRemainingMinutes: Long
    get() = max(T.msecs(end - System.currentTimeMillis()).mins(), 0L)

fun PumpSync.PumpState.ExtendedBolus.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun PumpSync.PumpState.ExtendedBolus.toStringFull(dateUtil: DateUtil): String =
    "E " + DecimalFormatter.to2Decimal(rate) + "U/h @" +
        dateUtil.timeString(timestamp) +
        " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "min"

