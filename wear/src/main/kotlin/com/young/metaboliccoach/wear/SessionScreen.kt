package com.young.metaboliccoach.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.young.metaboliccoach.core.model.InterventionType
import com.young.metaboliccoach.wear.data.ActiveWearSession
import com.young.metaboliccoach.wear.ui.WearUiState
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(
    state: WearUiState,
    terminalMessage: String? = null,
    onComplete: () -> Unit,
) {
    val session = state.activeSession
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            terminalMessage != null -> Text(terminalMessage)
            session == null -> Text(state.syncMessage ?: "Action syncing")
            else -> SessionProgress(session)
        }
        if (session != null && terminalMessage == null) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Complete") },
            )
        }
    }
}

@Composable
internal fun SessionProgress(
    session: ActiveWearSession,
    compact: Boolean = false,
) {
    var now by remember(session.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingSeconds = session.durationMinutes?.let { durationMinutes ->
        remainingWalkSeconds(
            startedAtEpochMillis = session.startedAtEpochMillis,
            durationMinutes = durationMinutes,
            nowEpochMillis = now,
        )
    }
    LaunchedEffect(session.id, remainingSeconds == 0L) {
        if (session.type == InterventionType.WALK && remainingSeconds == 0L) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (session.type == InterventionType.WALK) "WALK" else "STAIRS",
            fontWeight = FontWeight.Bold,
        )
        if (session.type == InterventionType.WALK) {
            Text(
                "%02d:%02d".format(
                    (remainingSeconds ?: 0L) / 60,
                    (remainingSeconds ?: 0L) % 60,
                ),
                fontSize = if (compact) 30.sp else 42.sp,
                fontWeight = FontWeight.Bold,
            )
            if (remainingSeconds == 0L) {
                Text("Time complete")
            }
        } else {
            Text(
                "${session.targetFloors ?: 0} floors",
                fontSize = if (compact) 28.sp else 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

internal fun remainingWalkSeconds(
    startedAtEpochMillis: Long,
    durationMinutes: Int,
    nowEpochMillis: Long,
): Long {
    val totalSeconds = durationMinutes.coerceAtLeast(0) * 60L
    val elapsedSeconds =
        (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L) / 1_000L
    return (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
}
