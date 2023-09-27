package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LoadBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin

    class LoadBgData(
        val iobCobCalculator: IobCobCalculator,
        val end: Long
    )

    private fun AutosensDataStore.loadBgData(to: Long, repository: AppRepository, aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        synchronized(dataLock) {
            val start = to - T.hours((24 + 10 /* max dia */).toLong()).msecs()
            // there can be some readings with time in close future (caused by wrong time setting on sensor)
            // so add 2 minutes
            bgReadings = repository
                .compatGetBgReadingsDataFromTime(start, to + T.mins(2).msecs(), false)
                .blockingGet()
            aapsLogger.debug(LTag.AUTOSENS) { "BG data loaded. Size: ${bgReadings.size} Start date: ${dateUtil.dateAndTimeString(start)} End date: ${dateUtil.dateAndTimeString(to)}" }
            createBucketedData(aapsLogger, dateUtil)
        }
    }

    private fun AutosensDataStore.smoothData(activePlugin: ActivePlugin) {
        synchronized(dataLock) {
            bucketedData?.let {
                val smoothedData = activePlugin.activeSmoothing.smooth(it)
                bucketedData = smoothedData
            }
        }
    }

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as LoadBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        data.iobCobCalculator.ads.loadBgData(data.end, repository, aapsLogger, dateUtil)
        data.iobCobCalculator.ads.smoothData(activePlugin)
        rxBus.send(EventBucketedDataCreated())
        data.iobCobCalculator.clearCache()
        return Result.success()
    }
}