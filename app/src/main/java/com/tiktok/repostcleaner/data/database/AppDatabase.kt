package com.tiktok.repostcleaner.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "repost_items")
data class RepostItemEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val authorName: String = "",
    val description: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    val sessionId: Long = 0,
    var isDeleted: Boolean = false,
    var deleteError: String? = null,
    var isSkipped: Boolean = false
)

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalFound: Int = 0,
    val totalDeleted: Int = 0,
    val status: String = "IN_PROGRESS"
)

@Dao
interface RepostDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepost(repost: RepostItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReposts(reposts: List<RepostItemEntity>)

    @Query("SELECT * FROM repost_items WHERE sessionId = :sessionId ORDER BY scannedAt ASC")
    fun getRepostsBySession(sessionId: Long): Flow<List<RepostItemEntity>>

    @Query("SELECT * FROM repost_items WHERE sessionId = :sessionId ORDER BY scannedAt ASC")
    suspend fun getRepostsBySessionSync(sessionId: Long): List<RepostItemEntity>

    @Query("SELECT * FROM repost_items WHERE sessionId = :sessionId AND isDeleted = 0 AND isSkipped = 0 ORDER BY scannedAt ASC")
    suspend fun getPendingReposts(sessionId: Long): List<RepostItemEntity>

    @Query("UPDATE repost_items SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE repost_items SET isSkipped = 1, deleteError = :error WHERE id = :id")
    suspend fun markSkipped(id: String, error: String)

    @Query("SELECT COUNT(*) FROM repost_items WHERE sessionId = :sessionId")
    suspend fun getCountBySession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM repost_items WHERE sessionId = :sessionId AND isDeleted = 1")
    suspend fun getDeletedCount(sessionId: Long): Int

    @Query("DELETE FROM repost_items WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM repost_items WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insert(session: ScanSessionEntity): Long

    @Update
    suspend fun update(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 10")
    suspend fun getRecentSessions(): List<ScanSessionEntity>

    @Query("UPDATE scan_sessions SET totalFound = :count WHERE id = :id")
    suspend fun updateFoundCount(id: Long, count: Int)

    @Query("UPDATE scan_sessions SET totalDeleted = :count WHERE id = :id")
    suspend fun updateDeletedCount(id: Long, count: Int)

    @Query("UPDATE scan_sessions SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun finishSession(id: Long, status: String, completedAt: Long)
}

@Database(
    entities = [RepostItemEntity::class, ScanSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repostDao(): RepostDao
    abstract fun scanSessionDao(): ScanSessionDao
}
