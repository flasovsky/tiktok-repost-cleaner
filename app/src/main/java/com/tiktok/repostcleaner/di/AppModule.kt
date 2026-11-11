package com.tiktok.repostcleaner.di

import android.content.Context
import androidx.room.Room
import com.tiktok.repostcleaner.data.database.AppDatabase
import com.tiktok.repostcleaner.data.database.RepostDao
import com.tiktok.repostcleaner.data.database.ScanSessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "repost_cleaner_db"
        ).build()
    }

    @Provides
    fun provideRepostDao(db: AppDatabase): RepostDao = db.repostDao()

    @Provides
    fun provideScanSessionDao(db: AppDatabase): ScanSessionDao = db.scanSessionDao()
}
