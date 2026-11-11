package com.tiktok.repostcleaner.domain.model

data class RepostItem(
    val id: String,
    val videoId: String,
    val authorName: String = "",
    val description: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    var isDeleted: Boolean = false,
    var deleteError: String? = null
)

data class ScanSession(
    val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalFound: Int = 0,
    val totalDeleted: Int = 0,
    val status: ScanStatus = ScanStatus.IN_PROGRESS
)

enum class ScanStatus {
    IN_PROGRESS,
    COMPLETED,
    PAUSED,
    FAILED
}

data class ScanProgress(
    val totalFound: Int = 0,
    val itemsScrolled: Int = 0,
    val status: String = "",
    val isComplete: Boolean = false
)

data class DeletionProgress(
    val total: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val current: Int = 0,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false
) {
    val remaining: Int get() = total - deleted - failed - skipped
    val percentage: Float get() = if (total > 0) (current.toFloat() / total.toFloat()) * 100f else 0f
}

enum class AppState {
    IDLE,
    CHECKING_TIKTOK,
    TIKTOK_NOT_INSTALLED,
    TIKTOK_NOT_AUTHORIZED,
    READY,
    SCANNING,
    SCAN_COMPLETE,
    DELETING,
    DELETE_COMPLETE,
    ERROR
}

data class TikTokStatus(
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    val packageName: String = "",
    val isAccessibilityEnabled: Boolean = false
)
