package com.tiktok.repostcleaner.data.repository

import com.tiktok.repostcleaner.data.database.*
import com.tiktok.repostcleaner.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepostRepository @Inject constructor(
    private val repostDao: RepostDao,
    private val sessionDao: ScanSessionDao
) {
    suspend fun startNewSession(): Long {
        val session = ScanSessionEntity()
        return sessionDao.insert(session)
    }

    suspend fun getLatestSession(): ScanSessionEntity? = sessionDao.getLatestSession()

    suspend fun finishSession(id: Long, status: ScanStatus) {
        sessionDao.finishSession(id, status.name, System.currentTimeMillis())
    }

    suspend fun saveRepost(item: RepostItem, sessionId: Long) {
        repostDao.insertRepost(
            RepostItemEntity(
                id = item.id,
                videoId = item.videoId,
                authorName = item.authorName,
                description = item.description,
                scannedAt = item.scannedAt,
                sessionId = sessionId
            )
        )
        sessionDao.updateFoundCount(sessionId, repostDao.getCountBySession(sessionId))
    }

    suspend fun repostExists(id: String): Boolean = repostDao.exists(id)

    fun getRepostsBySession(sessionId: Long): Flow<List<RepostItemEntity>> =
        repostDao.getRepostsBySession(sessionId)

    suspend fun getPendingReposts(sessionId: Long): List<RepostItemEntity> =
        repostDao.getPendingReposts(sessionId)

    suspend fun markDeleted(id: String, sessionId: Long) {
        repostDao.markDeleted(id)
        sessionDao.updateDeletedCount(sessionId, repostDao.getDeletedCount(sessionId))
    }

    suspend fun markSkipped(id: String, error: String) {
        repostDao.markSkipped(id, error)
    }

    suspend fun getSessionStats(sessionId: Long): Triple<Int, Int, Int> {
        val total = repostDao.getCountBySession(sessionId)
        val deleted = repostDao.getDeletedCount(sessionId)
        val all = repostDao.getRepostsBySessionSync(sessionId)
        val skipped = all.count { it.isSkipped }
        return Triple(total, deleted, skipped)
    }

    suspend fun clearSession(sessionId: Long) {
        repostDao.deleteBySession(sessionId)
    }
}
