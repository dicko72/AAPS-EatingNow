package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal class TidepoolCallback<T>(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val session: Session,
    private val name: String,
    private val onSuccess: () -> Unit,
    private val onFail: () -> Unit
) :
    Callback<T> {

    override fun onResponse(call: Call<T>, response: Response<T>) {
        if (response.isSuccessful && response.body() != null) {
            aapsLogger.debug(LTag.TIDEPOOL, "$name success")
            session.populateBody(response.body())
            session.populateHeaders(response.headers())
            onSuccess()
        } else {
            val msg = name + " was not successful: " + response.code() + " " + response.message()
            aapsLogger.debug(LTag.TIDEPOOL, msg)
            rxBus.send(EventTidepoolStatus(msg))
            onFail()
        }
    }

    override fun onFailure(call: Call<T>, t: Throwable) {
        val msg = "$name Failed: $t"
        aapsLogger.debug(LTag.TIDEPOOL, msg)
        rxBus.send(EventTidepoolStatus(msg))
        onFail()
    }

}
