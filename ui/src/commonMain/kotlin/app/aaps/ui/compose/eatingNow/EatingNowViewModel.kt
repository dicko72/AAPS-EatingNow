package app.aaps.ui.compose.eatingNow

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.isNotDeliveryError
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.failText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * ViewModel for the Eating Now entry. It writes an Eating Now temp target through the Client-Control
 * batch channel (same two-step prepare → confirm → commit as the other treatment dialogs). The prebolus
 * amount and ENW-IOB limit are NOT delivered here: they are saved as preferences on a successful commit,
 * and the Eating Now plugin reads the `_PB` temp target later to deliver the prebolus.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class EatingNowViewModel(
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val batchExecutor: BatchExecutor,
    private val hardLimits: HardLimits,
    val rh: TextResolver,
    val dateUtil: DateUtil,
    private val config: Config,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    // Unqualified: @ApplicationScope is a javax qualifier and cannot appear in commonMain. The graph
    // binds the same instance under both names.
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(EatingNowUiState())
    val uiState: StateFlow<EatingNowUiState> = _uiState.asStateFlow()

    sealed class SideEffect {

        /** The master prepared the batch and returned its merged confirmation [lines]; show them, then [commit] [id]. */
        data class ShowConfirmation(val id: Long, val lines: List<ConfirmationLine>) : SideEffect()

        /** A plain message to show in a dialog (e.g. the prebolus-over-limit warning). */
        data class ShowMessage(val message: String) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        viewModelScope.launch { loadInitial() }
    }

    private suspend fun loadInitial() {
        try {
            val units = profileUtil.units
            val profile = profileFunction.getProfile()
            val targetMgdl = profile?.getTargetMgdl() ?: 100.0
            val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
            val hasActiveTT = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) != null

            _uiState.update {
                EatingNowUiState(
                    units = units,
                    target = roundForDisplay(profileUtil.fromMgdlToUnits(targetMgdl, units), units),
                    durationMinutes = preferences.get(IntKey.Eatingnow_enw_minutes),
                    enwIob = preferences.get(DoubleKey.Eatingnow_enw_maxiob),
                    maxIob = hardLimits.maxIobSMB(),
                    prebolusChecked = false,
                    prebolus = 0.0,
                    maxPrebolus = Constants.MAX_EN_PREBOLUS,
                    bolusStep = bolusStep,
                    hasActiveTT = hasActiveTT,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to load Eating Now defaults", e)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Round a target to what the user can actually see: 0.1 mmol/L, or 1 mg/dL. */
    private fun roundForDisplay(value: Double, units: GlucoseUnit): Double = when (units) {
        GlucoseUnit.MMOL -> (value * 10).roundToInt() / 10.0
        GlucoseUnit.MGDL -> value.roundToInt().toDouble()
    }

    fun updateTarget(target: Double) {
        _uiState.update { it.copy(target = target) }
    }

    fun updateDuration(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes.coerceIn(0, Constants.MAX_ENTT_DURATION.toInt())) }
    }

    fun updateEnwIob(value: Double) {
        _uiState.update { it.copy(enwIob = value.coerceIn(0.0, it.maxIob)) }
    }

    fun updatePrebolusChecked(checked: Boolean) {
        _uiState.update {
            it.copy(
                prebolusChecked = checked,
                // Default the amount to the saved prebolus when ticking on; clear it when ticking off.
                prebolus = if (checked) preferences.get(DoubleKey.Eatingnow_enw_prebolus) else 0.0
            )
        }
    }

    fun updatePrebolus(value: Double) {
        _uiState.update { it.copy(prebolus = value.coerceIn(0.0, it.maxPrebolus)) }
    }

    @Volatile private var confirmedState: EatingNowUiState? = null

    /**
     * Tap-confirm → ask the master to PREPARE the temp-target batch (and return the merged confirmation lines).
     * A target or duration of 0 cancels the current temp target instead. The prebolus must not exceed the
     * ENW-IOB limit; if it does, nothing is prepared and a warning is shown.
     */
    fun prepareAndConfirm() {
        appScope.launch {
            val state = uiState.value
            confirmedState = state

            val roundedPrebolus = Round.roundTo(state.prebolus, 0.01)
            if (state.prebolusChecked && roundedPrebolus > state.enwIob) {
                _sideEffect.tryEmit(SideEffect.ShowMessage(rh.gs(CoreUiStrings.eatingnow_prebolus_exceeds_iob)))
                return@launch
            }

            val label = rh.gs(CoreUiStrings.eatingnow)
            val action = if (isCancelling(state)) {
                // Cancel the running temp target — a duration-0 action, as the TT dialog does.
                BatchAction.TempTarget(reason = TT.Reason.CUSTOM.text, lowMgdl = 0.0, highMgdl = 0.0, durationMinutes = 0, startOffsetMinutes = 0)
            } else {
                val reason = if (state.prebolusChecked) TT.Reason.EATING_NOW_PB else TT.Reason.EATING_NOW
                val mgdl = profileUtil.convertToMgdl(state.target, state.units)
                BatchAction.TempTarget(reason = reason.text, lowMgdl = mgdl, highMgdl = mgdl, durationMinutes = state.durationMinutes, startOffsetMinutes = 0)
            }

            when (val prepared = batchExecutor.prepare(listOf(action), Sources.TTDialog, label)) {
                is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines + enwConfirmLines(state)))
                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason.isNotDeliveryError())
                        rxBus.send(EventShowDialog.Ok(title = label, message = prepared.detail ?: rh.gs(prepared.reason.failText())))

                else                       -> Unit
            }
        }
    }

    /**
     * Confirm the master's prepared batch. On a successful set (not a cancel) the Eating Now preferences are
     * saved, mirroring the old dialog: the prebolus (when ticked), the ENW-IOB limit, and the duration.
     * [onSuccess] runs on the main thread only after the commit actually applies, so the screen closes on a
     * real write, not when the dialog appears.
     */
    fun commit(id: Long, onSuccess: () -> Unit) {
        appScope.launch {
            val state = confirmedState ?: uiState.value
            val label = rh.gs(CoreUiStrings.eatingnow)
            when (val result = batchExecutor.commit(id, Sources.TTDialog, label)) {
                is ActionProgress.Applied  -> {
                    if (state.durationMinutes == 10) preferences.put(BooleanNonKey.ObjectivesTempTargetUsed, true)
                    // Save the preferences only on a real set, never on a cancel.
                    if (!isCancelling(state)) {
                        if (state.prebolusChecked) preferences.put(DoubleKey.Eatingnow_enw_prebolus, Round.roundTo(state.prebolus, 0.01))
                        preferences.put(DoubleKey.Eatingnow_enw_maxiob, state.enwIob)
                        preferences.put(IntKey.Eatingnow_enw_minutes, state.durationMinutes)
                    }
                    withContext(Dispatchers.Main) { onSuccess() }
                }

                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || result.reason.isNotDeliveryError())
                        rxBus.send(EventShowDialog.Ok(title = label, message = result.detail ?: rh.gs(result.reason.failText())))

                else                       -> Unit
            }
        }
    }

    /**
     * Stop the running temp target. Sets the duration to 0 (which [prepareAndConfirm] reads as a cancel) and
     * runs the same prepare → confirm flow, so the user still sees the master's confirmation.
     */
    fun requestCancel() {
        _uiState.update { it.copy(durationMinutes = 0) }
        prepareAndConfirm()
    }

    /**
     * Extra confirmation lines for the Eating Now window: a blank line, then the prebolus (when ticked), then the
     * ENW-IOB limit being set. Empty on a cancel, where there is nothing to prebolus for.
     */
    private fun enwConfirmLines(state: EatingNowUiState): List<ConfirmationLine> {
        if (isCancelling(state)) return emptyList()
        return buildList {
            add(ConfirmationLine(ConfirmationRole.NORMAL, ""))
            if (state.prebolusChecked)
                add(
                    ConfirmationLine(
                        ConfirmationRole.BOLUS,
                        rh.gs(InterfacesStrings.confirmation_line, rh.gs(CoreUiStrings.prebolus_label), rh.gs(InterfacesStrings.format_insulin_units, Round.roundTo(state.prebolus, 0.01)))
                    )
                )
            add(
                ConfirmationLine(
                    ConfirmationRole.INFO,
                    rh.gs(InterfacesStrings.confirmation_line, rh.gs(CoreUiStrings.eatingnow_enw_iob), rh.gs(InterfacesStrings.format_insulin_units, state.enwIob))
                )
            )
        }
    }

    /** A target or duration of 0 means "stop the current target" rather than "set an Eating Now target". */
    private fun isCancelling(state: EatingNowUiState): Boolean = state.target == 0.0 || state.durationMinutes == 0
}
