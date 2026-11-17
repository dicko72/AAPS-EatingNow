// Modified for Eating Now
package app.aaps.plugins.aps.EN

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts steps with the phone's hardware step counter and saves them in the steps table,
 * one record per 5 minute block, like a watch does.
 *
 * The sensor gives the total steps since the phone started. Only the difference between two
 * readings is used, so steps are not lost while the phone sleeps: they are counted when the next
 * reading arrives (at the latest when the phone wakes for the next BG value).
 * Steps taken while the app is not running are not counted.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneStepCounter(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) : SensorEventListener {

    /** Name saved with each record, so phone steps can be told apart from watch steps. */
    val device: String = "Phone ${Build.MANUFACTURER ?: "unknown"} ${Build.MODEL ?: "unknown"}"

    // Steps per 5 minute block. Written on the sensor thread, read on the loop thread.
    private val blocks: MutableMap<Long, Int> = ConcurrentHashMap()
    @Volatile private var lastTotal = -1L
    @Volatile private var lastWrittenBlock = -1L
    @Volatile var isRunning = false
        private set

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Starts counting. Returns false when the permission is missing or the phone has no step counter. */
    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true
        if (!hasPermission()) return false
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager? ?: return false
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            aapsLogger.warn(LTag.APS, "PhoneStepCounter: this phone has no step counter")
            return false
        }
        isRunning = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        aapsLogger.info(LTag.APS, "PhoneStepCounter: started=$isRunning device=$device")
        return isRunning
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
        isRunning = false
        lastTotal = -1L
        aapsLogger.info(LTag.APS, "PhoneStepCounter: stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type != Sensor.TYPE_STEP_COUNTER || event.values.isEmpty()) return
        val total = event.values[0].toLong()
        // The first reading only sets the start point. A lower total means the counter started again.
        if (lastTotal in 0 until total) {
            val block = blockOf(dateUtil.now())
            blocks.merge(block, (total - lastTotal).toInt(), Int::plus)
            blocks.keys.removeIf { it < block - KEEP_BLOCKS }
        }
        lastTotal = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Saves every finished 5 minute block that is not saved yet. */
    suspend fun flush(now: Long) {
        if (!isRunning) return
        val lastFinished = blockOf(now) - 1
        val first = if (lastWrittenBlock < 0) lastFinished else maxOf(lastWrittenBlock + 1, lastFinished - KEEP_BLOCKS + 1)
        if (first > lastFinished) return
        val rows = (first..lastFinished).map { block ->
            SC(
                duration = BLOCK_MS,
                timestamp = (block + 1) * BLOCK_MS,
                steps5min = stepsIn(block, 1),
                steps10min = stepsIn(block, 2),
                steps15min = stepsIn(block, 3),
                steps30min = stepsIn(block, 6),
                steps60min = stepsIn(block, 12),
                steps180min = stepsIn(block, 36),
                device = device
            )
        }
        persistenceLayer.insertOrUpdateStepsCounts(rows)
        lastWrittenBlock = lastFinished
    }

    /** Steps from [from] to [now]: the saved phone records plus the block that is still running. */
    suspend fun stepsSince(from: Long, now: Long): Int {
        val saved = persistenceLayer.getStepsCountFromTimeToTime(from, now)
            .filter { it.isValid && it.device == device && it.duration == BLOCK_MS && it.timestamp > from }
            .sumOf { it.steps5min }
        return saved + (blocks[blockOf(now)] ?: 0)
    }

    /**
     * Time when the steps since [from] first reached [limit], or null if they have not yet.
     * Saved records give the end of the 5 minute block; the running block counts as [now].
     */
    suspend fun timeStepsReached(from: Long, now: Long, limit: Int): Long? {
        var sum = 0
        persistenceLayer.getStepsCountFromTimeToTime(from, now)
            .filter { it.isValid && it.device == device && it.duration == BLOCK_MS && it.timestamp > from }
            .sortedBy { it.timestamp }
            .forEach { sum += it.steps5min; if (sum >= limit) return it.timestamp }
        sum += blocks[blockOf(now)] ?: 0
        return if (sum >= limit) now else null
    }

    /** Sum of [count] blocks that end with [lastBlock]. */
    private fun stepsIn(lastBlock: Long, count: Int): Int =
        (lastBlock - count + 1..lastBlock).sumOf { blocks[it] ?: 0 }

    private fun blockOf(time: Long): Long = time / BLOCK_MS

    private companion object {

        const val BLOCK_MS = 5 * 60 * 1000L
        const val KEEP_BLOCKS = 36 // 3 hours, the longest window in the steps table
    }
}
