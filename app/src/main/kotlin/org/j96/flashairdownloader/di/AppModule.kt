package org.j96.flashairdownloader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Scope for work that outlives any screen: the network callback and the sync
     * engine. A [SupervisorJob] keeps one failure from cancelling the rest.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Injected rather than read statically, so that "when" is testable. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
