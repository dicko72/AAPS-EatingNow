// Modified for Eating Now
package app.aaps.core.interfaces.aps

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class ENConfig(
    var EatingNowTimeStart: Int,
    var EatingNowTimeEnd: Int,
    var OvernightSMBRestrict: Double
)