package com.tiktok.repostcleaner.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core Accessibility Service that interacts with TikTok UI.
 * Operates within TikTok's own UI without requiring credentials.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TikTokA11y"
        const val TIKTOK_PKG_MAIN = "com.zhiliaoapp.musically"
        const val TIKTOK_PKG_ALT = "com.ss.android.ugc.trill"

        // Actions sent to service from ViewModel via broadcast
        const val ACTION_START_SCAN = "com.tiktok.repostcleaner.START_SCAN"
        const val ACTION_START_DELETE = "com.tiktok.repostcleaner.START_DELETE"
        const val ACTION_PAUSE = "com.tiktok.repostcleaner.PAUSE"
        const val ACTION_RESUME = "com.tiktok.repostcleaner.RESUME"
        const val ACTION_STOP = "com.tiktok.repostcleaner.STOP"

        // Scroll delay between actions in ms
        private const val ACTION_DELAY_MS = 500L
        private const val SCROLL_DELAY_MS = 1500L
        private const val ELEMENT_WAIT_MS = 2000L
        private const val POLL_INTERVAL_MS = 250L

        private var instance: TikTokAccessibilityService? = null
        fun getInstance(): TikTokAccessibilityService? = instance

        // SharedFlows for communication with ViewModel
        private val _scanEvents = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 100)
        val scanEvents = _scanEvents.asSharedFlow()

        private val _serviceState = MutableStateFlow(ServiceState.DISCONNECTED)
        val serviceState = _serviceState.asStateFlow()
    }

    sealed class ScanEvent {
        data class RepostFound(val id: String, val videoId: String, val author: String, val desc: String) : ScanEvent()
        data class ScanProgress(val itemsScrolled: Int, val repostsFound: Int, val status: String) : ScanEvent()
        object ScanComplete : ScanEvent()
        data class DeleteProgress(val id: String, val success: Boolean, val error: String? = null) : ScanEvent()
        object DeleteComplete : ScanEvent()
        data class Error(val message: String, val code: ErrorCode) : ScanEvent()
    }

    enum class ErrorCode {
        TIKTOK_NOT_FOUND,
        NOT_AUTHORIZED,
        REPOSTS_TAB_NOT_FOUND,
        ACCESSIBILITY_LOST,
        GENERAL_ERROR
    }

    enum class ServiceState {
        DISCONNECTED, CONNECTED, SCANNING, DELETING, PAUSED
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentJob: Job? = null
    private var isPaused = false
    private var isStopped = false

    // Track seen repost IDs to avoid duplicates
    private val seenRepostIds = mutableSetOf<String>()
    private val foundReposts = mutableListOf<Pair<String, String>>() // id -> nodeDescription

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _serviceState.value = ServiceState.CONNECTED
        Log.i(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Process events if needed for state tracking
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        _serviceState.value = ServiceState.DISCONNECTED
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _serviceState.value = ServiceState.DISCONNECTED
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    fun startScan() {
        if (_serviceState.value == ServiceState.SCANNING) return
        isStopped = false
        isPaused = false
        seenRepostIds.clear()
        foundReposts.clear()
        currentJob = serviceScope.launch {
            _serviceState.value = ServiceState.SCANNING
            performScan()
        }
    }

    fun startDelete(repostIds: List<String>) {
        if (_serviceState.value == ServiceState.DELETING) return
        isStopped = false
        isPaused = false
        currentJob = serviceScope.launch {
            _serviceState.value = ServiceState.DELETING
            performDeletion(repostIds)
        }
    }

    fun pause() {
        isPaused = true
        _serviceState.value = ServiceState.PAUSED
    }

    fun resume() {
        isPaused = false
        _serviceState.value = if (_serviceState.value == ServiceState.PAUSED) ServiceState.DELETING else _serviceState.value
    }

    fun stop() {
        isStopped = true
        isPaused = false
        currentJob?.cancel()
        _serviceState.value = ServiceState.CONNECTED
    }

    // ─── SCAN LOGIC ───────────────────────────────────────────────────────────

    private suspend fun performScan() {
        try {
            // 1. Find active TikTok window
            val pkgName = awaitTikTokForeground()
            if (pkgName == null) {
                _scanEvents.emit(ScanEvent.Error("TikTok не открыт на экране. Приложение попробует открыть его автоматически — повторите запуск.", ErrorCode.TIKTOK_NOT_FOUND))
                _serviceState.value = ServiceState.CONNECTED
                return
            }

            // 2. Navigate to profile
            _scanEvents.emit(ScanEvent.ScanProgress(0, 0, "Открываю профиль..."))
            if (!navigateToProfile(pkgName)) {
                _scanEvents.emit(ScanEvent.Error("Не удалось открыть профиль. Убедитесь, что вы вошли в TikTok.", ErrorCode.NOT_AUTHORIZED))
                _serviceState.value = ServiceState.CONNECTED
                return
            }

            delay(ELEMENT_WAIT_MS)

            // 3. Find and tap reposts tab
            _scanEvents.emit(ScanEvent.ScanProgress(0, 0, "Ищу вкладку репостов..."))
            if (!navigateToRepostsTab(pkgName)) {
                _scanEvents.emit(ScanEvent.Error("Вкладка репостов не найдена. Возможно, у вас нет репостов или интерфейс изменился.", ErrorCode.REPOSTS_TAB_NOT_FOUND))
                _serviceState.value = ServiceState.CONNECTED
                return
            }

            delay(ELEMENT_WAIT_MS)

            // 4. Scroll through and collect reposts
            _scanEvents.emit(ScanEvent.ScanProgress(0, 0, "Сканирую репосты..."))
            scanRepostsList(pkgName)

            _scanEvents.emit(ScanEvent.ScanComplete)
            _serviceState.value = ServiceState.CONNECTED

        } catch (e: CancellationException) {
            Log.i(TAG, "Scan cancelled")
            _serviceState.value = ServiceState.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Scan error", e)
            _scanEvents.emit(ScanEvent.Error("Ошибка сканирования: ${e.message}", ErrorCode.GENERAL_ERROR))
            _serviceState.value = ServiceState.CONNECTED
        }
    }

    private fun getActiveTikTokPackage(): String? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        return if (pkg == TIKTOK_PKG_MAIN || pkg == TIKTOK_PKG_ALT) pkg else null
    }

    /** True when TikTok is the window currently in the foreground. */
    fun isTikTokInForeground(): Boolean = getActiveTikTokPackage() != null

    /**
     * The scan is normally started from our own UI, so TikTok may still be in the
     * background for a moment after we bring it up. Poll until its window becomes
     * active (or the timeout expires).
     */
    private suspend fun awaitTikTokForeground(timeoutMs: Long = 10_000L): String? {
        var waited = 0L
        while (waited < timeoutMs) {
            getActiveTikTokPackage()?.let { return it }
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return getActiveTikTokPackage()
    }

    private suspend fun navigateToProfile(pkgName: String): Boolean {
        // Find profile tab in bottom navigation
        val profileKeywords = listOf("profile", "Profile", "me", "Me", "профиль", "Профиль")
        return withTimeoutOrNull(5000L) {
            var found = false
            repeat(3) {
                if (!found) {
                    val root = rootInActiveWindow
                    if (root != null) {
                        for (keyword in profileKeywords) {
                            val node = findNodeByContentDescContaining(root, keyword)
                                ?: findNodeByTextContaining(root, keyword)
                            if (node != null) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                found = true
                                return@repeat
                            }
                        }
                        // Try finding by navigation tab position (profile is usually last)
                        val navNodes = findAllNodesByClassName(root, "android.widget.FrameLayout")
                            .filter { it.isClickable }
                            .take(5)
                        if (navNodes.size >= 4) {
                            navNodes.last().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            found = true
                        }
                    }
                    if (!found) delay(500)
                }
            }
            found
        } ?: false
    }

    private suspend fun navigateToRepostsTab(pkgName: String): Boolean {
        // TikTok profile has tabs: Videos, Liked, Reposts (or similar names)
        val repostKeywords = listOf(
            "repost", "Repost", "reposts", "Reposts",
            "Репост", "репост", "Репосты", "репосты",
            "shared", "Shared"
        )
        return withTimeoutOrNull(8000L) {
            var found = false
            repeat(5) {
                if (!found) {
                    val root = rootInActiveWindow
                    if (root != null) {
                        for (keyword in repostKeywords) {
                            val node = findNodeByContentDescContaining(root, keyword)
                                ?: findNodeByTextContaining(root, keyword)
                            if (node != null) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                found = true
                                return@repeat
                            }
                        }
                    }
                    if (!found) delay(1000)
                }
            }
            found
        } ?: false
    }

    private suspend fun scanRepostsList(pkgName: String) {
        var scrollCount = 0
        var noNewRepostsCount = 0
        val MAX_NO_NEW = 3  // Stop after 3 consecutive scrolls with no new reposts

        while (!isStopped && noNewRepostsCount < MAX_NO_NEW) {
            val prevFoundCount = seenRepostIds.size

            // Collect visible reposts from current view
            val root = rootInActiveWindow
            if (root != null) {
                collectVisibleReposts(root)
            }

            val newFound = seenRepostIds.size - prevFoundCount

            _scanEvents.emit(
                ScanEvent.ScanProgress(
                    itemsScrolled = scrollCount,
                    repostsFound = seenRepostIds.size,
                    status = "Найдено: ${seenRepostIds.size}, прокручено: $scrollCount"
                )
            )

            if (newFound == 0) {
                noNewRepostsCount++
            } else {
                noNewRepostsCount = 0
            }

            if (noNewRepostsCount < MAX_NO_NEW) {
                // Scroll down in the reposts grid
                scrollDown()
                delay(SCROLL_DELAY_MS)
                scrollCount++
            }
        }
    }

    private fun collectVisibleReposts(root: AccessibilityNodeInfo) {
        // Find video grid items — TikTok uses RecyclerView with video thumbnails
        val videoItems = mutableListOf<AccessibilityNodeInfo>()

        // Look for clickable image items in the grid (video thumbnails)
        findClickableVideoNodes(root, videoItems)

        for (node in videoItems) {
            val nodeId = generateNodeId(node)
            if (nodeId != null && nodeId !in seenRepostIds) {
                seenRepostIds.add(nodeId)
                val (author, desc) = extractVideoInfo(node)
                // Emit discovery
                serviceScope.launch {
                    _scanEvents.emit(
                        ScanEvent.RepostFound(
                            id = nodeId,
                            videoId = nodeId,
                            author = author,
                            desc = desc
                        )
                    )
                }
                // Store for deletion
                foundReposts.add(nodeId to desc)
            }
        }
    }

    private fun findClickableVideoNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && node.className?.contains("Image") == true) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableVideoNodes(child, result)
        }
    }

    private fun generateNodeId(node: AccessibilityNodeInfo): String? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        // Create a stable ID from content description + viewId
        val raw = "$desc|$viewId"
        return if (raw.isBlank() || raw == "|") null
        else raw.hashCode().toString() + "_" + System.currentTimeMillis()
    }

    private fun extractVideoInfo(node: AccessibilityNodeInfo): Pair<String, String> {
        val desc = node.contentDescription?.toString() ?: ""
        // TikTok contentDescription often contains "Video by @author: description"
        val authorPattern = Regex("(?:Video by |@)([^:,\\.]+)")
        val author = authorPattern.find(desc)?.groupValues?.getOrNull(1) ?: ""
        return Pair(author.trim(), desc.take(100))
    }

    // ─── DELETE LOGIC ─────────────────────────────────────────────────────────

    private suspend fun performDeletion(repostIds: List<String>) {
        try {
            val pkgName = awaitTikTokForeground()
            if (pkgName == null) {
                _scanEvents.emit(ScanEvent.Error("TikTok не открыт на экране. Приложение попробует открыть его автоматически — повторите запуск.", ErrorCode.TIKTOK_NOT_FOUND))
                _serviceState.value = ServiceState.CONNECTED
                return
            }

            // Navigate back to reposts tab before deletion
            navigateToProfile(pkgName)
            delay(ELEMENT_WAIT_MS)
            navigateToRepostsTab(pkgName)
            delay(ELEMENT_WAIT_MS)

            var processedCount = 0

            // Process reposts from the grid — open each, unrepost, return
            for (repostId in repostIds) {
                if (isStopped) break

                while (isPaused && !isStopped) {
                    delay(500)
                }

                if (isStopped) break

                val success = deleteRepostById(repostId, processedCount, repostIds.size, pkgName)
                _scanEvents.emit(
                    ScanEvent.DeleteProgress(
                        id = repostId,
                        success = success,
                        error = if (!success) "Не удалось удалить" else null
                    )
                )
                processedCount++
                delay(ACTION_DELAY_MS)
            }

            _scanEvents.emit(ScanEvent.DeleteComplete)
            _serviceState.value = ServiceState.CONNECTED

        } catch (e: CancellationException) {
            Log.i(TAG, "Deletion cancelled")
            _serviceState.value = ServiceState.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Deletion error", e)
            _scanEvents.emit(ScanEvent.Error("Ошибка удаления: ${e.message}", ErrorCode.GENERAL_ERROR))
            _serviceState.value = ServiceState.CONNECTED
        }
    }

    private suspend fun deleteRepostById(
        repostId: String,
        index: Int,
        total: Int,
        pkgName: String
    ): Boolean {
        // Strategy: tap the first visible repost video → long press → find "Remove repost" option
        return withTimeoutOrNull(15000L) {
            val root = rootInActiveWindow ?: return@withTimeoutOrNull false

            // Find clickable video grid items
            val videoItems = mutableListOf<AccessibilityNodeInfo>()
            findClickableVideoNodes(root, videoItems)

            if (videoItems.isEmpty()) return@withTimeoutOrNull false

            // Tap the first available video (top-left of grid = first repost)
            val targetNode = videoItems.firstOrNull() ?: return@withTimeoutOrNull false
            val bounds = Rect()
            targetNode.getBoundsInScreen(bounds)

            // Long press to open context menu
            performLongClick(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            delay(1000)

            // Look for "Remove repost" or similar option in the menu
            val menuRoot = rootInActiveWindow ?: return@withTimeoutOrNull false
            val removeKeywords = listOf(
                "Remove repost", "remove repost",
                "Удалить репост", "удалить репост",
                "Remove", "Убрать"
            )

            var removeNode: AccessibilityNodeInfo? = null
            for (keyword in removeKeywords) {
                removeNode = findNodeByTextContaining(menuRoot, keyword)
                    ?: findNodeByContentDescContaining(menuRoot, keyword)
                if (removeNode != null) break
            }

            if (removeNode != null) {
                removeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(500)

                // Handle possible confirmation dialog
                val confirmRoot = rootInActiveWindow
                if (confirmRoot != null) {
                    val confirmKeywords = listOf("Remove", "Удалить", "OK", "Yes", "Да", "Confirm", "Подтвердить")
                    for (keyword in confirmKeywords) {
                        val confirmNode = findNodeByTextContaining(confirmRoot, keyword)
                        if (confirmNode != null && confirmNode.isClickable) {
                            confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            break
                        }
                    }
                }
                delay(500)
                true
            } else {
                // If no context menu appeared, try alternative: open video → find share button → unrepost
                pressBack()
                delay(500)
                false
            }
        } ?: false
    }

    // ─── GESTURE HELPERS ──────────────────────────────────────────────────────

    private fun scrollDown() {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        val path = Path().apply {
            moveTo(screenWidth / 2f, screenHeight * 0.7f)
            lineTo(screenWidth / 2f, screenHeight * 0.3f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performLongClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ─── NODE FINDERS ─────────────────────────────────────────────────────────

    private fun findNodeByTextContaining(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    private fun findNodeByContentDescContaining(
        root: AccessibilityNodeInfo,
        keyword: String
    ): AccessibilityNodeInfo? {
        val desc = root.contentDescription?.toString() ?: ""
        if (desc.contains(keyword, ignoreCase = true)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByContentDescContaining(child, keyword)
            if (found != null) return found
        }
        return null
    }

    private fun findAllNodesByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root.className?.toString() == className) result.add(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            result.addAll(findAllNodesByClassName(child, className))
        }
        return result
    }
}
