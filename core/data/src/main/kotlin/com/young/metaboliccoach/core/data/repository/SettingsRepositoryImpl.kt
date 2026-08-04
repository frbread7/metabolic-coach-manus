package com.young.metaboliccoach.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.young.metaboliccoach.core.data.db.RecommendationSnapshotDao
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.SettingsValidator
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsValidator
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.GlycemicPlannerBounds
import com.young.metaboliccoach.core.domain.HistoryExplorerPreferencesRepository
import com.young.metaboliccoach.core.domain.requiresRecommendationInvalidation
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.CoachTheme
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.DefaultNightscoutSettings
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseUnit
import com.young.metaboliccoach.core.model.GlycemicPlannerSettings
import com.young.metaboliccoach.core.model.GlycemicTargetProvenance
import com.young.metaboliccoach.core.model.GlycemicWindow
import com.young.metaboliccoach.core.model.HistoryPeriodPreset
import com.young.metaboliccoach.core.model.NightscoutSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "coach_settings",
    produceMigrations = { listOf(GlucoseProviderModeMigration()) },
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val validator: SettingsValidator,
    private val nightscoutValidator: NightscoutSettingsValidator,
    private val recommendationSnapshotDao: RecommendationSnapshotDao,
) : SettingsRepository,
    NightscoutSettingsRepository,
    GlycemicGoalRepository,
    HistoryExplorerPreferencesRepository {
    override fun observe(): Flow<CoachSettings> = context.settingsDataStore.data.map { values ->
        val defaults = DefaultCoachSettings.create()
        CoachSettings(
            glucoseProviderMode =
                values[Keys.glucoseProviderMode]
                    .enumOrDefault(defaults.glucoseProviderMode)
                    .supportedForCurrentBuild(),
            healthConnectGlucoseOriginPackage =
                values[Keys.healthConnectGlucoseOriginPackage]
                    ?.takeIf(String::isNotBlank),
            glucoseUnit = values[Keys.glucoseUnit].enumOrDefault(defaults.glucoseUnit),
            lowGlucoseThresholdMgDl =
                values[Keys.lowGlucoseThreshold] ?: defaults.lowGlucoseThresholdMgDl,
            targetLowerMgDl = values[Keys.targetLower] ?: defaults.targetLowerMgDl,
            targetUpperMgDl = values[Keys.targetUpper] ?: defaults.targetUpperMgDl,
            rapidRiseThresholdMgDlPerMinute =
                values[Keys.rapidRiseThreshold] ?: defaults.rapidRiseThresholdMgDlPerMinute,
            exercisePauseFallRateMgDlPerMinute =
                values[Keys.exercisePauseFallRate]
                    ?: defaults.exercisePauseFallRateMgDlPerMinute,
            staleReadingMinutes = values[Keys.staleReadingMinutes] ?: defaults.staleReadingMinutes,
            walkingDurationMinutes =
                values[Keys.walkingDurationMinutes] ?: defaults.walkingDurationMinutes,
            stairTargetFloors = values[Keys.stairTargetFloors] ?: defaults.stairTargetFloors,
            dailyStepGoal = values[Keys.dailyStepGoal] ?: defaults.dailyStepGoal,
            dailyFloorGoal = values[Keys.dailyFloorGoal] ?: defaults.dailyFloorGoal,
            prolongedInactivityMinutes =
                values[Keys.prolongedInactivityMinutes] ?: defaults.prolongedInactivityMinutes,
            postMealDelayMinutes =
                values[Keys.postMealDelayMinutes] ?: defaults.postMealDelayMinutes,
            postMealWindowMinutes =
                values[Keys.postMealWindowMinutes] ?: defaults.postMealWindowMinutes,
            reminderCooldownMinutes =
                values[Keys.reminderCooldownMinutes] ?: defaults.reminderCooldownMinutes,
            snoozeMinutes = values[Keys.snoozeMinutes] ?: defaults.snoozeMinutes,
            maximumNotificationsPerDay =
                values[Keys.maximumNotifications] ?: defaults.maximumNotificationsPerDay,
            quietHoursStartMinuteOfDay =
                values[Keys.quietStart] ?: defaults.quietHoursStartMinuteOfDay,
            quietHoursEndMinuteOfDay =
                values[Keys.quietEnd] ?: defaults.quietHoursEndMinuteOfDay,
            workingHoursStartMinuteOfDay =
                values[Keys.workingStart] ?: defaults.workingHoursStartMinuteOfDay,
            workingHoursEndMinuteOfDay =
                values[Keys.workingEnd] ?: defaults.workingHoursEndMinuteOfDay,
            minimumObservationSamples =
                values[Keys.minimumObservationSamples] ?: defaults.minimumObservationSamples,
            minimumTimingBucketSamples =
                values[Keys.minimumTimingBucketSamples]
                    ?: defaults.minimumTimingBucketSamples,
            minimumComparableTimingBuckets =
                values[Keys.minimumComparableTimingBuckets]
                    ?: defaults.minimumComparableTimingBuckets,
            interventionTimingBucketMinutes =
                values[Keys.interventionTimingBucketMinutes]
                    ?: defaults.interventionTimingBucketMinutes,
            postMealTimingBucketMinutes =
                values[Keys.postMealTimingBucketMinutes]
                    ?: defaults.postMealTimingBucketMinutes,
            followUpDelayBucketMinutes =
                values[Keys.followUpDelayBucketMinutes]
                    ?: defaults.followUpDelayBucketMinutes,
            baselineGlucoseBandMgDl =
                values[Keys.baselineGlucoseBandMgDl]
                    ?: defaults.baselineGlucoseBandMgDl,
            interventionFollowUpMinutes =
                values[Keys.interventionFollowUpMinutes]
                    ?: defaults.interventionFollowUpMinutes,
            quickActionExpiryMinutes =
                values[Keys.quickActionExpiryMinutes] ?: defaults.quickActionExpiryMinutes,
            walkingRemindersEnabled =
                values[Keys.walkingEnabled] ?: defaults.walkingRemindersEnabled,
            stairRemindersEnabled =
                values[Keys.stairsEnabled] ?: defaults.stairRemindersEnabled,
            postMealRemindersEnabled =
                values[Keys.postMealEnabled] ?: defaults.postMealRemindersEnabled,
            notificationsEnabled =
                values[Keys.notificationsEnabled] ?: defaults.notificationsEnabled,
            theme = values[Keys.theme].enumOrDefault(defaults.theme),
            fontScale = values[Keys.fontScale] ?: defaults.fontScale,
        )
    }

    override suspend fun update(settings: CoachSettings) {
        val errors = validator.validate(settings)
        require(errors.isEmpty()) { errors.joinToString(separator = " ") }
        context.settingsDataStore.edit { values ->
            values[Keys.glucoseProviderMode] =
                settings.glucoseProviderMode.supportedForCurrentBuild().name
            settings.healthConnectGlucoseOriginPackage?.let {
                values[Keys.healthConnectGlucoseOriginPackage] = it
            } ?: values.remove(Keys.healthConnectGlucoseOriginPackage)
            values[Keys.glucoseUnit] = settings.glucoseUnit.name
            values[Keys.lowGlucoseThreshold] = settings.lowGlucoseThresholdMgDl
            values[Keys.targetLower] = settings.targetLowerMgDl
            values[Keys.targetUpper] = settings.targetUpperMgDl
            values[Keys.rapidRiseThreshold] = settings.rapidRiseThresholdMgDlPerMinute
            values[Keys.exercisePauseFallRate] = settings.exercisePauseFallRateMgDlPerMinute
            values[Keys.staleReadingMinutes] = settings.staleReadingMinutes
            values[Keys.walkingDurationMinutes] = settings.walkingDurationMinutes
            values[Keys.stairTargetFloors] = settings.stairTargetFloors
            values[Keys.dailyStepGoal] = settings.dailyStepGoal
            values[Keys.dailyFloorGoal] = settings.dailyFloorGoal
            values[Keys.prolongedInactivityMinutes] = settings.prolongedInactivityMinutes
            values[Keys.postMealDelayMinutes] = settings.postMealDelayMinutes
            values[Keys.postMealWindowMinutes] = settings.postMealWindowMinutes
            values[Keys.reminderCooldownMinutes] = settings.reminderCooldownMinutes
            values[Keys.snoozeMinutes] = settings.snoozeMinutes
            values[Keys.maximumNotifications] = settings.maximumNotificationsPerDay
            values[Keys.quietStart] = settings.quietHoursStartMinuteOfDay
            values[Keys.quietEnd] = settings.quietHoursEndMinuteOfDay
            values[Keys.workingStart] = settings.workingHoursStartMinuteOfDay
            values[Keys.workingEnd] = settings.workingHoursEndMinuteOfDay
            values[Keys.minimumObservationSamples] = settings.minimumObservationSamples
            values[Keys.minimumTimingBucketSamples] = settings.minimumTimingBucketSamples
            values[Keys.minimumComparableTimingBuckets] =
                settings.minimumComparableTimingBuckets
            values[Keys.interventionTimingBucketMinutes] =
                settings.interventionTimingBucketMinutes
            values[Keys.postMealTimingBucketMinutes] = settings.postMealTimingBucketMinutes
            values[Keys.followUpDelayBucketMinutes] = settings.followUpDelayBucketMinutes
            values[Keys.baselineGlucoseBandMgDl] = settings.baselineGlucoseBandMgDl
            values[Keys.interventionFollowUpMinutes] = settings.interventionFollowUpMinutes
            values[Keys.quickActionExpiryMinutes] = settings.quickActionExpiryMinutes
            values[Keys.walkingEnabled] = settings.walkingRemindersEnabled
            values[Keys.stairsEnabled] = settings.stairRemindersEnabled
            values[Keys.postMealEnabled] = settings.postMealRemindersEnabled
            values[Keys.notificationsEnabled] = settings.notificationsEnabled
            values[Keys.theme] = settings.theme.name
            values[Keys.fontScale] = settings.fontScale
        }
    }

    override fun observeNightscoutSettings(): Flow<NightscoutSettings> =
        context.settingsDataStore.data.map { values ->
            val defaults = DefaultNightscoutSettings.create()
            NightscoutSettings(
                servers = NightscoutSettingsJsonCodec.decodeServers(
                    encoded = values[Keys.nightscoutServers],
                    defaults = defaults.servers,
                ),
                activeServerId = values[Keys.nightscoutActiveServerId]
                    ?: defaults.activeServerId,
                pollingIntervalMinutes = values[Keys.nightscoutPollingInterval]
                    ?: defaults.pollingIntervalMinutes,
                connectionTimeoutSeconds = values[Keys.nightscoutConnectionTimeout]
                    ?: defaults.connectionTimeoutSeconds,
                retryIntervalSeconds = values[Keys.nightscoutRetryInterval]
                    ?: defaults.retryIntervalSeconds,
                maximumRetryAttempts = values[Keys.nightscoutMaximumRetryAttempts]
                    ?: defaults.maximumRetryAttempts,
                requireHttps = values[Keys.nightscoutRequireHttps]
                    ?: defaults.requireHttps,
            )
        }

    override suspend fun updateNightscoutSettings(settings: NightscoutSettings) {
        val normalized = nightscoutValidator.normalize(settings)
        val previous = observeNightscoutSettings().first()
        if (previous.requiresRecommendationInvalidation(normalized)) {
            // Delete first: a failed settings write may suppress a prompt, but it cannot leave a
            // prompt tied to a different glucose source available for execution.
            recommendationSnapshotDao.deleteAll()
        }
        context.settingsDataStore.edit { values ->
            values[Keys.nightscoutServers] =
                NightscoutSettingsJsonCodec.encodeServers(normalized.servers)
            normalized.activeServerId?.let {
                values[Keys.nightscoutActiveServerId] = it
            } ?: values.remove(Keys.nightscoutActiveServerId)
            values[Keys.nightscoutPollingInterval] = normalized.pollingIntervalMinutes
            values[Keys.nightscoutConnectionTimeout] = normalized.connectionTimeoutSeconds
            values[Keys.nightscoutRetryInterval] = normalized.retryIntervalSeconds
            values[Keys.nightscoutMaximumRetryAttempts] = normalized.maximumRetryAttempts
            values[Keys.nightscoutRequireHttps] = normalized.requireHttps
        }
    }

    override fun observeSettings(): Flow<GlycemicPlannerSettings> =
        context.settingsDataStore.data.map { values ->
            val target = values[Keys.glycemicTargetGmi]
                ?.takeIf { it.isFinite() && it in GlycemicPlannerBounds.TARGET_GMI_PERCENT }
            val veryLow = (values[Keys.glycemicVeryLowThreshold]
                ?: GlycemicPlannerSettings().veryLowGlucoseThresholdMgDl)
                .coerceIn(GlycemicPlannerBounds.VERY_LOW_GLUCOSE_MG_DL)
            val low = maxOf(
                (values[Keys.glycemicLowThreshold]
                    ?: GlycemicPlannerSettings().lowGlucoseThresholdMgDl)
                    .coerceIn(GlycemicPlannerBounds.LOW_GLUCOSE_MG_DL),
                veryLow + 1,
            ).coerceAtMost(GlycemicPlannerBounds.LOW_GLUCOSE_MG_DL.last)
            val maximumLow = values[Keys.glycemicMaximumLowPercent]
                ?.takeIf { it.isFinite() }
                ?.coerceIn(
                    GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.start.toDouble(),
                    GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
                )
                ?: GlycemicPlannerSettings().maximumLowGlucosePercent
            val maximumVeryLow = (values[Keys.glycemicMaximumVeryLowPercent]
                ?.takeIf { it.isFinite() }
                ?.coerceIn(
                    GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.start.toDouble(),
                    GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
                )
                ?: GlycemicPlannerSettings().maximumVeryLowGlucosePercent)
                .coerceAtMost(maximumLow)
            GlycemicPlannerSettings(
                targetGmiPercent = target,
                targetProvenance = values[Keys.glycemicTargetProvenance]
                    .enumOrNull<GlycemicTargetProvenance>()
                    ?.takeIf { target != null },
                horizon = GlycemicWindow.fromDays(values[Keys.glycemicHorizonDays] ?: 30)
                    ?.takeIf { it in GlycemicPlannerBounds.SCENARIO_HORIZONS }
                    ?: GlycemicWindow.DAYS_30,
                lowGlucoseThresholdMgDl = low,
                veryLowGlucoseThresholdMgDl = veryLow,
                maximumLowGlucosePercent = maximumLow,
                maximumVeryLowGlucosePercent = maximumVeryLow,
            )
        }

    override suspend fun updateSettings(settings: GlycemicPlannerSettings) {
        require(settings.lowGlucoseThresholdMgDl > settings.veryLowGlucoseThresholdMgDl) {
            "The low-glucose threshold must be above the very-low threshold."
        }
        require(settings.horizon in GlycemicPlannerBounds.SCENARIO_HORIZONS) {
            "The planner horizon must be 30, 60, or 90 days."
        }
        require(settings.lowGlucoseThresholdMgDl in GlycemicPlannerBounds.LOW_GLUCOSE_MG_DL) {
            "The planner low-glucose boundary is outside the supported range."
        }
        require(
            settings.veryLowGlucoseThresholdMgDl in GlycemicPlannerBounds.VERY_LOW_GLUCOSE_MG_DL,
        ) {
            "The planner very-low boundary is outside the supported range."
        }
        require(
            settings.maximumLowGlucosePercent in
                GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.start.toDouble()..
                GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
        ) {
            "The maximum low-glucose percentage must be between 0 and 20."
        }
        require(
            settings.maximumVeryLowGlucosePercent in
                GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.start.toDouble()..
                GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
        ) {
            "The maximum very-low-glucose percentage must be between 0 and 10."
        }
        require(settings.maximumVeryLowGlucosePercent <= settings.maximumLowGlucosePercent) {
            "The maximum very-low-glucose percentage cannot exceed the maximum low-glucose percentage."
        }
        settings.targetGmiPercent?.let { target ->
            require(target.isFinite() && target in GlycemicPlannerBounds.TARGET_GMI_PERCENT) {
                "The GMI target must be between 3.5 and 15.0."
            }
            requireNotNull(settings.targetProvenance) {
                "A target provenance is required when a GMI target is set."
            }
        } ?: require(settings.targetProvenance == null) {
            "Target provenance must be cleared when no GMI target is set."
        }
        context.settingsDataStore.edit { values ->
            settings.targetGmiPercent?.let {
                values[Keys.glycemicTargetGmi] = it
            } ?: values.remove(Keys.glycemicTargetGmi)
            settings.targetProvenance?.let {
                values[Keys.glycemicTargetProvenance] = it.name
            } ?: values.remove(Keys.glycemicTargetProvenance)
            values[Keys.glycemicHorizonDays] = settings.horizon.days
            values[Keys.glycemicLowThreshold] = settings.lowGlucoseThresholdMgDl
            values[Keys.glycemicVeryLowThreshold] = settings.veryLowGlucoseThresholdMgDl
            values[Keys.glycemicMaximumLowPercent] = settings.maximumLowGlucosePercent
            values[Keys.glycemicMaximumVeryLowPercent] = settings.maximumVeryLowGlucosePercent
        }
    }

    override suspend fun updateSafetySettings(settings: GlycemicPlannerSettings) {
        require(settings.lowGlucoseThresholdMgDl > settings.veryLowGlucoseThresholdMgDl) {
            "The low-glucose threshold must be above the very-low threshold."
        }
        require(settings.lowGlucoseThresholdMgDl in GlycemicPlannerBounds.LOW_GLUCOSE_MG_DL) {
            "The planner low-glucose boundary is outside the supported range."
        }
        require(
            settings.veryLowGlucoseThresholdMgDl in GlycemicPlannerBounds.VERY_LOW_GLUCOSE_MG_DL,
        ) {
            "The planner very-low boundary is outside the supported range."
        }
        require(
            settings.maximumLowGlucosePercent in
                GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.start.toDouble()..
                GlycemicPlannerBounds.MAXIMUM_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
        ) {
            "The maximum low-glucose percentage must be between 0 and 20."
        }
        require(
            settings.maximumVeryLowGlucosePercent in
                GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.start.toDouble()..
                GlycemicPlannerBounds.MAXIMUM_VERY_LOW_EXPOSURE_PERCENT.endInclusive.toDouble(),
        ) {
            "The maximum very-low-glucose percentage must be between 0 and 10."
        }
        require(settings.maximumVeryLowGlucosePercent <= settings.maximumLowGlucosePercent) {
            "The maximum very-low-glucose percentage cannot exceed the maximum low-glucose percentage."
        }
        context.settingsDataStore.edit { values ->
            values[Keys.glycemicLowThreshold] = settings.lowGlucoseThresholdMgDl
            values[Keys.glycemicVeryLowThreshold] = settings.veryLowGlucoseThresholdMgDl
            values[Keys.glycemicMaximumLowPercent] = settings.maximumLowGlucosePercent
            values[Keys.glycemicMaximumVeryLowPercent] = settings.maximumVeryLowGlucosePercent
        }
    }

    override fun observeLastFixedPreset(): Flow<HistoryPeriodPreset> =
        context.settingsDataStore.data.map { values ->
            values[Keys.historyLastFixedPreset]
                .enumOrDefault(HistoryPeriodPreset.HOURS_24)
                .takeUnless { it == HistoryPeriodPreset.CUSTOM }
                ?: HistoryPeriodPreset.HOURS_24
        }

    override suspend fun updateLastFixedPreset(preset: HistoryPeriodPreset) {
        require(preset != HistoryPeriodPreset.CUSTOM) {
            "Only a fixed history window may be persisted."
        }
        context.settingsDataStore.edit { values ->
            values[Keys.historyLastFixedPreset] = preset.name
        }
    }

    override suspend fun reset() {
        recommendationSnapshotDao.deleteAll()
        context.settingsDataStore.edit { values ->
            values.clear()
        }
    }

    private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
        this?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() } ?: default

    private object Keys {
        val glucoseProviderMode = glucoseProviderModePreferenceKey
        val healthConnectGlucoseOriginPackage =
            stringPreferencesKey("health_connect_glucose_origin_package")
        val glucoseUnit = stringPreferencesKey("glucose_unit")
        val lowGlucoseThreshold = intPreferencesKey("low_glucose_threshold")
        val targetLower = intPreferencesKey("target_lower")
        val targetUpper = intPreferencesKey("target_upper")
        val rapidRiseThreshold = doublePreferencesKey("rapid_rise_threshold")
        val exercisePauseFallRate = doublePreferencesKey("exercise_pause_fall_rate")
        val staleReadingMinutes = intPreferencesKey("stale_reading_minutes")
        val walkingDurationMinutes = intPreferencesKey("walking_duration_minutes")
        val stairTargetFloors = intPreferencesKey("stair_target_floors")
        val dailyStepGoal = intPreferencesKey("daily_step_goal")
        val dailyFloorGoal = intPreferencesKey("daily_floor_goal")
        val prolongedInactivityMinutes = intPreferencesKey("prolonged_inactivity_minutes")
        val postMealDelayMinutes = intPreferencesKey("post_meal_delay_minutes")
        val postMealWindowMinutes = intPreferencesKey("post_meal_window_minutes")
        val reminderCooldownMinutes = intPreferencesKey("reminder_cooldown_minutes")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val maximumNotifications = intPreferencesKey("maximum_notifications")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val workingStart = intPreferencesKey("working_start")
        val workingEnd = intPreferencesKey("working_end")
        val minimumObservationSamples = intPreferencesKey("minimum_observation_samples")
        val minimumTimingBucketSamples =
            intPreferencesKey("minimum_timing_bucket_samples")
        val minimumComparableTimingBuckets =
            intPreferencesKey("minimum_comparable_timing_buckets")
        val interventionTimingBucketMinutes =
            intPreferencesKey("intervention_timing_bucket_minutes")
        val postMealTimingBucketMinutes =
            intPreferencesKey("post_meal_timing_bucket_minutes")
        val followUpDelayBucketMinutes =
            intPreferencesKey("follow_up_delay_bucket_minutes")
        val baselineGlucoseBandMgDl =
            intPreferencesKey("baseline_glucose_band_mg_dl")
        val interventionFollowUpMinutes = intPreferencesKey("intervention_follow_up_minutes")
        val quickActionExpiryMinutes = intPreferencesKey("quick_action_expiry_minutes")
        val walkingEnabled = booleanPreferencesKey("walking_enabled")
        val stairsEnabled = booleanPreferencesKey("stairs_enabled")
        val postMealEnabled = booleanPreferencesKey("post_meal_enabled")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("font_scale")
        val nightscoutServers = stringPreferencesKey("nightscout_servers_json")
        val nightscoutActiveServerId = stringPreferencesKey("nightscout_active_server_id")
        val nightscoutPollingInterval = intPreferencesKey("nightscout_polling_interval_minutes")
        val nightscoutConnectionTimeout =
            intPreferencesKey("nightscout_connection_timeout_seconds")
        val nightscoutRetryInterval = intPreferencesKey("nightscout_retry_interval_seconds")
        val nightscoutMaximumRetryAttempts =
            intPreferencesKey("nightscout_maximum_retry_attempts")
        val nightscoutRequireHttps = booleanPreferencesKey("nightscout_require_https")
        val glycemicTargetGmi = doublePreferencesKey("glycemic_target_gmi")
        val glycemicTargetProvenance = stringPreferencesKey("glycemic_target_provenance")
        val glycemicHorizonDays = intPreferencesKey("glycemic_horizon_days")
        val glycemicLowThreshold = intPreferencesKey("glycemic_low_threshold")
        val glycemicVeryLowThreshold = intPreferencesKey("glycemic_very_low_threshold")
        val glycemicMaximumLowPercent = doublePreferencesKey("glycemic_maximum_low_percent")
        val glycemicMaximumVeryLowPercent =
            doublePreferencesKey("glycemic_maximum_very_low_percent")
        val historyLastFixedPreset = stringPreferencesKey("history_last_fixed_preset")
    }
}

private inline fun <reified T : Enum<T>> String?.enumOrNull(): T? =
    this?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
