package com.young.metaboliccoach.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.young.metaboliccoach.core.model.QuickActionType
import com.young.metaboliccoach.wear.ui.WearActionResultStatus
import com.young.metaboliccoach.wear.ui.WearViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class QuickActionActivity : ComponentActivity() {
    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        performIntentAction(intent)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val actionResult by viewModel.actionResult.collectAsState()
            androidx.compose.runtime.LaunchedEffect(actionResult) {
                val result = actionResult ?: return@LaunchedEffect
                if (
                    result.status == WearActionResultStatus.REJECTED ||
                    result.type == QuickActionType.SNOOZE ||
                    result.type == QuickActionType.MARK_COMPLETED
                ) {
                    delay(1_500)
                    finish()
                }
            }
            val terminalMessage = actionResult?.takeIf { result ->
                result.status == WearActionResultStatus.REJECTED ||
                    result.type == QuickActionType.SNOOZE ||
                    result.type == QuickActionType.MARK_COMPLETED
            }?.message
            WearTheme(state.watchState.settings) {
                SessionScreen(
                    state = state,
                    terminalMessage = terminalMessage,
                    onComplete = {
                        viewModel.perform(QuickActionType.MARK_COMPLETED)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        performIntentAction(intent)
    }

    private fun performIntentAction(intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { runCatching { QuickActionType.valueOf(it) }.getOrNull() }
            ?: run {
                finish()
                return
            }
        val recommendationId = intent.getStringExtra(EXTRA_RECOMMENDATION_ID)
        val recommendationValidUntilEpochMillis =
            intent.getLongExtra(EXTRA_RECOMMENDATION_VALID_UNTIL, Long.MIN_VALUE)
                .takeIf { intent.hasExtra(EXTRA_RECOMMENDATION_VALID_UNTIL) }
        viewModel.performExternal(
            requestKey = listOf(
                action.name,
                recommendationId.orEmpty(),
                recommendationValidUntilEpochMillis?.toString().orEmpty(),
            ).joinToString("|"),
            type = action,
            recommendationId = recommendationId,
            recommendationValidUntilEpochMillis = recommendationValidUntilEpochMillis,
        )
    }

    companion object {
        private const val EXTRA_ACTION = "quick_action"
        private const val EXTRA_RECOMMENDATION_ID = "recommendation_id"
        private const val EXTRA_RECOMMENDATION_VALID_UNTIL = "recommendation_valid_until"

        fun intent(
            context: Context,
            type: QuickActionType,
            recommendationId: String? = null,
            recommendationValidUntilEpochMillis: Long? = null,
        ) = Intent(context, QuickActionActivity::class.java)
                .setAction("com.young.metaboliccoach.wear.QUICK_ACTION")
                .putExtra(EXTRA_ACTION, type.name)
                .apply {
                    recommendationId?.let { putExtra(EXTRA_RECOMMENDATION_ID, it) }
                    recommendationValidUntilEpochMillis?.let {
                        putExtra(EXTRA_RECOMMENDATION_VALID_UNTIL, it)
                    }
                }
    }
}
