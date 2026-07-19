// Modified for Eating Now
package app.aaps.plugins.aps.EN

import android.content.Context
import android.content.Intent
import androidx.collection.LongSparseArray
import androidx.collection.forEach
import androidx.core.net.toUri
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.ENConfig
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.utils.extensions.put
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.openAPS.TddStatus
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.ln
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
open class ENPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    config: Config,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    private val preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val tddCalculator: TddCalculator,
    private val bgQualityCheck: BgQualityCheck,
    private val uiInteraction: UiInteraction,
    private val determineBasalEN: DetermineBasalEN,
    private val profiler: Profiler,
    private val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB,
    private val apsResultProvider: Provider<APSResult>
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.eatingnow_plugin)
        .shortName(R.string.eatingnow_plugin_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .preferencesVisibleInSimpleMode(false)
        .showInList(showInList = { config.APS })
        .description(R.string.eatingnow_plugin_description)
        .setDefault(),
    aapsLogger, rh
), APS, PluginConstraints {

    override fun onStart() {
        super.onStart()
        var count = 0
        val apsResults = persistenceLayer.getApsResults(dateUtil.now() - T.days(1).msecs(), dateUtil.now())
        apsResults.forEach {
            val glucose = it.glucoseStatus?.glucose ?: return@forEach
            val variableSens = it.variableSens ?: return@forEach
            val timestamp = it.date
            val key = timestamp - timestamp % T.mins(30).msecs() + glucose.toLong()
            if (variableSens > 0) dynIsfCache.put(key, variableSens)
            count++
        }
        aapsLogger.debug(LTag.APS, "Loaded $count variable sensitivity values from database")
    }

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.SMB
    override var lastAPSResult: APSResult? = null
    override fun supportsDynamicIsf(): Boolean = preferences.get(BooleanKey.ApsUseDynamicSensitivity)

    override fun getIsfMgdl(profile: Profile, caller: String): Double? {
        val start = dateUtil.now()
        val multiplier = (profile as ProfileSealed.EPS).value.originalPercentage / 100.0
        val sensitivity = calculateVariableIsf(start, multiplier)
        if (sensitivity.second == null)
            uiInteraction.addNotificationValidTo(
                Notification.DYN_ISF_FALLBACK, start,
                rh.gs(R.string.fallback_to_isf_no_tdd, sensitivity.first), Notification.INFO, dateUtil.now() + T.mins(1).msecs()
            )
        else
            uiInteraction.dismissNotification(Notification.DYN_ISF_FALLBACK)
        profiler.log(LTag.APS, "getIsfMgdl() multiplier=${multiplier} reason=${sensitivity.first} sensitivity=${sensitivity.second} caller=$caller", start)
        return sensitivity.second
    }

    override fun getAverageIsfMgdl(timestamp: Long, caller: String): Double? {
        var count = 0
        var sum = 0.0
        val start = timestamp - T.hours(24).msecs()
        dynIsfCache.forEach { key, value ->
            if (key in start..timestamp) {
                count++
                sum += value
            }
        }
        val sensitivity = if (count == 0) null else sum / count
        aapsLogger.debug(LTag.APS, "getAverageIsfMgdl() $sensitivity from $count values ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $caller")
        return sensitivity
    }

    override fun specialEnableCondition(): Boolean {
        return try {
            activePlugin.activePump.pumpDescription.isTempBasalCapable
        } catch (_: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun specialShowInListCondition(): Boolean {
        try {
            val pump = activePlugin.activePump
            return pump.pumpDescription.isTempBasalCapable
        } catch (_: Exception) {
            return true
        }
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)

        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        val smbAlwaysEnabled = preferences.get(BooleanKey.ApsUseSmbAlways)
        val uamEnabled = preferences.get(BooleanKey.ApsUseUam)
        val advancedFiltering = activePlugin.activeBgSource.advancedFilteringSupported()
        val autoSensOrDynIsfSensEnabled = if (preferences.get(BooleanKey.ApsUseDynamicSensitivity)) {
            preferences.get(BooleanKey.ApsDynIsfAdjustSensitivity)
        } else {
            preferences.get(BooleanKey.ApsUseAutosens)
        }

        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbAlways.key)?.isVisible = smbEnabled && advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbWithCob.key)?.isVisible = smbEnabled && !smbAlwaysEnabled && advancedFiltering || smbEnabled && !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbWithLowTt.key)?.isVisible = smbEnabled && !smbAlwaysEnabled && advancedFiltering || smbEnabled && !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbAfterCarbs.key)?.isVisible = smbEnabled && !smbAlwaysEnabled && advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsResistanceLowersTarget.key)?.isVisible = autoSensOrDynIsfSensEnabled
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsSensitivityRaisesTarget.key)?.isVisible = autoSensOrDynIsfSensEnabled
        preferenceFragment.findPreference<AdaptiveIntPreference>(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb.key)?.isVisible = smbEnabled && uamEnabled
    }

    private val dynIsfCache = LongSparseArray<Double>()

    @Synchronized
    private fun calculateVariableIsf(timestamp: Long, multiplier: Double): Pair<String, Double?> {
        if (!preferences.get(BooleanKey.ApsUseDynamicSensitivity)) return Pair("OFF", null)

        val result = persistenceLayer.getApsResultCloseTo(timestamp)
        if (result?.variableSens != null && result.variableSens != 0.0) {
            //aapsLogger.debug("calculateVariableIsf $caller DB  ${dateUtil.dateAndTimeAndSecondsString(timestamp)} ${result.variableSens}")
            return Pair("DB", result.variableSens)
        }

        val glucose = glucoseStatusProvider.glucoseStatusData?.glucose ?: return Pair("GLUC", null)
        // Round down to 30 min and use it as a key for caching
        // Add BG to key as it affects calculation
        val key = timestamp - timestamp % T.mins(30).msecs() + glucose.toLong()
        val cached = dynIsfCache[key]
        if (cached != null && timestamp < dateUtil.now()) {
            //aapsLogger.debug("calculateVariableIsf $caller HIT ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $cached")
            return Pair("HIT", cached)
        }

        val dynIsfResult = calculateRawDynIsf(multiplier)
        if (!dynIsfResult.tddPartsCalculated()) return Pair("TDD miss", null)
        // no cached result found, let's calculate the value
        //aapsLogger.debug("calculateVariableIsf $caller CAL ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $sensitivity")
        dynIsfCache.put(key, dynIsfResult.variableSensitivity)
        if (dynIsfCache.size() > 1000) dynIsfCache.clear()
        return Pair("CALC", dynIsfResult.variableSensitivity)
    }

    internal class DynIsfResult {

        var tdd1D: Double? = null
        var tdd7D: Double? = null
        var tddLast24H: Double? = null
        var tddLast4H: Double? = null
        var tddLast8to4H: Double? = null
        var tdd: Double? = null
        var variableSensitivity: Double? = null
        var insulinDivisor: Int = 0

        var tddLast24HCarbs = 0.0
        var tdd7DDataCarbs = 0.0
        var tdd7DAllDaysHaveCarbs = false

        fun tddPartsCalculated() = tdd1D != null && tdd7D != null && tddLast24H != null && tddLast4H != null && tddLast8to4H != null

        fun log() =
            "DynIsfResult: tdd1D=$tdd1D tdd7D=$tdd7D tddLast24H=$tddLast24H tddLast4H=$tddLast4H tddLast8to4H=$tddLast8to4H tdd=$tdd variableSensitivity=$variableSensitivity insulinDivisor=$insulinDivisor tdd7DDataCarbs=$tdd7DDataCarbs tdd7DAllDaysHaveCarbs=$tdd7DAllDaysHaveCarbs"
    }

    private fun calculateRawDynIsf(multiplier: Double): DynIsfResult {
        val dynIsfResult = DynIsfResult()
        // DynamicISF specific
        // without these values DynISF doesn't work properly
        // Current implementation is fallback to SMB if TDD history is not available. Thus calculated here
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData as GlucoseStatusSMB?
        dynIsfResult.tdd1D = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.data?.totalAmount
        tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.let {
            dynIsfResult.tdd7D = it.data.totalAmount
            dynIsfResult.tdd7DDataCarbs = it.data.carbs
            dynIsfResult.tdd7DAllDaysHaveCarbs = it.allDaysHaveCarbs
        }
        tddCalculator.calculateDaily(-24, 0)?.also {
            dynIsfResult.tddLast24H = it.totalAmount
            dynIsfResult.tddLast24HCarbs = it.carbs
        }
        dynIsfResult.tddLast4H = tddCalculator.calculateDaily(-4, 0)?.totalAmount
        dynIsfResult.tddLast8to4H = tddCalculator.calculateDaily(-8, -4)?.totalAmount

        val insulin = activePlugin.activeInsulin
        dynIsfResult.insulinDivisor = when {
            insulin.peak > 65 -> 55 // rapid peak: 75
            insulin.peak > 50 -> 65 // ultra rapid peak: 55
            else              -> 75 // lyumjev peak: 45
        }


        if (dynIsfResult.tddPartsCalculated() && glucoseStatus != null) {
            val tddStatus = TddStatus(dynIsfResult.tdd1D!!, dynIsfResult.tdd7D!!, dynIsfResult.tddLast24H!!, dynIsfResult.tddLast4H!!, dynIsfResult.tddLast8to4H!!)
            val tddWeightedFromLast8H = ((1.4 * tddStatus.tddLast4H) + (0.6 * tddStatus.tddLast8to4H)) * 3
            dynIsfResult.tdd = ((tddWeightedFromLast8H * 0.33) + (tddStatus.tdd7D * 0.34) + (tddStatus.tdd1D * 0.33)) * preferences.get(IntKey.ApsDynIsfAdjustmentFactor) / 100.0 * multiplier
            dynIsfResult.variableSensitivity = Round.roundTo(1800 / (dynIsfResult.tdd!! * (ln((glucoseStatus.glucose / dynIsfResult.insulinDivisor) + 1))), 0.1)
            aapsLogger.debug(LTag.APS, "multiplier=$multiplier dynIsfResult=${dynIsfResult.log()} glucoseStatus=${glucoseStatus.glucose} insulinDivisor=${dynIsfResult.insulinDivisor}")
        }
        return dynIsfResult
    }

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(app.aaps.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(app.aaps.core.ui.R.string.no_profile_set))
            return
        }
        if (!isEnabled()) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return
        }
        if (glucoseStatus == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
            return
        }

        val inputConstraints = ConstraintObject(0.0, aapsLogger) // fake. only for collecting all results

        if (!hardLimits.checkHardLimits(profile.dia, app.aaps.core.ui.R.string.profile_dia, hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                app.aaps.core.ui.R.string.profile_carbs_ratio_value,
                hardLimits.minIC(),
                hardLimits.maxIC()
            )
        ) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSSMBPlugin"), app.aaps.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), app.aaps.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, app.aaps.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return

        // End of check, start gathering data

        val dynIsfMode =
            preferences.get(BooleanKey.ApsUseDynamicSensitivity) && hardLimits.checkHardLimits(
                preferences.get(IntKey.ApsDynIsfAdjustmentFactor).toDouble(),
                R.string.dyn_isf_adjust_title,
                IntKey.ApsDynIsfAdjustmentFactor.min.toDouble(),
                IntKey.ApsDynIsfAdjustmentFactor.max.toDouble()
            )
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        // val advancedFiltering = if (ENActive) true else constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

        val now = dateUtil.now()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val currentTemp = CurrentTemp(
            duration = tb?.plannedRemainingMinutes ?: 0,
            rate = tb?.convertedToAbsolute(now, profile) ?: 0.0,
            minutesrunning = tb?.getPassedDurationToTimeInMinutes(now)
        )
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), app.aaps.core.ui.R.string.profile_low_target, HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1])
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), app.aaps.core.ui.R.string.profile_high_target, HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1])
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG[0], HardLimits.LIMIT_TARGET_BG[1])
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.lowTarget, app.aaps.core.ui.R.string.temp_target_low_target, HardLimits.LIMIT_TEMP_MIN_BG[0], HardLimits.LIMIT_TEMP_MIN_BG[1])
            maxBg = hardLimits.verifyHardLimits(tempTarget.highTarget, app.aaps.core.ui.R.string.temp_target_high_target, HardLimits.LIMIT_TEMP_MAX_BG[0], HardLimits.LIMIT_TEMP_MAX_BG[1])
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG[0], HardLimits.LIMIT_TEMP_TARGET_BG[1])
        }

        var autosensResult = AutosensResult()
        // var variableSensitivity = 0.0
        // var tdd = 0.0
        // var insulinDivisor = 0
        val dynIsfResult = calculateRawDynIsf((profile as ProfileSealed.EPS).value.originalPercentage / 100.0)
        if (dynIsfMode && !dynIsfResult.tddPartsCalculated()) {
            uiInteraction.addNotificationValidTo(
                Notification.SMB_FALLBACK, dateUtil.now(),
                rh.gs(R.string.fallback_smb_no_tdd), Notification.INFO, dateUtil.now() + T.mins(1).msecs()
            )
            inputConstraints.copyReasons(
                ConstraintObject(false, aapsLogger).also {
                    it.set(false, rh.gs(R.string.fallback_smb_no_tdd), this)
                }
            )
            inputConstraints.copyReasons(
                ConstraintObject(false, aapsLogger).apply {
                    set(true, "tdd1D=${dynIsfResult.tdd1D} tdd7D=${dynIsfResult.tdd7D} tddLast4H=${dynIsfResult.tddLast4H} tddLast8to4H=${dynIsfResult.tddLast8to4H} tddLast24H=${dynIsfResult.tddLast24H}", this)
                }
            )
        }
        if (dynIsfMode && dynIsfResult.tddPartsCalculated()) {
            uiInteraction.dismissNotification(Notification.SMB_FALLBACK)
            // Compare insulin consumption of last 24h with last 7 days average
            val tddRatio = if (preferences.get(BooleanKey.ApsDynIsfAdjustSensitivity)) dynIsfResult.tddLast24H!! / dynIsfResult.tdd7D!! else 1.0
            // Because consumed carbs affects total amount of insulin compensate final ratio by consumed carbs ratio
            // take only 60% (expecting 40% basal). We cannot use bolus/total because of SMBs
            val carbsRatio = if (
                preferences.get(BooleanKey.ApsDynIsfAdjustSensitivity) &&
                dynIsfResult.tddLast24HCarbs != 0.0 &&
                dynIsfResult.tdd7DDataCarbs != 0.0 &&
                dynIsfResult.tdd7DAllDaysHaveCarbs
            ) ((dynIsfResult.tddLast24HCarbs / dynIsfResult.tdd7DDataCarbs - 1.0) * 0.6) + 1.0 else 1.0
            autosensResult = AutosensResult(
                ratio = tddRatio / carbsRatio,
                ratioFromTdd = tddRatio,
                ratioFromCarbs = carbsRatio
            )
        } else {
            if (constraintsChecker.isAutosensModeEnabled().value()) {
                val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
                if (autosensData == null) {
                    rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                    return
                }
                autosensResult = autosensData.autosensResult
            } else autosensResult.sensResult = "autosens disabled"
        }

        @Suppress("KotlinConstantConditions")
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(autosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()

        // Eating Now Variables

        // Define the initial start time variables
        val startHour = preferences.get(IntKey.Eatingnow_timestart)
        val endHour = preferences.get(IntKey.Eatingnow_timeend)

        val midnightToday = MidnightTime.calc(now)
        val oneHourMs = 3_600_000L
        val oneDayMs = 86_400_000L

        val startMs = midnightToday + (startHour * oneHourMs)
        val endMs = midnightToday + (endHour * oneHourMs)

        val (EatingNowTimeStart, EatingNowTimeEnd) = when {
            endHour > startHour -> startMs to endMs             // Normal same-day window
            now < endMs -> (startMs - oneDayMs) to endMs        // Crosses midnight, currently early morning
            else -> startMs to (endMs + oneDayMs)               // Crosses midnight, currently evening/daytime
        }
        val ENTimeOK = now >= EatingNowTimeStart && now < EatingNowTimeEnd
        val AutoStartEN = preferences.get(BooleanKey.EatingNow_AutoStart)

        // Eating Now Treatments to trigger ENW and Start EN
        val todaysENTargets = persistenceLayer.getENTemporaryTargetsFromTime(EatingNowTimeStart, true).blockingGet() ?: emptyList()

        // COB Trigger
        val ignoreCOB = preferences.get(BooleanKey.EatingNow_IgnoreCOB)
        val units = profileFunction.getUnits()
        val ENWCOBTrigger = preferences.get(DoubleKey.Eatingnow_enw_triggercob)
        val todaysCarbs = if (ENWCOBTrigger > 0.0 && !ignoreCOB) {
            persistenceLayer.getCarbsFromTime(EatingNowTimeStart, true)
                .blockingGet()
                ?.filter { it.amount >= ENWCOBTrigger && it.duration == 0L }
                ?: emptyList()
        } else {
            emptyList()
        }



        // Filter immediately without storing the intermediate list
        val ENWBolusTrigger = max(Round.roundTo(preferences.get(DoubleKey.Eatingnow_enw_triggerbolus), 0.01), 0.0)
        val todaysTriggerBoluses = if (ENWBolusTrigger > 0.0) {
            persistenceLayer.getBolusesFromTime(EatingNowTimeStart, true)
                .blockingGet()
                ?.filter { it.amount >= ENWBolusTrigger && it.type == BS.Type.NORMAL }
                ?: emptyList()
        } else {
            emptyList() // Instantly return nothing if the trigger is disabled
        }

        // Variables based on todays ENW TTs
        val mealCount = todaysENTargets.size
        val ENStarted = todaysENTargets.isNotEmpty() || (todaysCarbs.isNotEmpty() && !ignoreCOB) || todaysTriggerBoluses.isNotEmpty() || (ENTimeOK && AutoStartEN)

        // Directly extract the timestamps rather than the events, avoiding list concatenations
        val lastENTT = todaysENTargets.maxByOrNull { it.timestamp }
        val lastENTTTime = todaysENTargets.maxOfOrNull { it.timestamp }

        // Temp ENWTT is when TT is at target
        val activeTT = persistenceLayer.getTemporaryTargetActiveAt(now)
        val manualENTT = isTempTarget &&
            targetBg == profile.getTargetMgdl() &&
            activeTT?.reason !in listOf(TT.Reason.EATING_NOW, TT.Reason.EATING_NOW_PB)
        val manualENTTDuration = activeTT?.duration?.div(60_000L.toInt()) ?: 0
        val manualENTTtimestamp = activeTT?.timestamp ?: 0L

        val ENWDurationMs = preferences.get(IntKey.Eatingnow_enw_minutes) * 60_000L

        // Gather all valid trigger windows as pairs of (startTime, endTime)
        val allTriggerWindows = mutableListOf<Pair<Long, Long>>()
        if (!ignoreCOB) todaysCarbs.forEach { allTriggerWindows.add(it.timestamp to (it.timestamp + ENWDurationMs)) }
        todaysTriggerBoluses.forEach { allTriggerWindows.add(it.timestamp to (it.timestamp + ENWDurationMs)) }
        todaysENTargets.forEach { allTriggerWindows.add(it.timestamp to (it.timestamp + it.duration)) }

        // Sort chronologically to build continuous overlapping windows
        val sortedWindows = allTriggerWindows.filter { it.first <= now }.sortedBy { it.first }

        var calculatedStartTime: Long? = null
        var calculatedEndTime: Long? = null

        for (window in sortedWindows) {
            val start = window.first
            val end = window.second
            if (calculatedEndTime == null || start > calculatedEndTime) {
                // No overlap detected, start a brand new window block
                calculatedStartTime = start
                calculatedEndTime = end
            } else {
                // Overlap detected! Keep the original start time, extend the end time if needed
                calculatedEndTime = max(calculatedEndTime, end)
            }
        }

        val ENWStartTime = when {
            manualENTT -> manualENTTtimestamp
            else -> calculatedStartTime
        }

        val ENWEndTime = when {
            manualENTT -> manualENTTtimestamp + (manualENTTDuration * 60_000L)
            else -> calculatedEndTime
        }

        // Are any ENW TTs in that list CURRENTLY active
        val activeENTT = todaysENTargets.find {
            val start = it.timestamp
            val end = start + (it.duration)
            now >= start && now < end // true if 'now' falls inside the target's time window
        }

        val ENWStarted = ENWStartTime != null && ENWEndTime != null && now >= ENWStartTime && now < ENWEndTime // check to see if TT or COB would mean the ENW is started

        // Other ENW variables
        val ENWActive = when {
            manualENTT -> TT.Reason.EATING_NOW
            ENWStarted -> activeENTT?.reason ?: TT.Reason.EATING_NOW
            else -> null
        }

        val ENWfirstMeal = (mealCount == 1 && activeENTT != null) // is this the firstmeal?
        val ENActive = ENTimeOK && ENStarted && (!isTempTarget && ENWActive == null || ENWActive != null || manualENTT) // is EN activated?
        val advancedFiltering = if (ENWActive != null) true else constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

        // Calculate the amount of time ENW has been running
        val ENWRunTime = if (ENWStartTime != null && now >= ENWStartTime) {
            // If ENWEndTime exists, don't let the calculation go past it!
            val calcEndTime = if (ENWEndTime != null) now.coerceAtMost(ENWEndTime) else now
            ((calcEndTime - ENWStartTime) / 60_000L).toInt()
        } else 0

            // Calculate vars using the prefs or manualENTT
        val ENWNetIOBMax = if (manualENTT) manualENTTDuration/10.0 else preferences.get(DoubleKey.Eatingnow_enw_maxiob)
        val ENWDuration = if (manualENTT) manualENTTDuration.toInt() else preferences.get(IntKey.Eatingnow_enw_minutes)

        // Calculate Net IOB (Using the start time of the most recent target)
        val isENWIOBActive = ENWStartTime != null && ENWEndTime != null && now < ENWEndTime + T.hours(3).msecs()
        val ENWNetIOB = if (isENWIOBActive) tddCalculator.calculateIntervalNet(ENWStartTime, now, allowMissingData = true)?.totalAmount ?: 0.0  else 0.0
        val ENWNetIOBRemaining = if (isENWIOBActive) max(ENWNetIOBMax - ENWNetIOB, 0.0) else 0.0

        val normalTargetBG = profile.getTargetMgdl() // profile target bg at this time

        // Calculate high bg for resistance assistance
        // rolling window from the end of the last meal window based upon insulinPeak
        val highThresholdMgdl = profileUtil.convertToMgdl(preferences.get(DoubleKey.highBGthreshold), units) + normalTargetBG
        val recentBg = persistenceLayer.getBgReadingsDataFromTimeToTime(now - T.hours(6).msecs(), now, ascending = false)
        var highSinceTime = now
        for (gv in recentBg) { if (gv.value > highThresholdMgdl) highSinceTime = gv.timestamp else break }  // newest→oldest
        val minutesHigh = ((now - highSinceTime) / 60_000L).toInt()
        // ledger lookback: corrections count for ~1.5× the insulin's peak time (Novorapid 75 → ~113m)
        val highRangeMins = activePlugin.activeInsulin.peak * 3 / 2
        val highSinceStart = maxOf(highSinceTime, now - T.mins(highRangeMins.toLong()).msecs(), (ENWEndTime ?: 0L).coerceAtMost(now))
        val netIOBSinceHigh = if (minutesHigh > 0)
            tddCalculator.calculateIntervalNet(highSinceStart, now, allowMissingData = true)?.totalAmount ?: 0.0
        else 0.0

        // Past insulin activity summit — same computation the Overview activity line plots.
        // Telemetry for the signed peak timeline (−y) and waveLive calibration; NOT used for dosing yet.
        var pastPeakTime = now
        var pastPeakActivity = 0.0
        var t = now - T.hours(4).msecs()
        while (t <= now) {
            val p = profileFunction.getProfile(t)
            if (p != null) {
                val act = iobCobCalculator.calculateFromTreatmentsAndTemps(t, p).activity
                if (act >= pastPeakActivity) { pastPeakActivity = act; pastPeakTime = t }
            }
            t += T.mins(5).msecs()
        }
        val pastPeakAgoMins = ((now - pastPeakTime) / 60_000L).toInt()

        // using ISF scaling?
        val useISFscaler = preferences.get(BooleanKey.EatingNow_UseISFscaler)
        if (useISFscaler) preferences.put(BooleanKey.ApsUseDynamicSensitivity,false) // disable DynISF if using ISF scaler

        // EN profile scaling within ENW
        val profileCarbRatio = profile.getIc()
        val profileIsf = profileUtil.convertToMgdlDetect(profile.getIsfMgdl("ENPlugin"))

        val enwProfileScalePct = preferences.get(IntKey.enwProfileScalePct)/100.0
        val scaleMultiplier = if (ENWActive != null) {
            enwProfileScalePct // use prefs 125 1.25
        } else {
            1.0 // Standard 100% profile 1.0
        }
        val scaledCarbRatio = Round.roundTo(profileCarbRatio / scaleMultiplier, 0.01)
        val scaledIsf = Round.roundTo(profileIsf / scaleMultiplier, 0.1)

        @Suppress("KotlinConstantConditions")
        val oapsProfile = OapsProfile(
            dia = 0.0, // not used
            min_5m_carbimpact = 0.0, // not used
            max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = profile.getMaxDailyBasal(),
            max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = minBg,
            max_bg = maxBg,
            target_bg = targetBg,
            carb_ratio = scaledCarbRatio,
            // sens = profile.getIsfMgdl("ENPlugin"),
            // OVERRIDE: Safety firewall to catch if the Profile leaked mmol/L (e.g. 8.0) instead of mg/dL
            sens = scaledIsf,
            autosens_adjust_targets = false, // not used
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
            high_temptarget_raises_sensitivity = true,
            low_temptarget_lowers_sensitivity = false,
            sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
            resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
            adv_target_adjustments = SMBDefaults.adv_target_adjustments,
            exercise_mode = SMBDefaults.exercise_mode,
            half_basal_exercise_target = SMBDefaults.half_basal_exercise_target,
            maxCOB = SMBDefaults.maxCOB,
            skip_neutral_temps = pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = SMBDefaults.remainingCarbsCap,
            enableUAM = constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
            enableSMB_with_COB = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob),
            enableSMB_with_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt),
            allowSMB_with_high_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt),
            enableSMB_always = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering,
            enableSMB_after_carbs = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAfterCarbs) && advancedFiltering,
            maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
            maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
            bolus_increment = pump.pumpDescription.bolusStep,
            carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
            current_basal = activePlugin.activePump.baseBasalRate,
            temptargetSet = isTempTarget,
            autosens_max = preferences.get(DoubleKey.AutosensMax),
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = if (dynIsfMode) dynIsfResult.variableSensitivity ?: 0.0 else 0.0,
            insulinDivisor = dynIsfResult.insulinDivisor,
            TDD = dynIsfResult.tdd ?: 0.0
        )

        // Define the variables to be available to DetermineBasalEN.kt
        @Suppress("KotlinConstantConditions")
        val enConfig = ENConfig(
            // General
            ENTimeStart = EatingNowTimeStart,
            ENTimeEnd = EatingNowTimeEnd,
            ENActive = ENActive,
            AutoStartEN = preferences.get(BooleanKey.EatingNow_AutoStart),
            OvernightSMBRestrict = profileUtil.convertToMgdl(preferences.get(DoubleKey.Eatingnow_overnightSMB), units) + normalTargetBG,
            IgnoreCOB = ignoreCOB,
            SafetyMaxBolus = preferences.get(DoubleKey.SafetyMaxBolus),
            useISFscaler = useISFscaler,
            highBGthreshold = profileUtil.convertToMgdl(preferences.get(DoubleKey.highBGthreshold), units) + normalTargetBG,
            netIOBSinceHigh = Round.roundTo(netIOBSinceHigh, 0.01),
            minutesHigh = minutesHigh,
            pastPeakAgoMins = pastPeakAgoMins,
            pastPeakActivity = Round.roundTo(pastPeakActivity, 0.0001),
            normalTargetBG = normalTargetBG,
            insulinPeakMins = activePlugin.activeInsulin.peak,

            // ENW variables
            ENWfirstMeal = ENWfirstMeal,
            ENWActive = ENWActive,
            ENWStartTime = ENWStartTime,
            ENWEndTime = ENWEndTime,
            ENWRunTime = ENWRunTime,
            ENWNetIOB = Round.roundTo(ENWNetIOB, 0.01),
            ENWDuration = ENWDuration,
            enwProfileScalePct = enwProfileScalePct,
            ENWsmbPct = preferences.get(IntKey.Eatingnow_enw_smb_pct),
            ENWcobMaxbolus = max(Round.roundTo(preferences.get(DoubleKey.Eatingnow_enw_cob_maxbolus), 0.01), 0.0),
            ENWuamMaxbolus = max(Round.roundTo(preferences.get(DoubleKey.Eatingnow_enw_uam_maxbolus), 0.01), 0.0),
            ENWNetIOBMax = max(Round.roundTo(ENWNetIOBMax, 0.01), 0.0),
            ENWNetIOBRemaining = max(Round.roundTo(ENWNetIOBRemaining, 0.01), 0.0),
            ENWprebolus = max(Round.roundTo(preferences.get(DoubleKey.Eatingnow_enw_prebolus), 0.01), 0.0),
            ENWuamPlusMaxbolus = max(Round.roundTo(preferences.get(DoubleKey.Eatingnow_enw_uamplus_maxbolus), 0.01), 0.0),
            OverrideENWNetIOBMax =  preferences.get(BooleanKey.EatingNow_OverrideENWNetIOBMax),
            ENWBolusTrigger = ENWBolusTrigger,
            ENWCOBTrigger = ENWCOBTrigger
        )

        val microBolusAllowed = constraintsChecker.isSMBModeEnabled(ConstraintObject(tempBasalFallback.not(), aapsLogger)).also { inputConstraints.copyReasons(it) }.value()
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT

        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal SMB <<<")
        aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
        aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
        aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.joinToString()}")
        aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
        aapsLogger.debug(LTag.APS, "ENConfig:           $enConfig")
        aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
        aapsLogger.debug(LTag.APS, "Meal data:          $mealData")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "flatBGsDetected:    $flatBGsDetected")
        aapsLogger.debug(LTag.APS, "DynIsfMode:         $dynIsfMode")

        determineBasalEN.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobArray,
            profile = oapsProfile,
            enConfig = enConfig,
            autosens_data = autosensResult,
            meal_data = mealData,
            microBolusAllowed = microBolusAllowed,
            currentTime = now,
            flatBGsDetected = flatBGsDetected,
            dynIsfMode = dynIsfMode && dynIsfResult.tddPartsCalculated()
        ).also {
            val determineBasalResult = apsResultProvider.get().with(it)
            // Preserve input data
            determineBasalResult.inputConstraints = inputConstraints
            determineBasalResult.autosensResult = autosensResult
            determineBasalResult.iobData = iobArray
            determineBasalResult.glucoseStatus = glucoseStatus
            determineBasalResult.currentTemp = currentTemp
            determineBasalResult.oapsProfile = oapsProfile
            determineBasalResult.mealData = mealData
            determineBasalResult.enConfig = enConfig
            lastAPSResult = determineBasalResult
            lastAPSRun = now
            aapsLogger.debug(LTag.APS, "Result: $it")
            rxBus.send(EventAPSCalculationFinished())
        }

        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorSMB.getGlucoseStatusData(allowOldData)

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref = preferences.get(DoubleKey.ApsSmbMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobSMB(), rh.gs(R.string.limiting_iob, hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        }
        return maxIob
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        if (isEnabled()) {
            var maxBasal = preferences.get(DoubleKey.ApsMaxBasal)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(R.string.increasing_max_basal), this)
            }
            absoluteRate.setIfSmaller(maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromBasalMultiplier, rh.gs(R.string.max_basal_multiplier)),
                this
            )
            val maxBasalFromDaily = preferences.get(DoubleKey.ApsMaxDailyMultiplier)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(maxFromDaily, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromDaily, rh.gs(R.string.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseSmb)
        if (!enabled) value.set(false, rh.gs(R.string.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseUam)
        if (!enabled) value.set(false, rh.gs(R.string.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (preferences.get(BooleanKey.ApsUseDynamicSensitivity)) {
            // DynISF mode
            if (!preferences.get(BooleanKey.ApsDynIsfAdjustSensitivity))
                value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        } else {
            // SMB mode
            val enabled = preferences.get(BooleanKey.ApsUseAutosens)
            if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        }
        return value
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .put(BooleanKey.ApsUseDynamicSensitivity, preferences)
            .put(IntKey.ApsDynIsfAdjustmentFactor, preferences)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .store(BooleanKey.ApsUseDynamicSensitivity, preferences)
            .store(IntKey.ApsDynIsfAdjustmentFactor, preferences)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey !="openapssmb_settings" && requiredKey != "absorption_smb_advanced" && !requiredKey.startsWith("eating_now")) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "eatingnow_settings"
            title = rh.gs(R.string.eatingnow_plugin)
            initialExpandedChildrenCount = 0

            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "openapssmb_settings"
                title = rh.gs(R.string.openapssmb)
                summary = "OpenAPS SMB plugin settings"
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxBasal, dialogMessage = R.string.openapsma_max_basal_summary, title = R.string.openapsma_max_basal_title))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsSmbMaxIob, dialogMessage = R.string.openapssmb_max_iob_summary, title = R.string.openapssmb_max_iob_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseDynamicSensitivity, summary = R.string.use_dynamic_sensitivity_summary, title = R.string.use_dynamic_sensitivity_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseAutosens, title = R.string.openapsama_use_autosens))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsDynIsfAdjustmentFactor, dialogMessage = R.string.dyn_isf_adjust_summary, title = R.string.dyn_isf_adjust_title))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.ApsLgsThreshold, dialogMessage = R.string.lgs_threshold_summary, title = R.string.lgs_threshold_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsDynIsfAdjustSensitivity, summary = R.string.dynisf_adjust_sensitivity_summary, title = R.string.dynisf_adjust_sensitivity))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsSensitivityRaisesTarget, summary = R.string.sensitivity_raises_target_summary, title = R.string.sensitivity_raises_target_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsResistanceLowersTarget, summary = R.string.resistance_lowers_target_summary, title = R.string.resistance_lowers_target_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmb, summary = R.string.enable_smb_summary, title = R.string.enable_smb))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithHighTt, summary = R.string.enable_smb_with_high_temp_target_summary, title = R.string.enable_smb_with_high_temp_target))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbAlways, summary = R.string.enable_smb_always_summary, title = R.string.enable_smb_always))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithCob, summary = R.string.enable_smb_with_cob_summary, title = R.string.enable_smb_with_cob))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithLowTt, summary = R.string.enable_smb_with_temp_target_summary, title = R.string.enable_smb_with_temp_target))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbAfterCarbs, summary = R.string.enable_smb_after_carbs_summary, title = R.string.enable_smb_after_carbs))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsMaxSmbFrequency, title = R.string.smb_interval_summary))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsMaxMinutesOfBasalToLimitSmb, title = R.string.smb_max_minutes_summary))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsUamMaxMinutesOfBasalToLimitSmb, dialogMessage = R.string.uam_smb_max_minutes, title = R.string.uam_smb_max_minutes_summary))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseUam, summary = R.string.enable_uam_summary, title = R.string.enable_uam))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsCarbsRequestThreshold, dialogMessage = R.string.carbs_req_threshold_summary, title = R.string.carbs_req_threshold))
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "absorption_smb_advanced"
                    title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                    addPreference(
                        AdaptiveIntentPreference(
                            ctx = context,
                            intentKey = IntentKey.ApsLinkToDocs,
                            intent = Intent().apply { action = Intent.ACTION_VIEW; data = rh.gs(R.string.openapsama_link_to_preference_json_doc).toUri() },
                            summary = R.string.openapsama_link_to_preference_json_doc_txt
                        )
                    )
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAlwaysUseShortDeltas, summary = R.string.always_use_short_avg_summary, title = R.string.always_use_short_avg))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxDailyMultiplier, dialogMessage = R.string.openapsama_max_daily_safety_multiplier_summary, title = R.string.openapsama_max_daily_safety_multiplier))
                    addPreference(
                        AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxCurrentBasalMultiplier, dialogMessage = R.string.openapsama_current_basal_safety_multiplier_summary, title = R.string.openapsama_current_basal_safety_multiplier)
                    )
                })
            })

            // Eating Now General Settings
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "eating_now1"
                title = rh.gs(app.aaps.core.ui.R.string.en_pref_general_title)
                summary = "General settings for Eating Now"
                addPreference(androidx.preference.Preference(context).apply {
                    title = rh.gs(app.aaps.core.ui.R.string.en_pref_general_title)
                    summary = "Eating Now operates within the Start and End time specified.\nLarger boluses from the Eating Now Window are allowed within this time range."
                    isSelectable = false
                })
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.Eatingnow_timestart, dialogMessage = R.string.eatingnow_timestart_summary, title = R.string.eatingnow_timestart_title))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.Eatingnow_timeend, dialogMessage = R.string.eatingnow_timeend_summary, title = R.string.eatingnow_timeend_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.EatingNow_AutoStart, summary = R.string.EatingNow_AutoStart_summary, title = R.string.EatingNow_AutoStart_title))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_overnightSMB, dialogMessage = R.string.eatingnow_overnightSMB_summary, title = R.string.eatingnow_overnightSMB_title))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.highBGthreshold, dialogMessage = R.string.eatingnow_highBGthreshold_summary, title = R.string.eatingnow_highBGthreshold_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.EatingNow_IgnoreCOB, summary = R.string.EatingNow_IgnoreCOB_summary, title = R.string.EatingNow_IgnoreCOB_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.EatingNow_UseISFscaler, summary = R.string.EatingNow_useISFscaler_summary, title = R.string.EatingNow_useISFscaler_title))

            })

            // addPreference(preferenceManager.createPreferenceScreen(context).apply {
            //     key = "eating_now2"
            //     title = rh.gs(app.aaps.core.ui.R.string.en_pref_uam_plus_title)
            //     addPreference(
            //         AdaptiveIntentPreference(
            //             ctx = context,
            //             intentKey = IntentKey.ApsLinkToDocs,
            //             intent = Intent().apply { action = Intent.ACTION_VIEW; data = Uri.parse(rh.gs(R.string.openapsama_link_to_preference_json_doc)) },
            //             summary = R.string.openapsama_link_to_preference_json_doc_txt
            //         )
            //     )
            //     addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAlwaysUseShortDeltas, summary = R.string.always_use_short_avg_summary, title = R.string.always_use_short_avg))
            //     addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxDailyMultiplier, dialogMessage = R.string.openapsama_max_daily_safety_multiplier_summary, title = R.string.openapsama_max_daily_safety_multiplier))
            //     addPreference(
            //         AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxCurrentBasalMultiplier, dialogMessage = R.string.openapsama_current_basal_safety_multiplier_summary, title = R.string.openapsama_current_basal_safety_multiplier)
            //     )
            // })

            // Eating Now Window menu options
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "eating_now3"
                title = "Eating Now Window"
                summary = "The EN Window is activated when there is a manual bolus,carb entry or EN temptarget."
                addPreference(androidx.preference.Preference(context).apply {
                    title = "Eating Now Window"
                    summary = "The EN Window is activated when there is a manual bolus,carb entry or EN temptarget."
                    isSelectable = false
                })
                // Eating Now Window submenu options
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "eating_now3a"
                    title = "ENW Settings"
                    summary = "ENW Settings for when there is an active ENW running."
                    addPreference(androidx.preference.Preference(context).apply {
                        title = "ENW Settings"
                        summary = "ENW Settings for when there is an active ENW running."
                        isSelectable = false
                    })
                    addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.Eatingnow_enw_minutes, dialogMessage = R.string.Eatingnow_enw_minutes_summary, title = R.string.Eatingnow_enw_minutes_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_cob_maxbolus, dialogMessage = R.string.Eatingnow_enw_cob_maxbolus_summary, title = R.string.Eatingnow_enw_cob_maxbolus_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_uam_maxbolus, dialogMessage = R.string.Eatingnow_enw_uam_maxbolus_summary, title = R.string.Eatingnow_enw_uam_maxbolus_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_uamplus_maxbolus, dialogMessage = R.string.Eatingnow_enw_uamplus_maxbolus_summary, title = R.string.Eatingnow_enw_uamplus_maxbolus_title))
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.EatingNow_OverrideENWNetIOBMax, summary = R.string.EatingNow_OverrideENWNetIOBMax_summary, title = R.string.EatingNow_OverrideENWNetIOBMax_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_maxiob, dialogMessage = R.string.Eatingnow_enw_maxiob_summary, title = R.string.Eatingnow_enw_maxiob_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_prebolus, dialogMessage = R.string.Eatingnow_enw_prebolus_summary, title = R.string.Eatingnow_enw_prebolus_title))
                    addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.enwProfileScalePct, dialogMessage = R.string.enwProfileScalePct_summary, title = R.string.enwProfileScalePct_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_triggerbolus, dialogMessage = R.string.Eatingnow_enw_triggerbolus_summary, title = R.string.Eatingnow_enw_triggerbolus_title))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Eatingnow_enw_triggercob, dialogMessage = R.string.Eatingnow_enw_triggercob_summary, title = R.string.Eatingnow_enw_triggercob_title))

                })
            })

            // addPreference(preferenceManager.createPreferenceScreen(context).apply {
            //     key = "eating_now4"
            //     title = rh.gs(app.aaps.core.ui.R.string.en_pref_isf_title)
            //     addPreference(androidx.preference.Preference(context).apply {
            //         summary = "These settings are for sensitivity within Eating Now only"
            //         isSelectable = false
            //     })
            //     // ISF Settings may not be as relevant with dynISF in the code
            // })
        }
    }
}