package com.young.metaboliccoach.wear.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataMap
import com.young.metaboliccoach.core.model.WatchState
import com.young.metaboliccoach.core.sync.WatchStateCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.watchStateDataStore by preferencesDataStore("watch_state")

@Singleton
class WearStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val codec: WatchStateCodec,
) {
    val state: Flow<WatchState?> = context.watchStateDataStore.data.map { preferences ->
        preferences[STATE]?.let { encoded ->
            runCatching {
                codec.decode(
                    DataMap.fromByteArray(Base64.decode(encoded, Base64.NO_WRAP)),
                )
            }.getOrNull()
        }
    }

    suspend fun save(state: WatchState) {
        context.watchStateDataStore.edit { preferences ->
            preferences[STATE] = Base64.encodeToString(
                codec.encode(state).toByteArray(),
                Base64.NO_WRAP,
            )
        }
    }

    suspend fun suppressRecommendation() {
        state.first()?.let { current ->
            save(current.copy(recommendation = null))
        }
    }

    companion object {
        private val STATE = stringPreferencesKey("encoded_watch_state")
    }
}
