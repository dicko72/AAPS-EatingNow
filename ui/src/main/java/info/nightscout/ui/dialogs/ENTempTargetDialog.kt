package info.nightscout.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogEnTemptargetBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ENTempTargetDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck

    private lateinit var reasonList: List<String>

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogEnTemptargetBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("tempTarget", binding.temptarget.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogEnTemptargetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val units = profileUtil.units
        binding.units.text = if (units == GlucoseUnit.MMOL) rh.gs(info.nightscout.core.ui.R.string.mmol) else rh.gs(info.nightscout.core.ui.R.string.mgdl)

        // set the Eating Now defaults
        // val enTT = profileUtil.convertToMgdl(profileFunction.getProfile()!!.getTargetMgdl(), units)
        //val enTT = profile.toCurrentUnits(units,profileFunction.getProfile()!!.getTargetMgdl())
        val enTT = profileUtil.valueInCurrentUnitsDetect(profileFunction.getProfile()!!.getTargetMgdl())

        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
            ?: 0.0, 0.0, Constants.MAX_ENTT_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok)

        if (profileFunction.getUnits() == GlucoseUnit.MMOL)
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: enTT,
                Constants.MIN_TT_MMOL, enTT, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok)
        else
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: enTT,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, binding.okcancel.ok)

        // temp target
        context?.let { context ->
            if (repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing) {
                binding.targetCancel.visibility = View.VISIBLE
                binding.prebolus.visibility = View.GONE // no prebolus checkbox when cancelling TT
            } else
                binding.targetCancel.visibility = View.GONE

            reasonList = Lists.newArrayList(
                rh.gs(info.nightscout.core.ui.R.string.eatingnow),
                rh.gs(info.nightscout.core.ui.R.string.eatingnow_prebolus)
            )
            binding.reasonList.setAdapter(ArrayAdapter(context, info.nightscout.core.ui.R.layout.spinner_centered, reasonList))

            binding.targetCancel.setOnClickListener { binding.duration.value = 0.0; shortClick(it) }
            binding.durationLabel.labelFor = binding.duration.editTextId
            binding.temptargetLabel.labelFor = binding.temptarget.editTextId
        }

        // reset to Eating Now defaults
        binding.duration.value = defaultValueHelper.determineEatingNowTTDuration().toDouble()
        binding.reasonList.setText(rh.gs(info.nightscout.core.ui.R.string.eatingnow), false)
        // binding.prebolus.isChecked = false

        // when the prebolus button is pressed
        binding.prebolus.setOnClickListener {
            if (binding.prebolus.isChecked) binding.reasonList.setText(rh.gs(info.nightscout.core.ui.R.string.eatingnow_prebolus), false)
            else binding.reasonList.setText(rh.gs(info.nightscout.core.ui.R.string.eatingnow), false)
        }
    }

    private fun shortClick(v: View) {
        v.performLongClick()
        if (submit()) dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val actions: LinkedList<String> = LinkedList()
        var reason = binding.reasonList.text.toString()
        val unitResId = if (profileFunction.getUnits() == GlucoseUnit.MGDL) info.nightscout.core.ui.R.string.mgdl else info.nightscout.core.ui.R.string.mmol
        val target = binding.temptarget.value
        val duration = binding.duration.value.toInt()
        if (target != 0.0 && duration != 0) {
            actions.add(rh.gs(info.nightscout.core.ui.R.string.reason) + ": " + reason)
            actions.add(rh.gs(info.nightscout.core.ui.R.string.target_label) + ": " + profileUtil.stringInCurrentUnitsDetect(target) + " " + rh.gs(unitResId))
            actions.add(rh.gs(info.nightscout.core.ui.R.string.duration) + ": " + rh.gs(info.nightscout.core.ui.R.string.format_mins, duration))
        } else {
            actions.add(rh.gs(info.nightscout.core.ui.R.string.stoptemptarget))
            reason = rh.gs(info.nightscout.core.ui.R.string.stoptemptarget)
        }
        if (eventTimeChanged)
            actions.add(rh.gs(info.nightscout.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.temporary_target), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                val units = profileFunction.getUnits()
                when (reason) {
                    rh.gs(info.nightscout.core.ui.R.string.eatingnow)     -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.EATING_NOW
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(info.nightscout.core.ui.R.string.eatingnow_prebolus)     -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.EATING_NOW_PB
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(info.nightscout.core.ui.R.string.stoptemptarget) -> uel.log(UserEntry.Action.CANCEL_TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged })
                }
                if (target == 0.0 || duration == 0) {
                    disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(eventTime))
                        .subscribe({ result ->
                                       result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                   }, {
                                       aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                   })
                } else {
                    disposable += repository.runTransactionForResult(
                        InsertAndCancelCurrentTemporaryTargetTransaction(
                            timestamp = eventTime,
                            duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                            reason = when (reason) {
                                rh.gs(info.nightscout.core.ui.R.string.eatingnow) -> TemporaryTarget.Reason.EATING_NOW
                                rh.gs(info.nightscout.core.ui.R.string.eatingnow_prebolus) -> TemporaryTarget.Reason.EATING_NOW_PB
                                else                                                 -> TemporaryTarget.Reason.CUSTOM
                            },
                            lowTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits()),
                            highTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits())
                        )
                    ).subscribe({ result ->
                                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                }, {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                })
                }

                if (duration == 10) sp.putBoolean(info.nightscout.core.utils.R.string.key_objectiveusetemptarget, true)
            })
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}