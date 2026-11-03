package app.aaps.core.keys.interfaces

interface DoublePreferenceKey : PreferenceKey, DoubleNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Double

    /**
     *  Minimal allowed value
     */
    val min: Double

    /**
     *  Maximal allowed value
     */
    val max: Double

    /**
     *  Value with calculation in simple mode
     */
    val calculatedBySM: Boolean

    /**
     * When true, this value is a glucose amount stored in mg/dl but shown and edited in the
     * profile's current units (mmol/L with one decimal, or mg/dl as an integer). The editor
     * converts with the known units, so it works for small values near the mmol/mg-dl boundary
     * where the magnitude-based `UnitDoublePreferenceKey` auto-detect is unsafe.
     */
    val glucoseDelta: Boolean get() = false

}