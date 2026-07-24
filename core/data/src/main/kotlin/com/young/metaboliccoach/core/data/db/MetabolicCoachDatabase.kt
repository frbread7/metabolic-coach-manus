package com.young.metaboliccoach.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GlucoseReadingEntity::class,
        ActivitySnapshotEntity::class,
        InterventionSessionEntity::class,
        MealMarkerEntity::class,
        CoachStateEntity::class,
        RecommendationSnapshotEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class MetabolicCoachDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
    abstract fun activityDao(): ActivityDao
    abstract fun interventionDao(): InterventionDao
    abstract fun mealDao(): MealDao
    abstract fun coachStateDao(): CoachStateDao
    abstract fun recommendationSnapshotDao(): RecommendationSnapshotDao
}
