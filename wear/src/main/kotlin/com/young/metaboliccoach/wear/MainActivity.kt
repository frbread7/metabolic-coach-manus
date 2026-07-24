package com.young.metaboliccoach.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.young.metaboliccoach.core.model.CoachRecommendation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.CoachTheme
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.wear.ui.WearUiState
import com.young.metaboliccoach.wear.ui.WearViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {}
            WearTheme(state.watchState.settings) {
                MetabolicHome(
                    state = state,
                    onAction = viewModel::perform,
                    onNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MetabolicHome(
    state: WearUiState,
    onAction: (QuickActionType, String?, Long?) -> Unit,
    onNotificationPermission: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    LaunchedEffect(state.activeSession?.id) {
        if (state.activeSession != null) {
            pagerState.animateScrollToPage(HOME_PAGE)
        }
    }
    AppScaffold {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (it) {
                    HOME_PAGE -> WearPage {
                        item { GlucosePanel(state) }
                        if (state.activeSession == null) {
                            state.watchState.recommendation?.let { recommendation ->
                                item { RecommendationPanel(recommendation, onAction) }
                            }
                        }
                        state.activeSession?.let { session ->
                            item { SessionProgress(session, compact = true) }
                            item {
                                PrimaryActionButton(
                                    label = if (session.type == InterventionType.WALK) {
                                        "Complete walk"
                                    } else {
                                        "Complete stairs"
                                    },
                                    onClick = {
                                        onAction(QuickActionType.MARK_COMPLETED, null, null)
                                    },
                                )
                            }
                        }
                        if (state.sessionSyncPending && state.activeSession == null) {
                            item {
                                Text(
                                    state.syncMessage ?: "Syncing activity with phone…",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (
                            state.activeSession == null &&
                            !state.sessionSyncPending &&
                            state.watchState.recommendation == null
                        ) {
                            item {
                                Text(
                                    "No action needed now",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    ACTIONS_PAGE -> WearPage {
                        item {
                            Text("Quick actions", style = MaterialTheme.typography.titleLarge)
                        }
                        if (state.activeSession == null && !state.sessionSyncPending) {
                            item {
                                PrimaryActionButton(
                                    label =
                                        "Start ${state.watchState.settings.walkingDurationMinutes}-min walk",
                                    onClick = {
                                        onAction(QuickActionType.START_WALK, null, null)
                                    },
                                )
                            }
                            item {
                                PrimaryActionButton(
                                    label =
                                        "Climb ${state.watchState.settings.stairTargetFloors} floors",
                                    onClick = {
                                        onAction(QuickActionType.START_STAIRS, null, null)
                                    },
                                )
                            }
                        } else {
                            item {
                                PrimaryActionButton(
                                    label = "Mark activity completed",
                                    onClick = {
                                        onAction(QuickActionType.MARK_COMPLETED, null, null)
                                    },
                                )
                            }
                        }
                        if (state.watchState.recommendation != null) {
                            item {
                                PrimaryActionButton(
                                    label = "Snooze reminder",
                                    onClick = {
                                        onAction(QuickActionType.SNOOZE, null, null)
                                    },
                                )
                            }
                        }
                        item {
                            PrimaryActionButton(
                                label = "Enable reminders",
                                onClick = onNotificationPermission,
                            )
                        }
                    }
                    else -> WearPage {
                        item { Text("Today", style = MaterialTheme.typography.titleLarge) }
                        item { ActivityPanel(state) }
                        item {
                            Text(
                                "Watch battery ${state.watchBatteryPercent ?: "—"}%",
                            )
                        }
                        item {
                            Text(
                                "Swipe left or right for coach and actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Text(
                                "Wellness guidance only",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearPage(content: TransformingLazyColumnScope.() -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {},
    ) { scaffoldPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 10.dp,
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                end = 10.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.9f),
        label = { Text(label) },
    )
}

@Composable
private fun GlucosePanel(state: WearUiState) {
    val reading = state.watchState.glucose
    var showTrendDetails by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(24.dp),
            )
            .combinedClickable(
                onClick = { showTrendDetails = !showTrendDetails },
                onLongClick = { showTrendDetails = !showTrendDetails },
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (reading == null) {
            Text("—", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("No glucose data")
        } else {
            Text(
                "${reading.displayValue(state.watchState.settings.glucoseUnit)} ${reading.trend.symbol}",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${reading.displayDelta(state.watchState.settings.glucoseUnit) ?: "—"} • " +
                    "${(System.currentTimeMillis() - reading.measuredAtEpochMillis).coerceAtLeast(0) / 60_000} min",
            )
            if (showTrendDetails) {
                Text(
                    reading.displayRateWithUnit(
                        state.watchState.settings.glucoseUnit,
                    )?.let { "Trend $it" } ?: "Numeric trend rate unavailable",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            "⌁ ${state.watchBatteryPercent ?: "—"}%",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RecommendationPanel(
    recommendation: CoachRecommendation,
    onAction: (QuickActionType, String?, Long?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (recommendation) {
            is CoachRecommendation.Action -> {
                Text(recommendation.title, fontWeight = FontWeight.Bold)
                val action = if (recommendation.interventionType == InterventionType.WALK) {
                    QuickActionType.START_WALK
                } else {
                    QuickActionType.START_STAIRS
                }
                Button(
                    onClick = {
                        onAction(
                            action,
                            recommendation.id,
                            recommendation.validUntilEpochMillis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(recommendation.actionLabel) },
                )
                Button(
                    onClick = { onAction(QuickActionType.SNOOZE, null, null) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Snooze") },
                )
            }
            is CoachRecommendation.Information -> {
                Text(recommendation.title, fontWeight = FontWeight.Bold)
                Text(recommendation.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActivityPanel(state: WearUiState) {
    val activity = state.watchState.activity
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${activity?.stepsToday ?: 0} / ${state.watchState.settings.dailyStepGoal} steps",
        )
        Text(
            "${"%.1f".format(activity?.floorsToday ?: 0.0)} / " +
                "${state.watchState.settings.dailyFloorGoal} floors",
        )
        state.watchState.phoneBatteryPercent?.let { Text("Phone $it%") }
    }
}

@Composable
internal fun WearTheme(
    settings: CoachSettings,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val colors = when (settings.theme) {
        CoachTheme.HIGH_CONTRAST -> base.copy(
            primary = Color(0xFF8BFFBF),
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surfaceContainer = Color.Black,
            onSurface = Color.White,
            onSurfaceVariant = Color.White,
            outline = Color.White,
        )
        CoachTheme.SYSTEM,
        CoachTheme.DARK,
        -> base.copy(
            primary = Color(0xFF65E6A5),
            background = Color.Black,
            surfaceContainer = Color(0xFF111C22),
        )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * settings.fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
}

private const val HOME_PAGE = 0
private const val ACTIONS_PAGE = 1
private const val PAGE_COUNT = 3
