package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgPCCommStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3002)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "PC comm stop received")
    }
}