package com.young.metaboliccoach

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.young.metaboliccoach.core.data.provider.HealthConnectPermissions
import com.young.metaboliccoach.core.domain.CoachSettingsBounds
import com.young.metaboliccoach.core.domain.GlycemicPlannerBounds
import com.young.metaboliccoach.core.domain.temporalState
import com.young.metaboliccoach.core.domain.NightscoutSettingsBounds
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.CoachTheme
import com.young.metaboliccoach.core.model.GlucoseDataOrigin
import com.young.metaboliccoach.core.model.GlucoseHistoryBackfillStatus
import com.young.metaboliccoach.core.model.GlucoseHistoryRetentionPolicy
import com.young.metaboliccoach.core.model.GlucoseHistorySettings
import com.young.metaboliccoach.core.model.GlucoseHistoryStatus
import com.young.metaboliccoach.core.model.GlucoseChartResult
import com.young.metaboliccoach.core.model.GlucoseChartStatus
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseUnit
import com.young.metaboliccoach.core.model.GlycemicMetricsStatus
import com.young.metaboliccoach.core.model.GlycemicGoalScenario
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestone
import com.young.metaboliccoach.core.model.GlycemicPlanningMilestoneEvaluation
import com.young.metaboliccoach.core.model.GlycemicScenarioStatus
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.HistoryRange
import com.young.metaboliccoach.core.model.MilestoneEvaluationState
import com.young.metaboliccoach.core.model.MilestoneLifecycleState
import com.young.metaboliccoach.core.model.MilestoneTemporalState
import com.young.metaboliccoach.core.model.RollingGlycemicMetrics
import com.young.metaboliccoach.core.model.SelectedPeriodGmiAvailability
import com.young.metaboliccoach.core.model.SelectedPeriodGmiQualifier
import com.young.metaboliccoach.core.model.SelectedPeriodGmiResult
import com.young.metaboliccoach.core.model.InterventionSession
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.NightscoutServerConfig
import com.young.metaboliccoach.core.model.NightscoutSettings
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.ui.PhoneUiState
import com.young.metaboliccoach.ui.PhoneViewModel
import com.young.metaboliccoach.ui.HistoryExplorerLoadStatus
import com.young.metaboliccoach.ui.HistoryExplorerUiState
import com.young.metaboliccoach.ui.HistoryViewport
import com.young.metaboliccoach.ui.HistoryViewportLoadStatus
import com.young.metaboliccoach.ui.HistoryViewportMath
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: PhoneViewModel by viewModels()
    private val healthConnectSdkStatus = mutableIntStateOf(HealthConnectClient.SDK_UNAVAILABLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val healthPermissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { viewModel.onHealthPermissionsChanged() }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.refresh() }
            val personalDataExportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                uri?.let(viewModel::exportData)
            }

            MetabolicCoachTheme(uiState.settings) {
                MetabolicCoachApp(
                    uiState = uiState,
                    healthConnectSdkStatus = healthConnectSdkStatus.intValue,
                    onRefresh = viewModel::refresh,
                    onMarkMeal = viewModel::markMeal,
                    onQuickAction = viewModel::quickAction,
                    onSaveSettings = viewModel::saveSettings,
                    onSaveGlycemicPlannerSafetySettings = viewModel::saveGlycemicPlannerSafetySettings,
                    onCreatePlanningMilestone = viewModel::createPlanningMilestone,
                    onUpdatePlanningMilestone = viewModel::updatePlanningMilestone,
                    onSelectPlanningMilestone = viewModel::selectPlanningMilestone,
                    onArchivePlanningMilestone = viewModel::archivePlanningMilestone,
                    onDeletePlanningMilestone = viewModel::deletePlanningMilestone,
                    onDismissMilestoneMigrationNotice = viewModel::dismissMilestoneMigrationNotice,
                    onExportData = {
                        personalDataExportLauncher.launch(
                            "metabolic-coach-export-${LocalDate.now()}.json",
                        )
                    },
                    onEraseData = viewModel::eraseLocalData,
                    onSaveGlucoseHistorySettings = viewModel::saveGlucoseHistorySettings,
                    onConfirmGlucoseHistoryRetention = viewModel::confirmGlucoseHistoryRetention,
                    onBackfillGlucoseHistory = viewModel::backfillGlucoseHistory,
                    onHistoryVisibilityChanged = viewModel::setHistoryVisible,
                    onSelectHistoryPreset = viewModel::selectHistoryPreset,
                    onCustomHistoryStartChanged = viewModel::updateCustomHistoryStartDate,
                    onCustomHistoryEndChanged = viewModel::updateCustomHistoryEndDate,
                    onApplyCustomHistoryRange = viewModel::applyCustomHistoryRange,
                    onTransformHistoryViewport = viewModel::transformHistoryViewport,
                    onZoomInHistoryViewport = viewModel::zoomInHistoryViewport,
                    onZoomOutHistoryViewport = viewModel::zoomOutHistoryViewport,
                    onResetHistoryViewport = viewModel::resetHistoryViewport,
                    onRetryHistoryViewport = viewModel::retryHistoryViewport,
                    onConnectHealth = {
                        when (healthConnectSdkStatus.intValue) {
                            HealthConnectClient.SDK_AVAILABLE -> {
                                val permissions = HealthConnectPermissions
                                    .requestableReadPermissions(this@MainActivity)
                                if (permissions.isNotEmpty()) {
                                    healthPermissionLauncher.launch(permissions)
                                }
                            }
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                openHealthConnectStoreListing()
                            }
                        }
                    },
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        } else {
                            viewModel.refresh()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        healthConnectSdkStatus.intValue = HealthConnectClient.getSdkStatus(this)
    }

    private fun openHealthConnectStoreListing() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$HEALTH_CONNECT_PACKAGE".toUri(),
        ).setPackage("com.android.vending")
        runCatching { startActivity(marketIntent) }.getOrElse {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri(),
                ),
            )
        }
    }

    private companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}

private enum class PhoneDestination { TODAY, HISTORY, PLANNER, SETTINGS }

