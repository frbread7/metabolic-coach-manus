package com.young.metaboliccoach.core.data.repository

import android.database.Cursor
import androidx.room.withTransaction
import com.young.metaboliccoach.core.data.db.MetabolicCoachDatabase
import com.young.metaboliccoach.core.domain.GlycemicGoalRepository
import com.young.metaboliccoach.core.domain.GlycemicPlanningMilestoneRepository
import com.young.metaboliccoach.core.domain.PersonalDataRepository
import com.young.metaboliccoach.core.domain.SettingsRepository
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class PersonalDataRepositoryImpl @Inject constructor(
    private val database: MetabolicCoachDatabase,
    private val settingsRepository: SettingsRepository,
    private val glycemicGoalRepository: GlycemicGoalRepository,
    private val milestoneRepository: GlycemicPlanningMilestoneRepository,
) : PersonalDataRepository {
    override suspend fun writeJsonExport(
        exportedAtEpochMillis: Long,
        destination: Appendable,
    ) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.observe().first()
        val glycemicPlannerSettings = glycemicGoalRepository.observeSettings().first()
        val milestones = milestoneRepository.observeMilestones().first()
        val selectedMilestoneId = milestoneRepository.observeSelectedMilestoneId().first()
        database.withTransaction {
            val readableDatabase = database.openHelper.readableDatabase
            val writer = PersonalDataJsonWriter(destination)
            writer.beginDocument(
                exportedAtEpochMillis = exportedAtEpochMillis,
                databaseSchemaVersion = readableDatabase.version,
                settings = settings,
                glycemicPlannerSettings = glycemicPlannerSettings,
                milestones = milestones,
                selectedMilestoneId = selectedMilestoneId,
            )
            EXPORT_TABLES.forEach { table ->
                readableDatabase.query(table.query).use { cursor ->
                    writer.beginTable(table.name)
                    val columnNames = cursor.columnNames.toList()
                    while (cursor.moveToNext()) {
                        writer.writeRow(
                            columnNames = columnNames,
                            values = columnNames.indices.map { columnIndex ->
                                cursor.exportValue(columnIndex)
                            },
                        )
                    }
                    writer.endTable()
                }
            }
            writer.endDocument()
        }
    }

    override suspend fun eraseAll() {
        withContext(Dispatchers.IO + NonCancellable) {
            // Clear health history first. If settings storage later fails, the sensitive records
            // are still gone and a retry can finish resetting preferences.
            database.clearAllTables()
            settingsRepository.reset()
            milestoneRepository.reset()
        }
    }

    private fun Cursor.exportValue(columnIndex: Int): ExportValue =
        when (getType(columnIndex)) {
            Cursor.FIELD_TYPE_NULL -> ExportValue.Null
            Cursor.FIELD_TYPE_INTEGER -> ExportValue.Integer(getLong(columnIndex))
            Cursor.FIELD_TYPE_FLOAT -> ExportValue.Decimal(getDouble(columnIndex))
            Cursor.FIELD_TYPE_STRING -> ExportValue.Text(getString(columnIndex))
            Cursor.FIELD_TYPE_BLOB -> ExportValue.Binary(
                Base64.getEncoder().encodeToString(getBlob(columnIndex)),
            )
            else -> error("Unsupported SQLite value type at column $columnIndex.")
        }

    private data class ExportTable(
        val name: String,
        val query: String,
    )

    private companion object {
        val EXPORT_TABLES = listOf(
            ExportTable(
                name = "glucose_readings",
                query = """
                    SELECT * FROM glucose_readings
                    ORDER BY measuredAtEpochMillis ASC, id ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "glucose_history_settings",
                query = """
                    SELECT * FROM glucose_history_settings
                    ORDER BY singletonId ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "glucose_history_backfill_state",
                query = """
                    SELECT * FROM glucose_history_backfill_state
                    ORDER BY sourceId ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "activity_snapshots",
                query = """
                    SELECT * FROM activity_snapshots
                    ORDER BY dayStartEpochMillis ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "intervention_sessions",
                query = """
                    SELECT * FROM intervention_sessions
                    ORDER BY startedAtEpochMillis ASC, id ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "meal_markers",
                query = """
                    SELECT * FROM meal_markers
                    ORDER BY occurredAtEpochMillis ASC, id ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "coach_state",
                query = """
                    SELECT * FROM coach_state
                    ORDER BY singletonId ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "recommendation_snapshots",
                query = """
                    SELECT * FROM recommendation_snapshots
                    ORDER BY createdAtEpochMillis ASC, id ASC
                """.trimIndent(),
            ),
            ExportTable(
                name = "glycemic_planning_milestones",
                query = """
                    SELECT * FROM glycemic_planning_milestones
                    ORDER BY lifecycleState ASC, targetDateEpochMillis ASC,
                             createdAtEpochMillis ASC, id ASC
                """.trimIndent(),
            ),
        )
    }
}
