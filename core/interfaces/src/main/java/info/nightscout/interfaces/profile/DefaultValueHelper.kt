package info.nightscout.interfaces.profile

interface DefaultValueHelper {

    /**
     * returns the configured EatingSoon TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineEatingSoonTT(): Double
    fun determineEatingSoonTTDuration(): Int

    // Eating Now
    fun determineEatingNowTTDuration(): Int {
        val value = sp.getInt(R.string.key_eatingnow_duration, Constants.defaultEatingNowTTDuration)
        return if (value > 0) value else Constants.defaultEatingNowTTDuration
    }

    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineActivityTT(): Double
    fun determineActivityTTDuration(): Int

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @return
     */
    fun determineHypoTT(): Double
    fun determineHypoTTDuration(): Int

    var bgTargetLow: Double
    var bgTargetHigh: Double

    fun determineHighLine(): Double
    fun determineLowLine(): Double
}