@Composable
private fun MetabolicCoachApp(
    uiState: PhoneUiState,
    healthConnectSdkStatus: Int,
    onRefresh: () -> Unit,
    onMarkMeal: () -> Unit,
    onQuickAction: (QuickActionType, String?) -> Unit,
    onSaveSettings: (CoachSettings, NightscoutSettings) -> Unit,
    onSaveGlycemicPlannerSafetySettings: (GlycemicPlannerSettings) -> Unit,
    onCreatePlanningMilestone: (String?, Double, GlycemicTargetProvenance, Int) -> Unit,
    onUpdatePlanningMilestone: (
        GlycemicPlanningMilestone,
        String?,
        Double,
        GlycemicTargetProvenance,
        Int,
        Long,
    ) -> Unit,
    onSelectPlanningMilestone: (String) -> Unit,
    onArchivePlanningMilestone: (String) -> Unit,
    onDeletePlanningMilestone: (String) -> Unit,
    onDismissMilestoneMigrationNotice: () -> Unit,
    onExportData: () -> Unit,
    onEraseData: () -> Unit,
    onSaveGlucoseHistorySettings: (GlucoseHistorySettings) -> Unit,
    onConfirmGlucoseHistoryRetention: () -> Unit,
    onBackfillGlucoseHistory: () -> Unit,
    onHistoryVisibilityChanged: (Boolean, String?) -> Unit,
    onSelectHistoryPreset: (HistoryPeriodPreset) -> Unit,
    onCustomHistoryStartChanged: (String) -> Unit,
    onCustomHistoryEndChanged: (String) -> Unit,
    onApplyCustomHistoryRange: () -> Unit,
    onTransformHistoryViewport: (Float, Float, Float, Float) -> Unit,
    onZoomInHistoryViewport: () -> Unit,
    onZoomOutHistoryViewport: () -> Unit,
    onResetHistoryViewport: () -> Unit,
    onRetryHistoryViewport: () -> Unit,
    onConnectHealth: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    var destination by remember { mutableStateOf(PhoneDestination.TODAY) }
    LaunchedEffect(destination, uiState.glucose?.sourceId) {
        onHistoryVisibilityChanged(
            destination == PhoneDestination.HISTORY,
            uiState.glucose?.sourceId,
        )
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == PhoneDestination.TODAY,
                    onClick = { destination = PhoneDestination.TODAY },
                    icon = { Text("●") },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = destination == PhoneDestination.HISTORY,
                    onClick = { destination = PhoneDestination.HISTORY },
                    icon = { Text("∿") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = destination == PhoneDestination.PLANNER,
                    onClick = { destination = PhoneDestination.PLANNER },
                    icon = { Text("◎") },
                    label = { Text("Planner") },
                )
                NavigationBarItem(
                    selected = destination == PhoneDestination.SETTINGS,
                    onClick = { destination = PhoneDestination.SETTINGS },
                    icon = { Text("⚙") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        when (destination) {
            PhoneDestination.TODAY -> TodayScreen(
                uiState = uiState,
                onRefresh = onRefresh,
                onMarkMeal = onMarkMeal,
                onQuickAction = onQuickAction,
                onConnectHealth = onConnectHealth,
                onEnableNotifications = onEnableNotifications,
                healthConnectSdkStatus = healthConnectSdkStatus,
                contentPadding = padding,
            )
            PhoneDestination.HISTORY -> HistoryExplorerScreen(
                state = uiState.historyExplorer,
                glucoseUnit = uiState.settings.glucoseUnit,
                onSelectPreset = onSelectHistoryPreset,
                onCustomStartChanged = onCustomHistoryStartChanged,
                onCustomEndChanged = onCustomHistoryEndChanged,
                onApplyCustomRange = onApplyCustomHistoryRange,
                onTransformViewport = onTransformHistoryViewport,
                onZoomInViewport = onZoomInHistoryViewport,
                onZoomOutViewport = onZoomOutHistoryViewport,
                onResetViewport = onResetHistoryViewport,
                onRetryViewport = onRetryHistoryViewport,
                contentPadding = padding,
            )
            PhoneDestination.SETTINGS -> SettingsScreen(
                settings = uiState.settings,
                nightscoutSettings = uiState.nightscoutSettings,
                glucoseHistory = uiState.glucoseHistory,
                operationMessage = uiState.operationMessage,
                isOperationInProgress = uiState.isOperationInProgress,
                onSave = onSaveSettings,
                onExportData = onExportData,
                onEraseData = onEraseData,
                onSaveGlucoseHistorySettings = onSaveGlucoseHistorySettings,
                onConfirmGlucoseHistoryRetention = onConfirmGlucoseHistoryRetention,
                onBackfillGlucoseHistory = onBackfillGlucoseHistory,
                contentPadding = padding,
            )
            PhoneDestination.PLANNER -> GlycemicGoalScreen(
                uiState = uiState,
                onSaveSafetySettings = onSaveGlycemicPlannerSafetySettings,
                onCreateMilestone = onCreatePlanningMilestone,
                onUpdateMilestone = onUpdatePlanningMilestone,
                onSelectMilestone = onSelectPlanningMilestone,
                onArchiveMilestone = onArchivePlanningMilestone,
                onDeleteMilestone = onDeletePlanningMilestone,
                onDismissMigrationNotice = onDismissMilestoneMigrationNotice,
                contentPadding = padding,
            )
        }
    }
}

@Composable
internal fun HistoryExplorerScreen(
    state: HistoryExplorerUiState,
    glucoseUnit: GlucoseUnit,
    onSelectPreset: (HistoryPeriodPreset) -> Unit,
    onCustomStartChanged: (String) -> Unit,
    onCustomEndChanged: (String) -> Unit,
    onApplyCustomRange: () -> Unit,
    onTransformViewport: (Float, Float, Float, Float) -> Unit,
    onZoomInViewport: () -> Unit,
    onZoomOutViewport: () -> Unit,
    onResetViewport: () -> Unit,
    onRetryViewport: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState? = null,
) {
    val resolvedListState = listState ?: rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = resolvedListState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Read-only glucose trends from this phone's local history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryPeriodPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.selectedPreset == preset,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                when (preset) {
                                    HistoryPeriodPreset.HOURS_6 -> "6h"
                                    HistoryPeriodPreset.HOURS_12 -> "12h"
                                    HistoryPeriodPreset.HOURS_24 -> "24h"
                                    HistoryPeriodPreset.DAYS_7 -> "7d"
                                    HistoryPeriodPreset.DAYS_14 -> "14d"
                                    HistoryPeriodPreset.DAYS_30 -> "30d"
                                    HistoryPeriodPreset.DAYS_90 -> "90d"
                                    HistoryPeriodPreset.CUSTOM -> "Custom"
                                },
                            )
                        },
                    )
                }
            }
        }
        if (state.selectedPreset == HistoryPeriodPreset.CUSTOM) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Completed local calendar days", fontWeight = FontWeight.Bold)
                        Text(
                            "Choose 14 to 90 days. The end date must be yesterday or earlier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.customDraft.startDateInput,
                            onValueChange = onCustomStartChanged,
                            label = { Text("Start date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.customDraft.endDateInput,
                            onValueChange = onCustomEndChanged,
                            label = { Text("End date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.customDraft.inputError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = onApplyCustomRange,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Apply custom period") }
                    }
                }
            }
        }
        item {
            when (state.loadStatus) {
                HistoryExplorerLoadStatus.LOADING -> Text("Loading local history…")
                HistoryExplorerLoadStatus.ERROR -> Text(
                    state.detail,
                    color = MaterialTheme.colorScheme.error,
                )
                HistoryExplorerLoadStatus.IDLE -> Text(
                    state.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistoryExplorerLoadStatus.READY -> Unit
            }
        }
        state.renderedViewport?.let {
            item {
                GlucoseHistoryChartCard(
                    state = state,
                    glucoseUnit = glucoseUnit,
                    onTransformViewport = onTransformViewport,
                    onZoomInViewport = onZoomInViewport,
                    onZoomOutViewport = onZoomOutViewport,
                    onResetViewport = onResetViewport,
                    onRetryViewport = onRetryViewport,
                )
            }
        }
        state.selectedPeriodGmi?.let { result ->
            item { SelectedPeriodGmiCard(result, glucoseUnit) }
        }
    }
}

