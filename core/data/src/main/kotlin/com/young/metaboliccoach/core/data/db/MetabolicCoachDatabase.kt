package com.young.metaboliccoach.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GlucoseReadingEntity::class,
        GlucoseHistorySettingsEntity::class,
        GlucoseHistoryBackfillEntity::class,
        ActivitySnapshotEntity::class,
        InterventionSessionEntity::class,
        MealMarkerEntity::class,
        CoachStateEntity::class,
        RecommendationSnapshotEntity::class,
        GlycemicPlanningMilestoneEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class MetabolicCoachDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
    abstract fun glucoseHistoryDao(): GlucoseHistoryDao
    abstract fun activityDao(): ActivityDao
    abstract fun interventionDao(): InterventionDao
    abstract fun mealDao(): MealDao
    abstract fun coachStateDao(): CoachStateDao
    abstract fun recommendationSnapshotDao(): RecommendationSnapshotDao
    abstract fun glycemicPlanningMilestoneDao(): GlycemicPlanningMilestoneDao
}
