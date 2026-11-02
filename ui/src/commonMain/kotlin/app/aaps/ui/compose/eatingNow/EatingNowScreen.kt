package app.aaps.ui.compose.eatingNow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LabeledSwitch
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.dialogs.ElementConfirmationDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

@Composable
fun EatingNowScreen(
    viewModel: EatingNowViewModel = metroViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The master's prepared confirmation (id + its merged lines), set via the ShowConfirmation side effect.
    var confirmation by remember { mutableStateOf<Pair<Long, List<ConfirmationLine>>?>(null) }
    // A plain warning message (e.g. prebolus over the ENW-IOB limit).
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is EatingNowViewModel.SideEffect.ShowConfirmation -> confirmation = effect.id to effect.lines
                is EatingNowViewModel.SideEffect.ShowMessage      -> message = effect.message
            }
        }
    }

    confirmation?.let { (id, lines) ->
        ElementConfirmationDialog(
            elementType = ElementType.EATING_NOW,
            lines = lines,
            onConfirm = {
                confirmation = null
                viewModel.commit(id) { onNavigateBack() }
            },
            onDismiss = { confirmation = null }
        )
    }

    message?.let { text ->
        ElementConfirmationDialog(
            elementType = ElementType.EATING_NOW,
            message = text,
            onConfirm = { message = null },
            onDismiss = { message = null }
        )
    }

    EatingNowContent(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onDurationChange = { viewModel.updateDuration(it.toInt()) },
        onEnwIobChange = viewModel::updateEnwIob,
        onPrebolusCheckedChange = viewModel::updatePrebolusChecked,
        onPrebolusChange = viewModel::updatePrebolus,
        onNavigateBack = onNavigateBack,
        onConfirmClick = viewModel::prepareAndConfirm,
        onCancelTargetClick = viewModel::requestCancel
    )
}

@Composable
internal fun EatingNowContent(
    uiState: EatingNowUiState,
    onTargetChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onEnwIobChange: (Double) -> Unit,
    onPrebolusCheckedChange: (Boolean) -> Unit,
    onPrebolusChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit,
    onCancelTargetClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    val (targetRange, targetStep) = when (uiState.units) {
        GlucoseUnit.MGDL -> Constants.TT_RANGE_MGDL to 1.0
        GlucoseUnit.MMOL -> Constants.TT_RANGE_MMOL to 0.1
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResourceOrNull(ElementType.EATING_NOW.label()) ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(CoreUiStrings.close)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onConfirmClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bottomBarSafeArea()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(CoreUiStrings.ok))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeOverscroll()
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Target
                    NumberInputRow(
                        labelRef = CoreUiStrings.temporary_target,
                        value = uiState.target,
                        onValueChange = onTargetChange,
                        valueRange = targetRange,
                        step = targetStep,
                        valueFormat = if (uiState.units == GlucoseUnit.MGDL) NumberFormat.INTEGER else NumberFormat.DECIMAL_1,
                        unitLabel = TextRef.Literal(uiState.units.displayLabel),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Duration
                    NumberInputRow(
                        labelRef = CoreUiStrings.duration,
                        value = uiState.durationMinutes.toDouble(),
                        onValueChange = onDurationChange,
                        valueRange = 0.0..Constants.MAX_ENTT_DURATION,
                        step = 1.0,
                        valueFormat = NumberFormat.INTEGER,
                        unitLabel = CoreUiStrings.units_min,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ENW-IOB limit
                    NumberInputRow(
                        labelRef = CoreUiStrings.eatingnow_enw_iob_label,
                        value = uiState.enwIob,
                        onValueChange = onEnwIobChange,
                        valueRange = 0.0..uiState.maxIob,
                        step = uiState.bolusStep,
                        decimalPlaces = 2,
                        unitLabel = CoreUiStrings.insulin_unit_shortname,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Prebolus — hidden while a temp target is already running (there is nothing to prebolus for
                    // when cancelling).
                    if (!uiState.hasActiveTT) {
                        LabeledSwitch(
                            label = stringResource(CoreUiStrings.eatingnow_use_prebolus),
                            checked = uiState.prebolusChecked,
                            onCheckedChange = onPrebolusCheckedChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (uiState.prebolusChecked) {
                            NumberInputRow(
                                labelRef = CoreUiStrings.eatingnow_prebolus_amount,
                                value = uiState.prebolus,
                                onValueChange = onPrebolusChange,
                                valueRange = 0.0..uiState.maxPrebolus,
                                step = uiState.bolusStep,
                                decimalPlaces = 2,
                                unitLabel = CoreUiStrings.insulin_unit_shortname,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Stop the running target (shown only when one is active).
            if (uiState.hasActiveTT) {
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        onCancelTargetClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(CoreUiStrings.eatingnow_stop))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
