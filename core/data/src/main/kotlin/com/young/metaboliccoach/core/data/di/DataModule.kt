package com.young.metaboliccoach.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.young.metaboliccoach.core.data.db.MetabolicCoachDatabase
import com.young.metaboliccoach.core.data.db.GlycemicPlanningMilestoneDao
import com.young.metaboliccoach.core.data.provider.CareSensAirProvider
import com.young.metaboliccoach.core.data.provider.GlucoseProvider
import com.young.metaboliccoach.core.data.provider.HealthConnectGlucoseProvider
import com.young.metaboliccoach.core.data.provider.XdripBroadcastGlucoseProvider
import com.young.metaboliccoach.core.data.provider.nightscout.CoroutineNightscoutRetrySleeper
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutApiClient
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutProvider
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutRequestAuthenticator
import com.young.metaboliccoach.core.data.provider.nightscout.NightscoutRetrySleeper
import com.young.metaboliccoach.core.data.provider.nightscout.NoOpNightscoutRequestAuthenticator
import com.young.metaboliccoach.core.data.provider.nightscout.OkHttpNightscoutApiClient
import com.young.metaboliccoach.core.data.repository.ActivityRepositoryImpl
import com.young.metaboliccoach.core.data.repository.CoachingRepositoryImpl
import com.young.metaboliccoach.core.data.repository.GlucoseRepositoryImpl
import com.young.metaboliccoach.core.data.repository.GlycemicPlanningMilestoneRepositoryImpl
import com.young.metaboliccoach.core.data.repository.PersonalDataRepositoryImpl
import com.young.metaboliccoach.core.data.repository.SettingsRepositoryImpl
import com.young.metaboliccoach.core.data.repository.SystemCoachTimeSource
import com.young.metaboliccoach.core.domain.ActivityRepository
import com.young.metaboliccoach.core.domain.CoachTimeSource
import com.young.metaboliccoach.core.domain.CoachRuleEngine
import com.young.metaboliccoach.core.domain.CoachingRepository
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.GlycemicPlanningMilestoneRepository
import com.young.metaboliccoach.core.domain.GlucoseRepository
import com.young.metaboliccoach.core.domain.ObservationAnalyzer
import com.young.metaboliccoach.core.domain.NightscoutSettingsRepository
import com.young.metaboliccoach.core.domain.NightscoutSettingsValidator
import com.young.metaboliccoach.core.domain.PersonalDataRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.domain.SettingsValidator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindGlucoseRepository(impl: GlucoseRepositoryImpl): GlucoseRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNightscoutSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): NightscoutSettingsRepository

    @Binds
    @Singleton
    abstract fun bindGlycemicGoalRepository(
        impl: SettingsRepositoryImpl,
    ): GlycemicGoalRepository

    @Binds
    @Singleton
    abstract fun bindGlycemicPlanningMilestoneRepository(
        impl: GlycemicPlanningMilestoneRepositoryImpl,
    ): GlycemicPlanningMilestoneRepository

    @Binds
    @Singleton
    abstract fun bindCoachingRepository(impl: CoachingRepositoryImpl): CoachingRepository

    @Binds
    @Singleton
    abstract fun bindPersonalDataRepository(
        impl: PersonalDataRepositoryImpl,
    ): PersonalDataRepository

    @Binds
    @Singleton
    abstract fun bindCoachTimeSource(impl: SystemCoachTimeSource): CoachTimeSource

    @Binds
    @Singleton
    abstract fun bindNightscoutApiClient(
        impl: OkHttpNightscoutApiClient,
    ): NightscoutApiClient

    @Binds
    @Singleton
    abstract fun bindNightscoutAuthenticator(
        impl: NoOpNightscoutRequestAuthenticator,
    ): NightscoutRequestAuthenticator

    @Binds
    @Singleton
    abstract fun bindNightscoutRetrySleeper(
        impl: CoroutineNightscoutRetrySleeper,
    ): NightscoutRetrySleeper

    @Binds
    @IntoSet
    abstract fun bindNightscoutProvider(impl: NightscoutProvider): GlucoseProvider

    @Binds
    @IntoSet
    abstract fun bindHealthConnectGlucoseProvider(
        impl: HealthConnectGlucoseProvider,
    ): GlucoseProvider

    @Binds
    @IntoSet
    abstract fun bindXdripGlucoseProvider(
        impl: XdripBroadcastGlucoseProvider,
    ): GlucoseProvider

    @Binds
    @IntoSet
    abstract fun bindCareSensGlucoseProvider(
        impl: CareSensAirProvider,
    ): GlucoseProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MetabolicCoachDatabase = Room.databaseBuilder(
        context,
        MetabolicCoachDatabase::class.java,
        "metabolic-coach.db",
    ).addMigrations(*DatabaseMigrations.ALL).build()

    @Provides
    fun provideGlucoseDao(database: MetabolicCoachDatabase) = database.glucoseDao()

    @Provides
    fun provideActivityDao(database: MetabolicCoachDatabase) = database.activityDao()

    @Provides
    fun provideInterventionDao(database: MetabolicCoachDatabase) = database.interventionDao()

    @Provides
    fun provideMealDao(database: MetabolicCoachDatabase) = database.mealDao()

    @Provides
    fun provideCoachStateDao(database: MetabolicCoachDatabase) = database.coachStateDao()

    @Provides
    fun provideRecommendationSnapshotDao(database: MetabolicCoachDatabase) =
        database.recommendationSnapshotDao()

    @Provides
    fun provideGlycemicPlanningMilestoneDao(database: MetabolicCoachDatabase) =
        database.glycemicPlanningMilestoneDao()

    @Provides
    @Singleton
    fun provideGlycemicPlanningMilestoneDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("glycemic_planning_milestones")
    }

    @Provides
    fun provideRuleEngine() = CoachRuleEngine()

    @Provides
    fun provideObservationAnalyzer() = ObservationAnalyzer()

    @Provides
    fun provideSettingsValidator() = SettingsValidator()

    @Provides
    fun provideNightscoutSettingsValidator() = NightscoutSettingsValidator()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

}

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN followUpDueAtEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN followUpReadingAtEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN followUpFinalizedAtEpochMillis INTEGER",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_glucose_readings_measuredAtEpochMillis " +
                    "ON glucose_readings (measuredAtEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_glucose_readings_sourceId_measuredAtEpochMillis " +
                    "ON glucose_readings (sourceId, measuredAtEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_intervention_sessions_status_startedAtEpochMillis " +
                    "ON intervention_sessions (status, startedAtEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_intervention_sessions_status_followUpDueAtEpochMillis " +
                    "ON intervention_sessions (status, followUpDueAtEpochMillis)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN baselineGlucoseReadingId TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN baselineGlucoseMeasuredAtEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN baselineGlucoseSourceId TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN followUpGlucoseReadingId TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN followUpGlucoseSourceId TEXT",
            )
            db.execSQL(
                "ALTER TABLE coach_state ADD COLUMN lastRecommendationId TEXT",
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE activity_snapshots " +
                    "ADD COLUMN exerciseSessionCountToday INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE activity_snapshots " +
                    "ADD COLUMN exerciseDurationMinutesToday INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE intervention_sessions ADD COLUMN recommendationId TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions ADD COLUMN recommendationReason TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN recommendationAlgorithmVersion INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN recommendationCreatedAtEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN recommendationValidUntilEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions ADD COLUMN triggerContextId TEXT",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions ADD COLUMN triggerAtEpochMillis INTEGER",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN baselineEffectiveRateMgDlPerMinute REAL",
            )
            db.execSQL(
                "ALTER TABLE intervention_sessions " +
                    "ADD COLUMN lowGlucoseThresholdMgDlAtStart INTEGER",
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS recommendation_snapshots (
                    id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    validUntilEpochMillis INTEGER NOT NULL,
                    interventionType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    actionLabel TEXT NOT NULL,
                    durationMinutes INTEGER,
                    targetFloors INTEGER,
                    algorithmVersion INTEGER NOT NULL,
                    triggerContextId TEXT,
                    triggerAtEpochMillis INTEGER,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_recommendation_snapshots_validUntilEpochMillis " +
                    "ON recommendation_snapshots (validUntilEpochMillis)",
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS glycemic_planning_milestones (
                    id TEXT NOT NULL,
                    title TEXT,
                    targetGmiPercent REAL NOT NULL,
                    targetProvenance TEXT NOT NULL,
                    targetDateEpochMillis INTEGER NOT NULL,
                    originalHorizonDays INTEGER NOT NULL,
                    lifecycleState TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    archivedAtEpochMillis INTEGER,
                    calculationContractVersion INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_glycemic_planning_milestones_lifecycleState_targetDateEpochMillis " +
                    "ON glycemic_planning_milestones (lifecycleState, targetDateEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_glycemic_planning_milestones_createdAtEpochMillis " +
                    "ON glycemic_planning_milestones (createdAtEpochMillis)",
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
}
