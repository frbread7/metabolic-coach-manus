package com.young.metaboliccoach.core.sync.di

import com.young.metaboliccoach.core.domain.WatchSyncRepository
import com.young.metaboliccoach.core.sync.DataLayerTransport
import com.young.metaboliccoach.core.sync.DataLayerWatchSyncRepository
import com.young.metaboliccoach.core.sync.PlayServicesDataLayerTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindTransport(impl: PlayServicesDataLayerTransport): DataLayerTransport

    @Binds
    @Singleton
    abstract fun bindWatchSyncRepository(
        impl: DataLayerWatchSyncRepository,
    ): WatchSyncRepository
}
