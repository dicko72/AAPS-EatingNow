package app.aaps.core.interfaces.rx.events

class EventBTChange(val state: Change, val deviceName: String?, val deviceAddress: String? = null) : Event() {

    enum class Change {
        CONNECT,
        DISCONNECT
    }
}