package com.young.metaboliccoach.core.data.provider

import android.content.Intent
import android.os.Bundle
import com.young.metaboliccoach.core.data.db.GlucoseDao
import com.young.metaboliccoach.core.data.db.GlucoseReadingEntity
import com.young.metaboliccoach.core.domain.SettingsRepository
import com.young.metaboliccoach.core.model.CoachSettings
import com.young.metaboliccoach.core.model.DefaultCoachSettings
import com.young.metaboliccoach.core.model.GlucoseProviderMode
import com.young.metaboliccoach.core.model.GlucoseTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class XdripGlucoseIngestorTest {
    @Test
    fun `valid payload is normalized and stored`() = runTest {
        val timestamp = System.currentTimeMillis() - 1_000
        val previous = reading(
            valueMgDl = 132,
            measuredAtEpochMillis = timestamp - 5 * 60_000,
        )
        val dao = RecordingGlucoseDao(previous)
        val source = "CareSens Air " + "x".repeat(100)
        val intent = xdripIntent(
            estimate = 147.4f,
            timestamp = timestamp,
            slope = rawSlopeForRate(2.4),
            source = source,
        )

        val accepted = ingestor(dao).ingest(intent)

        assertTrue(accepted)
        val stored = dao.inserted.single()
        assertEquals("${XdripGlucoseIngestor.PROVIDER_ID}:$timestamp", stored.id)
        assertEquals(147, stored.valueMgDl)
        assertEquals(GlucoseTrend.RISING.name, stored.trend)
        assertEquals(15, stored.deltaMgDl)
        assertEquals(2.4, stored.rateMgDlPerMinute!!, 0.000_001)
        assertEquals(timestamp, stored.measuredAtEpochMillis)
        assertTrue(stored.receivedAtEpochMillis >= timestamp)
        assertEquals(
            "${XdripGlucoseIngestor.PROVIDER_ID}:${source.take(80)}",
            stored.sourceId,
        )
    }

    @Test
    fun `all slope bands map to the expected trend`() = runTest {
        val cases = listOf(
            -3.0 to GlucoseTrend.RAPIDLY_FALLING,
            -2.0 to GlucoseTrend.FALLING,
            -0.5 to GlucoseTrend.SLIGHTLY_FALLING,
            0.0 to GlucoseTrend.STABLE,
            0.5 to GlucoseTrend.SLIGHTLY_RISING,
            2.0 to GlucoseTrend.RISING,
            3.0 to GlucoseTrend.RAPIDLY_RISING,
        )

        cases.forEachIndexed { index, (slope, expectedTrend) ->
            val dao = RecordingGlucoseDao()
            val timestamp = System.currentTimeMillis() - index

            assertTrue(
                ingestor(dao).ingest(
                    xdripIntent(
                        estimate = 120,
                        timestamp = timestamp,
                        slope = rawSlopeForRate(slope),
                    ),
                ),
            )
            assertEquals(expectedTrend.name, dao.inserted.single().trend)
        }
    }

    @Test
    fun `missing slope is accepted as unknown trend`() = runTest {
        val dao = RecordingGlucoseDao()

        val accepted = ingestor(dao).ingest(
            xdripIntent(
                estimate = 110L,
                timestamp = System.currentTimeMillis(),
                slope = null,
            ),
        )

        assertTrue(accepted)
        assertEquals(GlucoseTrend.UNKNOWN.name, dao.inserted.single().trend)
        assertNull(dao.inserted.single().rateMgDlPerMinute)
    }

    @Test
    fun `older payload does not calculate delta against a newer sample`() = runTest {
        val timestamp = System.currentTimeMillis() - 5_000
        val dao = RecordingGlucoseDao(
            reading(
                valueMgDl = 100,
                measuredAtEpochMillis = timestamp + 1_000,
            ),
        )

        assertTrue(
            ingestor(dao).ingest(
                xdripIntent(
                    estimate = 130,
                    timestamp = timestamp,
                    slope = 0,
                ),
            ),
        )
        assertNull(dao.inserted.single().deltaMgDl)
    }

    @Test
    fun `wrong action and disabled provider are rejected before storage`() = runTest {
        val dao = RecordingGlucoseDao()
        val validPayload = xdripIntent(
            estimate = 120,
            timestamp = System.currentTimeMillis(),
            slope = 0,
        )
        val wrongAction = xdripIntent(
            estimate = 120,
            timestamp = System.currentTimeMillis(),
            slope = 0,
            action = "not.xdrip",
        )

        assertFalse(ingestor(dao).ingest(wrongAction))
        assertFalse(
            ingestor(
                dao = dao,
                providerMode = GlucoseProviderMode.HEALTH_CONNECT,
            ).ingest(validPayload),
        )
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `invalid numeric and timestamp payloads are rejected`() = runTest {
        val now = System.currentTimeMillis()
        val invalidPayloads = listOf(
            xdripIntent(estimate = null, timestamp = now, slope = 0),
            xdripIntent(estimate = Double.NaN, timestamp = now, slope = 0),
            xdripIntent(estimate = 19.9, timestamp = now, slope = 0),
            xdripIntent(estimate = 600.1, timestamp = now, slope = 0),
            xdripIntent(estimate = 120, timestamp = null, slope = 0),
            xdripIntent(
                estimate = 120,
                timestamp = now - 25 * 60 * 60 * 1_000L,
                slope = 0,
            ),
            xdripIntent(
                estimate = 120,
                timestamp = now + 6 * 60 * 1_000L,
                slope = 0,
            ),
            xdripIntent(estimate = 120, timestamp = now, slope = Double.POSITIVE_INFINITY),
            xdripIntent(estimate = 120, timestamp = now, slope = rawSlopeForRate(20.1)),
            xdripIntent(estimate = "120", timestamp = now, slope = 0),
        )

        invalidPayloads.forEach { intent ->
            val dao = RecordingGlucoseDao()
            assertFalse(ingestor(dao).ingest(intent))
            assertTrue(dao.inserted.isEmpty())
        }
    }

    private fun ingestor(
        dao: RecordingGlucoseDao,
        providerMode: GlucoseProviderMode = GlucoseProviderMode.XDRIP_BROADCAST,
    ) = XdripGlucoseIngestor(
        glucoseDao = dao,
        settingsRepository = FixedSettingsRepository(
            DefaultCoachSettings.create().copy(glucoseProviderMode = providerMode),
        ),
    )

    @Suppress("DEPRECATION")
    private fun xdripIntent(
        estimate: Any?,
        timestamp: Any?,
        slope: Any?,
        source: String? = null,
        action: String = XdripGlucoseIngestor.ACTION_NEW_BG_ESTIMATE,
    ): Intent {
        val extras = mock(Bundle::class.java)
        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(action)
        `when`(intent.extras).thenReturn(extras)
        `when`(extras.get(XdripGlucoseIngestor.EXTRA_BG_ESTIMATE)).thenReturn(estimate)
        `when`(extras.get(XdripGlucoseIngestor.EXTRA_TIMESTAMP)).thenReturn(timestamp)
        `when`(extras.get(XdripGlucoseIngestor.EXTRA_BG_SLOPE)).thenReturn(slope)
        `when`(intent.getStringExtra(XdripGlucoseIngestor.EXTRA_SOURCE_INFO))
            .thenReturn(source)
        return intent
    }

    private fun rawSlopeForRate(rateMgDlPerMinute: Double): Double =
        rateMgDlPerMinute / 60_000.0

    private fun reading(
        valueMgDl: Int,
        measuredAtEpochMillis: Long,
    ) = GlucoseReadingEntity(
        id = "previous:$measuredAtEpochMillis",
        valueMgDl = valueMgDl,
        trend = GlucoseTrend.STABLE.name,
        deltaMgDl = null,
        rateMgDlPerMinute = 0.0,
        measuredAtEpochMillis = measuredAtEpochMillis,
        receivedAtEpochMillis = measuredAtEpochMillis,
        sourceId = "test",
    )

    private class FixedSettingsRepository(
        private var settings: CoachSettings,
    ) : SettingsRepository {
        override fun observe(): Flow<CoachSettings> = flowOf(settings)

        override suspend fun update(settings: CoachSettings) {
            this.settings = settings
        }

        override suspend fun reset() {
            settings = DefaultCoachSettings.create()
        }
    }

    private class RecordingGlucoseDao(
        private val latest: GlucoseReadingEntity? = null,
    ) : GlucoseDao {
        val inserted = mutableListOf<GlucoseReadingEntity>()

        override suspend fun getLatest(): GlucoseReadingEntity? = latest

        override suspend fun getLatestForSource(sourcePrefix: String): GlucoseReadingEntity? = latest

        override fun observeLatest(): Flow<GlucoseReadingEntity?> = flowOf(latest)

        override fun observeLatestForSource(
            sourcePrefix: String,
        ): Flow<GlucoseReadingEntity?> = flowOf(latest)

        override suspend fun readingsBetween(
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> = emptyList()

        override suspend fun readingsBetweenForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> = emptyList()

        override suspend fun readingsBetweenExactSource(
            sourceId: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
        ): List<GlucoseReadingEntity> = emptyList()

        override fun observeSinceForSource(
            sourcePrefix: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = flowOf(emptyList())

        override fun observeSinceExactSource(
            sourceId: String,
            startEpochMillis: Long,
        ): Flow<List<GlucoseReadingEntity>> = flowOf(emptyList())

        override suspend fun insertAll(readings: List<GlucoseReadingEntity>) {
            inserted += readings
        }
    }
}
