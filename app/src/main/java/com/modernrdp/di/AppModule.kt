package com.modernrdp.di

import android.content.Context
import androidx.room.Room
import com.modernrdp.data.local.ConnectionDao
import com.modernrdp.data.local.GroupDao
import com.modernrdp.data.local.MIGRATION_1_2
import com.modernrdp.data.local.RdpDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RdpDatabase =
        Room.databaseBuilder(
            context,
            RdpDatabase::class.java,
            "modernrdp.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideConnectionDao(database: RdpDatabase): ConnectionDao =
        database.connectionDao()

    @Provides
    @Singleton
    fun provideGroupDao(database: RdpDatabase): GroupDao =
        database.groupDao()
}
