package com.cloudflare.manager.di

import android.content.Context
import androidx.room.Room
import com.cloudflare.manager.data.local.AccountDatabase
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
    fun provideAccountDatabase(@ApplicationContext context: Context): AccountDatabase {
        return Room.databaseBuilder(
            context,
            AccountDatabase::class.java,
            "cloudflare_manager.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: AccountDatabase) = database.accountDao()
}