@Composable
private fun GlucoseHistoryChartCard(
    state: HistoryExplorerUiState,
    glucoseUnit: GlucoseUnit,
    onTransformViewport: (Float, Float, Float, Float) -> Unit,
    onZoomInViewport: () -> Unit,
    onZoomOutViewport: () -> Unit,
    onResetViewport: () -> Unit,
    onRetryViewport: () -> Unit,
) {
    val rendered = state.renderedViewport ?: return
    val chart = rendered.chart
    val selectedRange = state.selectedRange ?: rendered.selectedRange
    val requestedViewport = state.requestedViewport ?: rendered.viewport
    val fullViewport = HistoryViewportMath.full(selectedRange)
    val canZoomIn = requestedViewport.durationMillis >
        HistoryViewportMath.MINIMUM_DURATION_MILLIS
    val canZoomOut = fullViewport != null && requestedViewport != fullViewport
    val isUpdating = state.viewportLoadStatus == HistoryViewportLoadStatus.DEBOUNCING ||
        state.viewportLoadStatus == HistoryViewportLoadStatus.LOADING
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Selected period: ${historyRangeLabel(selectedRange)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Visible chart window", style = MaterialTheme.typography.titleLarge)
            Text(historyViewportRangeLabel(rendered.viewport, selectedRange.displayTimeZoneId))
            if (isUpdating) {
                Text(
                    "Updating visible chart…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Updating history chart"
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onZoomOutViewport,
                    enabled = canZoomOut,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Zoom out history chart" },
                ) { Text("−") }
                OutlinedButton(
                    onClick = onZoomInViewport,
                    enabled = canZoomIn,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Zoom in history chart" },
                ) { Text("+") }
                OutlinedButton(
                    onClick = onResetViewport,
                    enabled = canZoomOut,
                    modifier = Modifier
                        .weight(1.4f)
                        .semantics { contentDescription = "Reset history chart" },
                ) { Text("Reset") }
            }
            if (state.viewportLoadStatus == HistoryViewportLoadStatus.ERROR) {
                Text(state.viewportDetail, color = MaterialTheme.colorScheme.error)
                TextButton(
                    onClick = onRetryViewport,
                    modifier = Modifier.semantics {
                        contentDescription = "Retry history chart"
                    },
                ) { Text("Retry chart") }
            }
            if (chart.status != GlucoseChartStatus.AVAILABLE) {
                Text(chart.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val buckets = chart.segments.flatMap { it.buckets }
            InteractiveGlucoseTrendCanvas(
                chart = chart,
                selectedRange = selectedRange,
                requestedViewport = requestedViewport,
                onTransformViewport = onTransformViewport,
            )
            val minimum = buckets.minOfOrNull { it.minimumMgDl }
            val maximum = buckets.maxOfOrNull { it.maximumMgDl }
            Text(
                "Visible range ${formatGlucose(minimum, glucoseUnit)} – " +
                    formatGlucose(maximum, glucoseUnit),
            )
            Text(
                "Visible coverage ${formatPercent(chart.coverage.coveragePercent)} • " +
                    "${chart.coverage.gapCount} gap(s) • largest " +
                    formatDuration(chart.coverage.largestGapMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chart.coverage.gapCount > 0) {
                Text(
                    "Missing periods are left disconnected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InteractiveGlucoseTrendCanvas(
    chart: GlucoseChartResult,
    selectedRange: HistoryRange,
    requestedViewport: HistoryViewport,
    onTransformViewport: (Float, Float, Float, Float) -> Unit,
) {
    var chartWidthPixels by remember(chart.sourceId, selectedRange) { mutableIntStateOf(0) }
    val canPan = requestedViewport.durationMillis < selectedRange.durationMillis
    val renderedViewport = HistoryViewport(
        chart.range.startEpochMillis,
        chart.range.endExclusiveEpochMillis,
    )
    val visibleWindowDescription = "Visible window " +
        historyViewportRangeLabel(renderedViewport, selectedRange.displayTimeZoneId) + ". " +
        if (renderedViewport.durationMillis < selectedRange.durationMillis) {
            "Chart is zoomed."
        } else {
            "Chart shows the full selected period."
        }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val width = chartWidthPixels.toFloat()
        if (width > 0f) {
            onTransformViewport(
                zoomChange,
                (centroid.x / width).coerceIn(0f, 1f),
                panChange.x,
                width,
            )
        }
    }
    GlucoseTrendCanvas(
        chart = chart,
        accessibilityContext = visibleWindowDescription,
        modifier = Modifier
            .onSizeChanged { chartWidthPixels = it.width }
            .transformable(
                state = transformState,
                canPan = { delta ->
                    canPan && HistoryViewportMath.isHorizontalIntent(
                        delta.x.toDouble(),
                        delta.y.toDouble(),
                    )
                },
                lockRotationOnZoomPan = true,
            ),
    )
}

@Composable
private fun GlucoseTrendCanvas(
    chart: GlucoseChartResult,
    accessibilityContext: String,
    modifier: Modifier = Modifier,
) {
    val buckets = chart.segments.flatMap { it.buckets }
    if (buckets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag(HISTORY_CHART_TEST_TAG)
                .semantics {
                    contentDescription = "$accessibilityContext ${chart.detail}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No local readings in this visible window.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val observedMinimum = buckets.minOf { it.minimumMgDl }
    val observedMaximum = buckets.maxOf { it.maximumMgDl }
    val padding = ((observedMaximum - observedMinimum) * 0.08).coerceAtLeast(8.0)
    val minimum = observedMinimum - padding
    val maximum = observedMaximum + padding
    val glucoseSpan = (maximum - minimum).coerceAtLeast(1.0)
    val timeSpan = chart.range.durationMillis.coerceAtLeast(1L).toDouble()
    val lineColor = MaterialTheme.colorScheme.primary
    val rangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val firstMean = buckets.first().timeWeightedMeanMgDl
    val lastMean = buckets.last().timeWeightedMeanMgDl
    val trendSummary = when {
        lastMean - firstMean > 5.0 -> "Overall displayed trend rises."
        firstMean - lastMean > 5.0 -> "Overall displayed trend falls."
        else -> "Overall displayed trend is broadly level."
    }
    val accessibility = "Glucose trend with ${chart.segments.size} disconnected segment(s), " +
        "${"%.1f".format(chart.coverage.coveragePercent)} percent coverage. " +
        "Missing periods are not connected. $trendSummary $accessibilityContext"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag(HISTORY_CHART_TEST_TAG)
            .semantics { contentDescription = accessibility }
            .drawWithCache {
                fun x(epochMillis: Long): Float = (
                    (epochMillis - chart.range.startEpochMillis) / timeSpan * size.width
                    ).toFloat().coerceIn(0f, size.width)
                fun y(valueMgDl: Double): Float = (
                    size.height - ((valueMgDl - minimum) / glucoseSpan * size.height)
                    ).toFloat().coerceIn(0f, size.height)

                val cachedSegments = chart.segments.map { segment ->
                    val path = Path()
                    val ranges = segment.buckets.mapIndexed { index, bucket ->
                        val center = bucket.startEpochMillis +
                            (bucket.endExclusiveEpochMillis - bucket.startEpochMillis) / 2L
                        val pointX = x(center)
                        val pointY = y(bucket.timeWeightedMeanMgDl)
                        if (index == 0) {
                            path.moveTo(pointX, pointY)
                        } else {
                            path.lineTo(pointX, pointY)
                        }
                        Offset(pointX, y(bucket.minimumMgDl)) to
                            Offset(pointX, y(bucket.maximumMgDl))
                    }
                    path to ranges
                }
                onDrawBehind {
                    drawLine(
                        axisColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                    )
                    cachedSegments.forEach { (path, ranges) ->
                        ranges.forEach { (start, end) ->
                            drawLine(
                                color = rangeColor,
                                start = start,
                                end = end,
                                strokeWidth = 2f,
                                cap = StrokeCap.Round,
                            )
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round),
                        )
                    }
                }
            },
    )
}

internal const val HISTORY_CHART_TEST_TAG = "history-glucose-chart"

@Composable
private fun SelectedPeriodGmiCard(
    result: SelectedPeriodGmiResult,
    glucoseUnit: GlucoseUnit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Selected-period GMI", style = MaterialTheme.typography.titleLarge)
            Text(historyDateRange(result.range), style = MaterialTheme.typography.bodySmall)
            if (result.availability == SelectedPeriodGmiAvailability.AVAILABLE) {
                Text(
                    "${formatPercent(result.gmiPercent)} GMI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Time-weighted mean: ${formatGlucose(result.timeWeightedMeanMgDl, glucoseUnit)}")
                Text(
                    "Coverage ${formatPercent(result.coveragePercent)} • " +
                        "TIR ${formatPercent(result.timeInRangePercent)} • " +
                        "TBR ${formatPercent(result.timeBelowRangePercent)}",
                )
                Text(
                    "Missing ${formatDuration(result.missingDurationMillis)} • " +
                        "largest gap ${formatDuration(result.largestGapMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (SelectedPeriodGmiQualifier.LOW_GLUCOSE_EXPOSURE in result.qualifiers) {
                    Text(
                        "This period includes low-glucose exposure. Review the safety metrics " +
                            "without treating a lower GMI as a positive result by itself.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (SelectedPeriodGmiQualifier.CONCENTRATED_GAPS in result.qualifiers) {
                    Text("Coverage includes a concentrated missing-data period.")
                }
                if (SelectedPeriodGmiQualifier.PARTIAL_LATEST_DAY in result.qualifiers) {
                    Text("This rolling period includes part of the current local day.")
                }
            } else {
                Text(gmiAvailabilityLabel(result.availability), fontWeight = FontWeight.Bold)
                Text(result.detail)
                Text("Coverage: ${formatPercent(result.coveragePercent)}")
            }
            Text(
                "Selected-period GMI is calculated from CGM mean glucose. It is not a " +
                    "laboratory HbA1c measurement and may differ from one. Missing data can " +
                    "affect the result.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun historyRangeLabel(range: HistoryRange): String = when (range.preset) {
    HistoryPeriodPreset.HOURS_6 -> "Last 6 hours"
    HistoryPeriodPreset.HOURS_12 -> "Last 12 hours"
    HistoryPeriodPreset.HOURS_24 -> "Last 24 hours"
    HistoryPeriodPreset.DAYS_7 -> "Last 7 days"
    HistoryPeriodPreset.DAYS_14 -> "Last 14 days"
    HistoryPeriodPreset.DAYS_30 -> "Last 30 days"
    HistoryPeriodPreset.DAYS_90 -> "Last 90 days"
    HistoryPeriodPreset.CUSTOM -> "Custom ${range.calendarDayCount}-day period"
}

private fun historyViewportRangeLabel(viewport: HistoryViewport, displayTimeZoneId: String): String {
    val zone = ZoneId.of(displayTimeZoneId)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val start = Instant.ofEpochMilli(viewport.startEpochMillis).atZone(zone).format(formatter)
    val end = Instant.ofEpochMilli(viewport.endExclusiveEpochMillis).atZone(zone).format(formatter)
    return "$start to $end"
}

private fun historyDateRange(range: HistoryRange): String {
    val zone = ZoneId.of(range.displayTimeZoneId)
    val start = Instant.ofEpochMilli(range.startEpochMillis).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(range.endExclusiveEpochMillis - 1L).atZone(zone).toLocalDate()
    return "$start to $end"
}

private fun gmiAvailabilityLabel(availability: SelectedPeriodGmiAvailability): String =
    when (availability) {
        SelectedPeriodGmiAvailability.AVAILABLE -> "Available"
        SelectedPeriodGmiAvailability.INSUFFICIENT_DURATION -> "GMI requires at least 14 days"
        SelectedPeriodGmiAvailability.INSUFFICIENT_COVERAGE -> "Not enough local coverage"
        SelectedPeriodGmiAvailability.NO_DATA -> "No local data"
        SelectedPeriodGmiAvailability.SOURCE_DISCONTINUITY -> "Source discontinuity"
        SelectedPeriodGmiAvailability.INVALID_RANGE -> "Invalid period"
        SelectedPeriodGmiAvailability.CALCULATION_ERROR -> "Calculation unavailable"
    }

@Composable
private fun TodayScreen(
    uiState: PhoneUiState,
    onRefresh: () -> Unit,
    onMarkMeal: () -> Unit,
    onQuickAction: (QuickActionType, String?) -> Unit,
    onConnectHealth: () -> Unit,
    onEnableNotifications: () -> Unit,
    healthConnectSdkStatus: Int,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Metabolic Coach", style = MaterialTheme.typography.headlineMedium)
            Text(
                "What is the best action I can take right now?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.operationMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { GlucoseCard(uiState) }
        uiState.activeSession?.let { session ->
            item {
                ActiveSessionCard(
                    session = session,
                    nowEpochMillis = uiState.nowEpochMillis,
                    onComplete = {
                        onQuickAction(QuickActionType.MARK_COMPLETED, session.id)
                    },
                )
            }
        }
        item {
            RecommendationCard(
                recommendation = uiState.recommendation,
                onQuickAction = onQuickAction,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onMarkMeal, modifier = Modifier.weight(1f)) {
                    Text("Mark meal")
                }
                Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("Refresh")
                }
            }
        }
        item {
            val status = uiState.providerStatus
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(status?.displayName ?: "Glucose provider", fontWeight = FontWeight.Bold)
                    Text(
                        status?.detail ?: "Provider status is loading.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Samsung Health activity is read through Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onConnectHealth,
                        enabled = healthConnectSdkStatus != HealthConnectClient.SDK_UNAVAILABLE,
                    ) {
                        Text(
                            when (healthConnectSdkStatus) {
                                HealthConnectClient.SDK_AVAILABLE ->
                                    if (
                                        uiState.settings.glucoseProviderMode ==
                                        GlucoseProviderMode.HEALTH_CONNECT
                                    ) {
                                        "Connect glucose & activity"
                                    } else {
                                        "Connect activity data"
                                    }
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                    "Install or update Health Connect"
                                else -> "Health Connect unavailable"
                            },
                        )
                    }
                }
            }
        }
        item { DailySummaryCard(uiState) }
        if (uiState.observations.isNotEmpty()) {
            item { Text("Personal observations", style = MaterialTheme.typography.titleLarge) }
            items(uiState.observations) { observation ->
                Card {
                    Text(observation.text, modifier = Modifier.padding(16.dp))
                }
            }
        }
        item {
            Button(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                Text("Enable coaching notifications")
            }
        }
        item {
            Text(
                "Metabolic Coach is a wellness tool, not a medical device. It does not diagnose, " +
                    "treat, or replace your CGM app, clinical alerts, or personal care plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GlucoseCard(uiState: PhoneUiState) {
    val reading = uiState.glucose
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("CURRENT GLUCOSE", style = MaterialTheme.typography.labelLarge)
            if (reading == null) {
                Text("—", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("No verified reading received")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reading.displayValue(uiState.settings.glucoseUnit),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(reading.trend.symbol, fontSize = 40.sp)
                }
                Text(
                    buildString {
                        append(reading.displayDelta(uiState.settings.glucoseUnit) ?: "No delta")
                        append(" • ")
                        append(
                            (uiState.nowEpochMillis - reading.measuredAtEpochMillis)
                                .coerceAtLeast(0) / 60_000,
                        )
                        append(" min ago")
                    },
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: CoachRecommendation?,
    onQuickAction: (QuickActionType, String?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            when (recommendation) {
                is CoachRecommendation.Action -> {
                    Text(recommendation.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    val type = if (recommendation.interventionType.name == "WALK") {
                        QuickActionType.START_WALK
                    } else {
                        QuickActionType.START_STAIRS
                    }
                    Button(
                        onClick = { onQuickAction(type, null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(recommendation.actionLabel)
                    }
                    Button(
                        onClick = { onQuickAction(QuickActionType.SNOOZE, null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Snooze")
                    }
                }
                is CoachRecommendation.Information -> {
                    Text(recommendation.title, style = MaterialTheme.typography.titleLarge)
                    Text(recommendation.detail)
                }
                null -> {
                    Text("No action needed right now", style = MaterialTheme.typography.titleLarge)
                    Text("Coaching uses your configured thresholds and recent data.")
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: InterventionSession,
    nowEpochMillis: Long,
    onComplete: () -> Unit,
) {
    val elapsedMinutes =
        ((nowEpochMillis - session.startedAtEpochMillis).coerceAtLeast(0) / 60_000)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (session.type == InterventionType.WALK) "Walk in progress" else "Stairs in progress",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                buildString {
                    append("$elapsedMinutes min elapsed")
                    session.targetDurationMinutes?.let { append(" • $it min target") }
                    session.targetFloors?.let { append(" • $it floors target") }
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text("Mark completed")
            }
        }
    }
}

@Composable
private fun DailySummaryCard(uiState: PhoneUiState) {
    val summary = uiState.summary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Text("Stable glucose: ${summary?.stableGlucosePercent?.let { "$it%" } ?: "—"}")
            Text("Walk sessions: ${summary?.completedWalks ?: 0}")
            Text("Stair sessions: ${summary?.completedStairSessions ?: 0}")
            Text(
                "Steps: ${summary?.steps ?: uiState.activity?.stepsToday ?: 0} / " +
                    uiState.settings.dailyStepGoal,
            )
            Text(
                "Floors: ${"%.1f".format(summary?.floors ?: 0.0)} / " +
                    uiState.settings.dailyFloorGoal,
            )
            Text(
                "Exercise sessions: ${summary?.exerciseSessionCount ?: 0} • " +
                    "${summary?.exerciseDurationMinutes ?: 0} min",
            )
        }
    }
}

@Composable
private fun GlycemicGoalScreen(
    uiState: PhoneUiState,
    onSaveSafetySettings: (GlycemicPlannerSettings) -> Unit,
    onCreateMilestone: (String?, Double, GlycemicTargetProvenance, Int) -> Unit,
    onUpdateMilestone: (
        GlycemicPlanningMilestone,
        String?,
        Double,
        GlycemicTargetProvenance,
        Int,
        Long,
    ) -> Unit,
    onSelectMilestone: (String) -> Unit,
    onArchiveMilestone: (String) -> Unit,
    onDeleteMilestone: (String) -> Unit,
    onDismissMigrationNotice: () -> Unit,
    contentPadding: PaddingValues,
) {
    var safetyDraft by remember(uiState.glycemicPlannerSettings) {
        mutableStateOf(uiState.glycemicPlannerSettings)
    }
    var editorKey by remember { mutableStateOf<String?>(null) }
    var titleText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var targetProvenance by remember { mutableStateOf(GlycemicTargetProvenance.USER_ENTERED) }
    var horizonDays by remember { mutableIntStateOf(30) }
    var pendingHorizonDays by remember { mutableStateOf<Int?>(null) }
    var deleteMilestoneId by remember { mutableStateOf<String?>(null) }
    val editingMilestone = editorKey
        ?.takeUnless { it == NEW_MILESTONE_KEY }
        ?.let { id -> uiState.planningMilestones.firstOrNull { it.id == id } }
    val futureEditAllowed = editingMilestone?.let {
        it.temporalState(uiState.nowEpochMillis) == MilestoneTemporalState.FUTURE
    } ?: true
    val orderedMilestones = com.young.metaboliccoach.core.domain.sortPlanningMilestones(
        uiState.planningMilestones,
        uiState.nowEpochMillis,
    )
    val parsedTarget = targetText.toDoubleOrNull()
    val editorHorizon = GlycemicWindow.fromDays(horizonDays)
    val editorTargetDate = editingMilestone?.let { milestone ->
        if (horizonDays == milestone.originalHorizonDays) {
            milestone.targetDateEpochMillis
        } else {
            uiState.nowEpochMillis + (editorHorizon?.durationMillis ?: 30 * DAY_MILLIS)
        }
    } ?: (uiState.nowEpochMillis + (editorHorizon?.durationMillis ?: 30 * DAY_MILLIS))

    fun beginNewMilestone() {
        editorKey = NEW_MILESTONE_KEY
        titleText = ""
        targetText = ""
        targetProvenance = GlycemicTargetProvenance.USER_ENTERED
        horizonDays = 30
    }

    fun beginEditMilestone(milestone: GlycemicPlanningMilestone) {
        editorKey = milestone.id
        titleText = milestone.title.orEmpty()
        targetText = milestone.targetGmiPercent.toString()
        targetProvenance = milestone.targetProvenance
        horizonDays = milestone.originalHorizonDays
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Glycemic Goal Planner", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "GMI is calculated from CGM mean glucose. It is not a laboratory HbA1c " +
                        "measurement, and the two values may differ.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.milestoneMigrationNotice) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Existing planner target migrated", fontWeight = FontWeight.Bold)
                        Text(
                            "Your previous target was converted into a saved milestone with a fixed target date.",
                        )
                        TextButton(onClick = onDismissMigrationNotice) { Text("Dismiss") }
                    }
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Saved planning milestones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Save several intentions, then select one for detailed evaluation. " +
                            "Milestones never change coaching, notifications, or watch data.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = ::beginNewMilestone,
                        enabled = !uiState.isOperationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("New planning milestone")
                    }
                    if (orderedMilestones.isEmpty()) {
                        Text(
                            "No saved milestones yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    orderedMilestones.forEach { milestone ->
                        MilestoneRow(
                            milestone = milestone,
                            selected = milestone.id == uiState.selectedMilestoneId,
                            nowEpochMillis = uiState.nowEpochMillis,
                            onSelect = { onSelectMilestone(milestone.id) },
                            onEdit = { beginEditMilestone(milestone) },
                            onArchive = { onArchiveMilestone(milestone.id) },
                            onDelete = { deleteMilestoneId = milestone.id },
                            enabled = !uiState.isOperationInProgress,
                        )
                    }
                }
            }
        }
        editorKey?.let { key ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (key == NEW_MILESTONE_KEY) {
                                "Create planning milestone"
                            } else {
                                "Edit planning milestone"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it.take(80) },
                            label = { Text("Title (optional)") },
                            supportingText = { Text("Use a neutral personal label.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { targetText = it },
                            label = { Text("Target GMI (%)") },
                            supportingText = {
                                Text(
                                    "GMI is CGM-derived and may differ from laboratory HbA1c.",
                                )
                            },
                            singleLine = true,
                            enabled = futureEditAllowed,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (futureEditAllowed) {
                            EnumSetting(
                                title = "Target provenance",
                                values = GlycemicTargetProvenance.entries,
                                selected = targetProvenance,
                                label = { it.name.lowercase().replace('_', ' ') },
                                onSelected = { targetProvenance = it },
                            )
                            EnumSetting(
                                title = "Time horizon",
                                values = listOf(30, 60, 90),
                                selected = horizonDays,
                                label = { "$it days" },
                                onSelected = { selected ->
                                    if (
                                        editingMilestone != null &&
                                        selected != editingMilestone.originalHorizonDays
                                    ) {
                                        pendingHorizonDays = selected
                                    } else {
                                        horizonDays = selected
                                    }
                                },
                            )
                        } else {
                            Text(
                                "Target, provenance, horizon, and date are fixed after the " +
                                    "milestone is due; only the title can change.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Target date: ${formatMilestoneDate(editorTargetDate)}",
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { editorKey = null },
                                modifier = Modifier.weight(1f),
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val target = parsedTarget ?: return@Button
                                    if (editingMilestone == null) {
                                        onCreateMilestone(
                                            normalizedTitle(titleText),
                                            target,
                                            targetProvenance,
                                            horizonDays,
                                        )
                                    } else {
                                        onUpdateMilestone(
                                            editingMilestone,
                                            normalizedTitle(titleText),
                                            target,
                                            targetProvenance,
                                            horizonDays,
                                            editorTargetDate,
                                        )
                                    }
                                    editorKey = null
                                },
                                enabled = parsedTarget != null && !uiState.isOperationInProgress,
                                modifier = Modifier.weight(1f),
                            ) { Text("Save") }
                        }
                    }
                }
            }
        }
        item {
            uiState.selectedMilestoneEvaluation?.let { evaluation ->
                MilestoneEvaluationCard(evaluation, uiState.settings.glucoseUnit)
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Planner safety settings", style = MaterialTheme.typography.titleLarge)
                    GlucoseThresholdSetting(
                        title = "Planner low-glucose boundary",
                        valueMgDl = safetyDraft.lowGlucoseThresholdMgDl,
                        rangeMgDl = GlycemicPlannerBounds.LOW_GLUCOSE_MG_DL,
                        unit = uiState.settings.glucoseUnit,
                    ) {
                        safetyDraft = safetyDraft.copy(
                            lowGlucoseThresholdMgDl = it.coerceAtLeast(
                                safetyDraft.veryLowGlucoseThresholdMgDl + 1,
                            ),
                        )
                    }
                    GlucoseThresholdSetting(
                        title = "Planner very-low boundary",
                        valueMgDl = safetyDraft.veryLowGlucoseThresholdMgDl,
                        rangeMgDl = GlycemicPlannerBounds.VERY_LOW_GLUCOSE_MG_DL,
                        unit = uiState.settings.glucoseUnit,
                    ) {
                        safetyDraft = safetyDraft.copy(
                            veryLowGlucoseThresholdMgDl = it.coerceAtMost(
                                safetyDraft.lowGlucoseThresholdMgDl - 1,
                            ),
                        )
                    }
                    DecimalSliderSetting(
                        title = "Maximum low-glucose exposure",
                        value = safetyDraft.maximumLowGlucosePercent,
                        range = GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT,
                        suffix = "%",
                    ) {
                        safetyDraft = safetyDraft.copy(
                            maximumLowGlucosePercent = it.coerceAtLeast(
                                safetyDraft.maximumVeryLowGlucosePercent,
                            ),
                        )
                    }
                    DecimalSliderSetting(
                        title = "Maximum very-low exposure",
                        value = safetyDraft.maximumVeryLowGlucosePercent,
                        range = GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT,
                        suffix = "%",
                    ) {
                        safetyDraft = safetyDraft.copy(
                            maximumVeryLowGlucosePercent = it.coerceAtMost(
                                safetyDraft.maximumLowGlucosePercent,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = { onSaveSafetySettings(safetyDraft) },
                        enabled = !uiState.isOperationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save safety settings")
                    }
                }
            }
        }
        item {
            Text("Rolling CGM metrics", style = MaterialTheme.typography.titleLarge)
        }
        items(uiState.glycemicMetrics) { metrics ->
            GlycemicMetricsCard(metrics, uiState.settings.glucoseUnit)
        }
        item {
            GlycemicScenarioCard(uiState.glycemicGoalScenario, uiState.settings.glucoseUnit)
        }
        item {
            Text(
                "Scenarios describe the future-period mean that would satisfy a simplified " +
                    "rolling CGM model. They are not treatment recommendations, prescribed " +
                    "glucose levels, or guarantees about laboratory HbA1c.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingHorizonDays?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingHorizonDays = null },
            title = { Text("Change milestone timeframe?") },
            text = { Text("Changing the timeframe will set a new fixed target date.") },
            confirmButton = {
                TextButton(onClick = {
                    horizonDays = pending
                    pendingHorizonDays = null
                }) { Text("Change date") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHorizonDays = null }) { Text("Keep date") }
            },
        )
    }
    deleteMilestoneId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteMilestoneId = null },
            title = { Text("Delete planning milestone?") },
            text = { Text("Only this saved milestone will be deleted. CGM history is unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMilestone(id)
                    deleteMilestoneId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteMilestoneId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MilestoneRow(
    milestone: GlycemicPlanningMilestone,
    selected: Boolean,
    nowEpochMillis: Long,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    val temporal = milestone.temporalState(nowEpochMillis)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    milestone.title ?: "Planning milestone",
                    fontWeight = FontWeight.Bold,
                )
                if (selected) Text("Selected", fontWeight = FontWeight.Medium)
            }
            Text("Target GMI: ${"%.1f".format(milestone.targetGmiPercent)}%")
            Text(
                "Target date: ${formatMilestoneDate(milestone.targetDateEpochMillis)} • " +
                    temporal.name.lowercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (milestone.lifecycleState == MilestoneLifecycleState.ARCHIVED) {
                Text("Archived", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (milestone.lifecycleState == MilestoneLifecycleState.ACTIVE) {
                    OutlinedButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
                }
                if (milestone.lifecycleState == MilestoneLifecycleState.ACTIVE) {
                    OutlinedButton(onClick = onArchive, enabled = enabled) { Text("Archive") }
                }
                OutlinedButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun MilestoneEvaluationCard(
    evaluation: GlycemicPlanningMilestoneEvaluation,
    unit: GlucoseUnit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Selected milestone", style = MaterialTheme.typography.titleLarge)
            evaluation.scenario?.let { scenario ->
                if (
                    scenario.status == GlycemicScenarioStatus.AVAILABLE ||
                    scenario.status == GlycemicScenarioStatus.AVAILABLE_WITH_WARNING
                ) {
                    Text(
                        "Remaining-window scenario mean: " +
                            formatGlucose(scenario.scenarioFutureMeanGlucoseMgDl, unit),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Target date: ${formatMilestoneDate(evaluation.targetDateEpochMillis)}",
                )
                Text(scenario.detail)
            }
            evaluation.evaluationState?.let { state ->
                Text(
                    when (state) {
                        MilestoneEvaluationState.TARGET_CONDITION_MET ->
                            "Target condition met"
                        MilestoneEvaluationState.TARGET_CONDITION_NOT_MET ->
                            "Target condition not met"
                        MilestoneEvaluationState.INSUFFICIENT_DATA ->
                            "Not enough data to evaluate"
                        MilestoneEvaluationState.SOURCE_DISCONTINUITY ->
                            "Source discontinuity"
                        MilestoneEvaluationState.SUPPRESSED_FOR_LOW_GLUCOSE_RISK ->
                            "Evaluation suppressed for low-glucose risk"
                        MilestoneEvaluationState.CALCULATION_UNAVAILABLE ->
                            "Evaluation unavailable"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(evaluation.detail)
            evaluation.rollingMetrics?.let { metrics ->
                Text(
                    "Coverage: ${formatPercent(metrics.coveragePercent)} • " +
                        "GMI: ${formatPercent(metrics.gmiPercent)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun normalizedTitle(value: String): String? = value.trim().takeIf(String::isNotEmpty)

private fun formatMilestoneDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

private const val NEW_MILESTONE_KEY = "__new_milestone__"
private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

@Composable
private fun GlycemicMetricsCard(
    metrics: RollingGlycemicMetrics,
    unit: GlucoseUnit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${metrics.window.days}-day average", style = MaterialTheme.typography.titleLarge)
            if (metrics.status == GlycemicMetricsStatus.AVAILABLE) {
                Text(
                    "Mean glucose: ${formatGlucose(metrics.meanGlucoseMgDl, unit)}",
                    fontWeight = FontWeight.Bold,
                )
                Text("CGM-derived GMI: ${formatPercent(metrics.gmiPercent)}")
                Text(
                    "TIR: ${formatPercent(metrics.timeInRangePercent)} • " +
                        "TBR: ${formatPercent(metrics.timeBelowRangePercent)} • " +
                        "Very low: ${formatPercent(metrics.timeVeryLowPercent)}",
                )
                Text("Coverage: ${formatPercent(metrics.coveragePercent)}")
                Text(
                    "Missing: ${formatDuration(metrics.missingDurationMillis)} • " +
                        "Largest gap: ${formatDuration(metrics.largestGapMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(metrics.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Coverage: ${formatPercent(metrics.coveragePercent)}")
            }
        }
    }
}

@Composable
private fun GlycemicScenarioCard(
    scenario: GlycemicGoalScenario?,
    unit: GlucoseUnit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Goal scenario", style = MaterialTheme.typography.titleLarge)
            when {
                scenario == null -> Text("Set a target to calculate a planning scenario.")
                scenario.status == GlycemicScenarioStatus.AVAILABLE ||
                    scenario.status == GlycemicScenarioStatus.AVAILABLE_WITH_WARNING -> {
                    Text(
                        "Next ${scenario.horizon.days} days: " +
                            formatGlucose(scenario.scenarioFutureMeanGlucoseMgDl, unit),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(scenario.detail)
                    scenario.recentSafety?.let {
                        Text(
                            "Recent 14-day TIR ${formatPercent(it.timeInRangePercent)} • " +
                                "TBR ${formatPercent(it.timeBelowRangePercent)}",
                        )
                    }
                }
                else -> Text(scenario.detail)
            }
        }
    }
}

private fun formatGlucose(valueMgDl: Double?, unit: GlucoseUnit): String =
    valueMgDl?.let {
        if (unit == GlucoseUnit.MG_DL) "%.0f mg/dL".format(it)
        else "%.1f mmol/L".format(unit.fromMgDl(it))
    } ?: "—"

private fun formatPercent(value: Double?): String = value?.let { "%.1f%%".format(it) } ?: "—"

private fun formatDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    return when {
        totalMinutes < 60L -> "${totalMinutes}m"
        else -> "${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    }
}

@Composable
private fun SettingsScreen(
    settings: CoachSettings,
    nightscoutSettings: NightscoutSettings,
    glucoseHistory: GlucoseHistoryStatus,
    operationMessage: String?,
    isOperationInProgress: Boolean,
    onSave: (CoachSettings, NightscoutSettings) -> Unit,
    onExportData: () -> Unit,
    onEraseData: () -> Unit,
    onSaveGlucoseHistorySettings: (GlucoseHistorySettings) -> Unit,
    onConfirmGlucoseHistoryRetention: () -> Unit,
    onBackfillGlucoseHistory: () -> Unit,
    contentPadding: PaddingValues,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var nightscoutDraft by remember(nightscoutSettings) {
        mutableStateOf(nightscoutSettings)
    }
    var showEraseConfirmation by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                operationMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            NightscoutSettingsCard(
                settings = nightscoutDraft,
                onSettingsChanged = { nightscoutDraft = it },
            )
        }
        item {
            GlucoseHistorySettingsCard(
                status = glucoseHistory,
                isOperationInProgress = isOperationInProgress,
                onSaveSettings = onSaveGlucoseHistorySettings,
                onConfirmRetention = onConfirmGlucoseHistoryRetention,
                onBackfill = onBackfillGlucoseHistory,
            )
        }
        item {
            EnumSetting(
                title = "Glucose units",
                values = GlucoseUnit.entries,
                selected = draft.glucoseUnit,
                label = { if (it == GlucoseUnit.MG_DL) "mg/dL" else "mmol/L" },
                onSelected = { draft = draft.copy(glucoseUnit = it) },
            )
        }
        item {
            GlucoseThresholdSetting(
                "Low-glucose safety threshold",
                draft.lowGlucoseThresholdMgDl,
                CoachSettingsBounds.LOW_GLUCOSE_MG_DL,
                draft.glucoseUnit,
            ) { draft = draft.copy(lowGlucoseThresholdMgDl = it) }
        }
        item {
            GlucoseThresholdSetting(
                "Target lower bound",
                draft.targetLowerMgDl,
                CoachSettingsBounds.TARGET_LOWER_MG_DL,
                draft.glucoseUnit,
            ) {
                draft = draft.copy(
                    targetLowerMgDl = it.coerceAtMost(draft.targetUpperMgDl - 1),
                )
            }
        }
        item {
            GlucoseThresholdSetting(
                "Target upper bound",
                draft.targetUpperMgDl,
                CoachSettingsBounds.TARGET_UPPER_MG_DL,
                draft.glucoseUnit,
            ) {
                draft = draft.copy(
                    targetUpperMgDl = it.coerceAtLeast(draft.targetLowerMgDl + 1),
                )
            }
        }
        item {
            GlucoseRateSetting(
                "Rapid-rise threshold",
                draft.rapidRiseThresholdMgDlPerMinute,
                CoachSettingsBounds.RAPID_RISE_MG_DL_PER_MINUTE,
                draft.glucoseUnit,
            ) { draft = draft.copy(rapidRiseThresholdMgDlPerMinute = it) }
        }
        item {
            GlucoseRateSetting(
                "Exercise-pause fall rate",
                draft.exercisePauseFallRateMgDlPerMinute,
                CoachSettingsBounds.EXERCISE_PAUSE_FALL_RATE_MG_DL_PER_MINUTE,
                draft.glucoseUnit,
            ) { draft = draft.copy(exercisePauseFallRateMgDlPerMinute = it) }
        }
        item {
            SliderSetting(
                "Glucose/activity freshness",
                draft.staleReadingMinutes,
                CoachSettingsBounds.STALE_READING_MINUTES,
                "min",
            ) {
                draft = draft.copy(staleReadingMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Walk duration",
                draft.walkingDurationMinutes,
                CoachSettingsBounds.WALKING_DURATION_MINUTES,
                "min",
            ) {
                draft = draft.copy(walkingDurationMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Stair target",
                draft.stairTargetFloors,
                CoachSettingsBounds.STAIR_TARGET_FLOORS,
                "floors",
            ) {
                draft = draft.copy(stairTargetFloors = it)
            }
        }
        item {
            SliderSetting(
                "Daily step goal",
                draft.dailyStepGoal,
                CoachSettingsBounds.DAILY_STEP_GOAL,
                "steps",
            ) {
                draft = draft.copy(dailyStepGoal = it)
            }
        }
        item {
            SliderSetting(
                "Daily floor goal",
                draft.dailyFloorGoal,
                CoachSettingsBounds.DAILY_FLOOR_GOAL,
                "floors",
            ) {
                draft = draft.copy(dailyFloorGoal = it)
            }
        }
        item {
            SliderSetting(
                "Inactivity threshold",
                draft.prolongedInactivityMinutes,
                CoachSettingsBounds.PROLONGED_INACTIVITY_MINUTES,
                "min",
            ) { draft = draft.copy(prolongedInactivityMinutes = it) }
        }
        item {
            SliderSetting(
                "Post-meal delay",
                draft.postMealDelayMinutes,
                CoachSettingsBounds.POST_MEAL_DELAY_MINUTES,
                "min",
            ) {
                draft = draft.copy(postMealDelayMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Post-meal window",
                draft.postMealWindowMinutes,
                CoachSettingsBounds.POST_MEAL_WINDOW_MINUTES,
                "min",
            ) {
                draft = draft.copy(postMealWindowMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Reminder cooldown",
                draft.reminderCooldownMinutes,
                CoachSettingsBounds.REMINDER_COOLDOWN_MINUTES,
                "min",
            ) {
                draft = draft.copy(reminderCooldownMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Snooze",
                draft.snoozeMinutes,
                CoachSettingsBounds.SNOOZE_MINUTES,
                "min",
            ) {
                draft = draft.copy(snoozeMinutes = it)
            }
        }
        item {
            SliderSetting(
                "Maximum notifications",
                draft.maximumNotificationsPerDay,
                CoachSettingsBounds.MAXIMUM_NOTIFICATIONS_PER_DAY,
                "per day",
            ) { draft = draft.copy(maximumNotificationsPerDay = it) }
        }
        item {
            TimeSetting("Quiet hours start", draft.quietHoursStartMinuteOfDay) {
                draft = draft.copy(quietHoursStartMinuteOfDay = it)
            }
        }
        item {
            TimeSetting("Quiet hours end", draft.quietHoursEndMinuteOfDay) {
                draft = draft.copy(quietHoursEndMinuteOfDay = it)
            }
        }
        item {
            TimeSetting("Working hours start", draft.workingHoursStartMinuteOfDay) {
                draft = draft.copy(workingHoursStartMinuteOfDay = it)
            }
        }
        item {
            TimeSetting("Working hours end", draft.workingHoursEndMinuteOfDay) {
                draft = draft.copy(workingHoursEndMinuteOfDay = it)
            }
        }
        item {
            SliderSetting(
                "Observation sample minimum",
                draft.minimumObservationSamples,
                CoachSettingsBounds.MINIMUM_OBSERVATION_SAMPLES,
                "sessions",
            ) { draft = draft.copy(minimumObservationSamples = it) }
        }
        item {
            SliderSetting(
                "Timing samples per bucket",
                draft.minimumTimingBucketSamples,
                CoachSettingsBounds.MINIMUM_TIMING_BUCKET_SAMPLES,
                "sessions",
            ) { draft = draft.copy(minimumTimingBucketSamples = it) }
        }
        item {
            SliderSetting(
                "Comparable timing buckets",
                draft.minimumComparableTimingBuckets,
                CoachSettingsBounds.MINIMUM_COMPARABLE_TIMING_BUCKETS,
                "buckets",
            ) { draft = draft.copy(minimumComparableTimingBuckets = it) }
        }
        item {
            SliderSetting(
                "General timing bucket width",
                draft.interventionTimingBucketMinutes,
                CoachSettingsBounds.INTERVENTION_TIMING_BUCKET_MINUTES,
                "min",
            ) { draft = draft.copy(interventionTimingBucketMinutes = it) }
        }
        item {
            SliderSetting(
                "Post-meal timing bucket width",
                draft.postMealTimingBucketMinutes,
                CoachSettingsBounds.POST_MEAL_TIMING_BUCKET_MINUTES,
                "min",
            ) { draft = draft.copy(postMealTimingBucketMinutes = it) }
        }
        item {
            SliderSetting(
                "Follow-up matching bucket width",
                draft.followUpDelayBucketMinutes,
                CoachSettingsBounds.FOLLOW_UP_DELAY_BUCKET_MINUTES,
                "min",
            ) { draft = draft.copy(followUpDelayBucketMinutes = it) }
        }
        item {
            GlucoseThresholdSetting(
                "Baseline glucose matching band",
                draft.baselineGlucoseBandMgDl,
                CoachSettingsBounds.BASELINE_GLUCOSE_BAND_MG_DL,
                draft.glucoseUnit,
            ) { draft = draft.copy(baselineGlucoseBandMgDl = it) }
        }
        item {
            SliderSetting(
                "Intervention glucose follow-up",
                draft.interventionFollowUpMinutes,
                CoachSettingsBounds.INTERVENTION_FOLLOW_UP_MINUTES,
                "min",
            ) { draft = draft.copy(interventionFollowUpMinutes = it) }
        }
        item {
            SliderSetting(
                "Offline quick-action expiry",
                draft.quickActionExpiryMinutes,
                CoachSettingsBounds.QUICK_ACTION_EXPIRY_MINUTES,
                "min",
            ) { draft = draft.copy(quickActionExpiryMinutes = it) }
        }
        item {
            ToggleSetting("Walking reminders", draft.walkingRemindersEnabled) {
                draft = draft.copy(walkingRemindersEnabled = it)
            }
        }
        item {
            ToggleSetting("Stair reminders", draft.stairRemindersEnabled) {
                draft = draft.copy(stairRemindersEnabled = it)
            }
        }
        item {
            ToggleSetting("Post-meal reminders", draft.postMealRemindersEnabled) {
                draft = draft.copy(postMealRemindersEnabled = it)
            }
        }
        item {
            ToggleSetting("Notifications", draft.notificationsEnabled) {
                draft = draft.copy(notificationsEnabled = it)
            }
        }
        item {
            EnumSetting(
                title = "Theme",
                values = CoachTheme.entries,
                selected = draft.theme,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelected = { draft = draft.copy(theme = it) },
            )
        }
        item {
            DecimalSliderSetting(
                "Font size",
                draft.fontScale.toDouble(),
                CoachSettingsBounds.FONT_SCALE,
                "×",
            ) { draft = draft.copy(fontScale = it.toFloat()) }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        draft.copy(glucoseProviderMode = GlucoseProviderMode.NIGHTSCOUT),
                        nightscoutDraft,
                    )
                },
                enabled = !isOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save settings")
            }
        }
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your data", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Export creates plain JSON containing sensitive health and activity data. " +
                        "Store it in a secure location.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onExportData,
                    enabled = !isOperationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export personal data")
                }
                Text(
                    "Erase removes this app's phone history and settings and sends a reset to " +
                        "the watch. It does not delete source records or revoke Health Connect " +
                        "or CGM permissions, so new data can be collected later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showEraseConfirmation = true },
                    enabled = !isOperationInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Erase Metabolic Coach data")
                }
            }
        }
    }
    if (showEraseConfirmation) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmation = false },
            title = { Text("Erase local data?") },
            text = {
                Text(
                    "This permanently deletes Metabolic Coach history and resets settings on " +
                        "this phone. The synced watch cache and queued watch actions will be " +
                        "cleared when the reset arrives. Source data and permissions remain.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseConfirmation = false
                        onEraseData()
                    },
                    enabled = !isOperationInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Erase data")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEraseConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun GlucoseHistorySettingsCard(
    status: GlucoseHistoryStatus,
    isOperationInProgress: Boolean,
    onSaveSettings: (GlucoseHistorySettings) -> Unit,
    onConfirmRetention: () -> Unit,
    onBackfill: () -> Unit,
) {
    var draft by remember(status.settings) { mutableStateOf(status.settings) }
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Local glucose history", style = MaterialTheme.typography.titleLarge)
            Text(
                "Current and historical Nightscout readings are stored locally on the phone. " +
                    "The watch receives only the normalized current state.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EnumSetting(
                title = "Retention policy",
                values = GlucoseHistoryRetentionPolicy.entries,
                selected = draft.retentionPolicy,
                label = { it.label },
                onSelected = {
                    draft = GlucoseHistorySettings(
                        retentionPolicy = it,
                        retentionConfirmed = false,
                    )
                },
            )
            Button(
                onClick = { onSaveSettings(draft) },
                enabled = !isOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save history policy") }
            if (!status.settings.retentionConfirmed) {
                Text(
                    "No records are pruned until you explicitly confirm the selected policy.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onConfirmRetention,
                    enabled = !isOperationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm and apply policy") }
            } else {
                Text("Retention policy confirmed; pruning is source-scoped.")
            }
            Text(
                "Stored: ${status.readingCount} readings • " +
                    "oldest: ${status.oldestReadingAtEpochMillis?.let(::formatMilestoneDate) ?: "—"} • " +
                    "newest: ${status.newestReadingAtEpochMillis?.let(::formatMilestoneDate) ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (status.settings.retentionPolicy == GlucoseHistoryRetentionPolicy.LAST_90_DAYS) {
                Text(
                    "The normal refresh keeps the most recent 90 days. Choose 1 year or " +
                        "Keep all downloaded, confirm it, then download older ranges here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status.settings.retentionPolicy != GlucoseHistoryRetentionPolicy.LAST_90_DAYS) {
                Button(
                    onClick = onBackfill,
                    enabled = status.settings.retentionConfirmed &&
                        !isOperationInProgress &&
                        status.backfillStatus != GlucoseHistoryBackfillStatus.RUNNING &&
                        status.backfillStatus != GlucoseHistoryBackfillStatus.COMPLETE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (status.backfillStatus == GlucoseHistoryBackfillStatus.COMPLETE) {
                            "Older history complete"
                        } else {
                            "Download one older range"
                        },
                    )
                }
                Text(
                    "Backfill status: ${status.backfillStatus.name.lowercase()}" +
                        (status.lastError?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "History is app-private and excluded from Android cloud/device backup. " +
                    "Opening future charts will use local rows and will not start network work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NightscoutSettingsCard(
    settings: NightscoutSettings,
    onSettingsChanged: (NightscoutSettings) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nightscout glucose", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version 1 reads glucose only from the selected Nightscout server. " +
                    "Servers are kept separate and are never used as automatic failover.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            settings.servers.forEachIndexed { index, server ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = server.displayName,
                            onValueChange = { value ->
                                onSettingsChanged(
                                    settings.copy(
                                        servers = settings.servers.replaceServer(
                                            index,
                                            server.copy(displayName = value),
                                        ),
                                    ),
                                )
                            },
                            label = { Text("Server name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = server.baseUrl,
                            onValueChange = { value ->
                                onSettingsChanged(
                                    settings.copy(
                                        servers = settings.servers.replaceServer(
                                            index,
                                            server.copy(baseUrl = value),
                                        ),
                                    ),
                                )
                            },
                            label = { Text("Nightscout URL") },
                            placeholder = { Text("https://example.fly.dev") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = settings.activeServerId == server.id,
                                onClick = {
                                    onSettingsChanged(
                                        settings.copy(activeServerId = server.id),
                                    )
                                },
                                enabled = server.baseUrl.isNotBlank(),
                                label = {
                                    Text(
                                        if (settings.activeServerId == server.id) {
                                            "Active server"
                                        } else {
                                            "Use this server"
                                        },
                                    )
                                },
                            )
                            if (settings.servers.size > 1) {
                                OutlinedButton(
                                    onClick = {
                                        val remaining = settings.servers
                                            .filterNot { it.id == server.id }
                                        val nextActive = if (
                                            settings.activeServerId == server.id
                                        ) {
                                            remaining.firstOrNull {
                                                it.baseUrl.isNotBlank()
                                            }?.id ?: remaining.firstOrNull()?.id
                                        } else {
                                            settings.activeServerId
                                        }
                                        onSettingsChanged(
                                            settings.copy(
                                                servers = remaining,
                                                activeServerId = nextActive,
                                            ),
                                        )
                                    },
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
            if (settings.servers.size < NightscoutSettingsBounds.MAXIMUM_SERVERS) {
                OutlinedButton(
                    onClick = {
                        val id = nextNightscoutServerId(settings.servers)
                        onSettingsChanged(
                            settings.copy(
                                servers = settings.servers + NightscoutServerConfig(
                                    id = id,
                                    displayName = "Server ${settings.servers.size + 1}",
                                    baseUrl = "",
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add Nightscout server")
                }
            }
            ToggleSetting("Require HTTPS", settings.requireHttps) {
                onSettingsChanged(settings.copy(requireHttps = it))
            }
            Text(
                if (settings.requireHttps) {
                    "HTTPS is enforced. This is strongly recommended."
                } else {
                    "HTTP is allowed for explicitly configured local/test servers. " +
                        "Glucose can be exposed on the network."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.requireHttps) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            SliderSetting(
                title = "Polling interval",
                value = settings.pollingIntervalMinutes,
                range = NightscoutSettingsBounds.POLLING_INTERVAL_MINUTES,
                suffix = "min",
            ) {
                onSettingsChanged(settings.copy(pollingIntervalMinutes = it))
            }
            SliderSetting(
                title = "Connection timeout",
                value = settings.connectionTimeoutSeconds,
                range = NightscoutSettingsBounds.CONNECTION_TIMEOUT_SECONDS,
                suffix = "sec",
            ) {
                onSettingsChanged(settings.copy(connectionTimeoutSeconds = it))
            }
            SliderSetting(
                title = "Retry interval",
                value = settings.retryIntervalSeconds,
                range = NightscoutSettingsBounds.RETRY_INTERVAL_SECONDS,
                suffix = "sec",
            ) {
                onSettingsChanged(settings.copy(retryIntervalSeconds = it))
            }
            SliderSetting(
                title = "Retry attempts",
                value = settings.maximumRetryAttempts,
                range = NightscoutSettingsBounds.MAXIMUM_RETRY_ATTEMPTS,
                suffix = "",
            ) {
                onSettingsChanged(settings.copy(maximumRetryAttempts = it))
            }
            Text(
                "Future authenticated servers will use a phone-only credential store. " +
                    "Credentials are never accepted in the URL or sent to the watch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun List<NightscoutServerConfig>.replaceServer(
    index: Int,
    server: NightscoutServerConfig,
): List<NightscoutServerConfig> = mapIndexed { currentIndex, current ->
    if (currentIndex == index) server else current
}

private fun nextNightscoutServerId(
    servers: List<NightscoutServerConfig>,
): String {
    val existing = servers.mapTo(mutableSetOf(), NightscoutServerConfig::id)
    return generateSequence(servers.size + 1) { it + 1 }
        .map { "server-$it" }
        .first { it !in existing }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onValue: (Int) -> Unit,
) {
    Column {
        Text("$title: $value $suffix", fontWeight = FontWeight.Medium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun GlucoseThresholdSetting(
    title: String,
    valueMgDl: Int,
    rangeMgDl: IntRange,
    unit: GlucoseUnit,
    onValueMgDl: (Int) -> Unit,
) {
    val displayValue = unit.fromMgDl(valueMgDl.toDouble())
    val displayRange =
        unit.fromMgDl(rangeMgDl.first.toDouble()).toFloat()..
            unit.fromMgDl(rangeMgDl.last.toDouble()).toFloat()
    val formattedValue = if (unit == GlucoseUnit.MG_DL) {
        valueMgDl.toString()
    } else {
        "%.1f".format(displayValue)
    }
    Column {
        Text(
            "$title: $formattedValue ${unit.concentrationLabel}",
            fontWeight = FontWeight.Medium,
        )
        Slider(
            value = displayValue.toFloat().coerceIn(displayRange),
            onValueChange = { selected ->
                onValueMgDl(
                    unit.toMgDl(selected.toDouble())
                        .roundToInt()
                        .coerceIn(rangeMgDl),
                )
            },
            valueRange = displayRange,
        )
    }
}

@Composable
private fun GlucoseRateSetting(
    title: String,
    valueMgDlPerMinute: Double,
    rangeMgDlPerMinute: ClosedFloatingPointRange<Double>,
    unit: GlucoseUnit,
    onValueMgDlPerMinute: (Double) -> Unit,
) {
    val displayValue = unit.fromMgDl(valueMgDlPerMinute)
    val displayRange =
        unit.fromMgDl(rangeMgDlPerMinute.start).toFloat()..
            unit.fromMgDl(rangeMgDlPerMinute.endInclusive).toFloat()
    val displayPrecision = if (unit == GlucoseUnit.MG_DL) 10.0 else 100.0
    val formattedValue = if (unit == GlucoseUnit.MG_DL) {
        "%.1f".format(displayValue)
    } else {
        "%.2f".format(displayValue)
    }
    Column {
        Text(
            "$title: $formattedValue ${unit.rateLabel}",
            fontWeight = FontWeight.Medium,
        )
        Slider(
            value = displayValue.toFloat().coerceIn(displayRange),
            onValueChange = { selected ->
                val roundedDisplay =
                    (selected * displayPrecision).roundToInt() / displayPrecision
                onValueMgDlPerMinute(
                    unit.toMgDl(roundedDisplay)
                        .coerceIn(rangeMgDlPerMinute),
                )
            },
            valueRange = displayRange,
        )
    }
}

@Composable
private fun DecimalSliderSetting(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValue: (Double) -> Unit,
) {
    Column {
        Text("$title: ${"%.1f".format(value)} $suffix", fontWeight = FontWeight.Medium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue((it * 10).roundToInt() / 10.0) },
            valueRange = range,
        )
    }
}

@Composable
private fun TimeSetting(
    title: String,
    minuteOfDay: Int,
    onValue: (Int) -> Unit,
) {
    val normalized = minuteOfDay.coerceIn(CoachSettingsBounds.MINUTE_OF_DAY)
    val hours = normalized / MINUTES_PER_HOUR
    val minutes = normalized % MINUTES_PER_HOUR
    Column {
        Text("$title: %02d:%02d".format(hours, minutes), fontWeight = FontWeight.Medium)
        Text("Hour", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = hours.toFloat(),
            onValueChange = {
                onValue(minuteOfDayWithHour(normalized, it.roundToInt()))
            },
            valueRange = 0f..23f,
            steps = 22,
        )
        Text("Minute", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = minutes.toFloat(),
            onValueChange = {
                onValue(minuteOfDayWithMinute(normalized, it.roundToInt()))
            },
            valueRange = 0f..59f,
            steps = 58,
        )
    }
}

internal fun minuteOfDayWithHour(minuteOfDay: Int, hour: Int): Int =
    hour.coerceIn(0, 23) * MINUTES_PER_HOUR +
        minuteOfDay.coerceIn(CoachSettingsBounds.MINUTE_OF_DAY) % MINUTES_PER_HOUR

internal fun minuteOfDayWithMinute(minuteOfDay: Int, minute: Int): Int =
    minuteOfDay.coerceIn(CoachSettingsBounds.MINUTE_OF_DAY) / MINUTES_PER_HOUR *
        MINUTES_PER_HOUR +
        minute.coerceIn(0, 59)

private const val MINUTES_PER_HOUR = 60

@Composable
private fun ToggleSetting(title: String, selected: Boolean, onSelected: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Switch(checked = selected, onCheckedChange = onSelected)
    }
}

@Composable
private fun HealthConnectOriginSetting(
    availableOrigins: List<GlucoseDataOrigin>,
    selectedPackageName: String?,
    onSelected: (String) -> Unit,
) {
    val packages = buildList {
        selectedPackageName?.let(::add)
        addAll(availableOrigins.map(GlucoseDataOrigin::packageName))
    }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Health Connect glucose source", fontWeight = FontWeight.Medium)
        when {
            packages.isEmpty() -> Text(
                "Refresh after granting Health Connect access to discover glucose-writing apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selectedPackageName == null && packages.size > 1 -> Text(
                "Choose exactly one source. Glucose coaching stays paused until the choice is saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Text(
                "The saved package remains pinned if it temporarily stops writing records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        packages.forEach { packageName ->
            val discovered = availableOrigins.any { it.packageName == packageName }
            FilterChip(
                selected = packageName == selectedPackageName,
                onClick = { onSelected(packageName) },
                label = {
                    Text(
                        if (discovered) {
                            packageName
                        } else {
                            "$packageName (no recent records)"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun <T> EnumSetting(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun MetabolicCoachTheme(
    settings: CoachSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (settings.theme) {
        CoachTheme.HIGH_CONTRAST -> darkColorScheme(
            primary = Color(0xFF8BFFBF),
            onPrimary = Color.Black,
            secondary = Color.White,
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            outline = Color.White,
        )
        CoachTheme.DARK -> darkColorScheme(
            primary = Color(0xFF65E6A5),
            onPrimary = Color(0xFF002114),
            secondary = Color(0xFF9CCAFF),
            background = Color(0xFF081116),
            surface = Color(0xFF111C22),
            onSurface = Color(0xFFF1F5F4),
        )
        CoachTheme.SYSTEM -> if (systemDark) {
            darkColorScheme(
                primary = Color(0xFF65E6A5),
                onPrimary = Color(0xFF002114),
                secondary = Color(0xFF9CCAFF),
                background = Color(0xFF081116),
                surface = Color(0xFF111C22),
                onSurface = Color(0xFFF1F5F4),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF006C4A),
                onPrimary = Color.White,
                secondary = Color(0xFF2E6385),
                background = Color(0xFFF7FAF8),
                surface = Color.White,
                onSurface = Color(0xFF111814),
            )
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * settings.fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
