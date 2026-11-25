package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    PendingReportDeletionEntity::class,
    CachedMedicalReportEntity::class,
    CachedReportExplanationEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class NexzyLocalDatabase : RoomDatabase() {
  abstract fun reportLocalDao(): ReportLocalDao

  companion object {
    private const val DATABASE_NAME = "nexzyhealth_local.db"

    @Volatile
    private var instance: NexzyLocalDatabase? = null

    fun getInstance(context: Context): NexzyLocalDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          NexzyLocalDatabase::class.java,
          DATABASE_NAME
        )
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
          .also { instance = it }
      }
    }
  }
}
