package com.tiktok.repostcleaner.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiktok.repostcleaner.accessibility.TikTokAccessibilityService
import com.tiktok.repostcleaner.data.database.RepostItemEntity
import com.tiktok.repostcleaner.data.repository.RepostRepository
import com.tiktok.repostcleaner.domain.model.*
import com.tiktok.repostcleaner.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UiState(
    val appState: AppState = AppState.IDLE,
    val tikTokStatus: TikTokStatus = TikTokStatus(),
    val scanProgress: ScanProgress = ScanProgress(),
    val deletionProgress: DeletionProgress = DeletionProgress(),
    val reposts: List<RepostItemEntity> = emptyList(),
    val currentSessionId: Long = -1L,
    val errorMessage: String? = null,
    val lastStats: Triple<Int, Int, Int>? = null // total, deleted, skipped
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkTikTokStatusUseCase: CheckTikTokStatusUseCase,
    private val startScanUseCase: StartScanUseCase,
    private val repository: RepostRepository,
    private val getSessionStatsUseCase: GetSessionStatsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var scanEventJob: Job? = null
    private var repostsObserveJob: Job? = null

    init {
        refreshTikTokStatus()
        observeServiceState()
        loadLastSession()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            TikTokAccessibilityService.serviceState.collect { state ->
                val currentState = _uiState.value
                val appState = when (state) {
                    TikTokAccessibilityService.ServiceState.CONNECTED,
                    TikTokAccessibilityService.ServiceState.DISCONNECTED -> {
                        if (currentState.appState == AppState.SCANNING ||
                            currentState.appState == AppState.DELETING) currentState.appState
                        else AppState.READY
                    }
                    TikTokAccessibilityService.ServiceState.SCANNING -> AppState.SCANNING
                    TikTokAccessibilityService.ServiceState.DELETING -> AppState.DELETING
                    TikTokAccessibilityService.ServiceState.PAUSED -> AppState.DELETING
                }
                _uiState.update { it.copy(
                    tikTokStatus = it.tikTokStatus.copy(
                        isAccessibilityEnabled = state != TikTokAccessibilityService.ServiceState.DISCONNECTED
                    )
                )}
            }
        }
    }

    fun refreshTikTokStatus() {
        viewModelScope.launch {
            val status = checkTikTokStatusUseCase()
            _uiState.update { it.copy(
                tikTokStatus = status,
                appState = when {
                    !status.isInstalled -> AppState.TIKTOK_NOT_INSTALLED
                    !status.isAccessibilityEnabled -> AppState.IDLE
                    else -> AppState.READY
                }
            )}
        }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openTikTok() {
        val tikTokStatus = _uiState.value.tikTokStatus
        val pkg = tikTokStatus.packageName.ifBlank {
            TikTokAccessibilityService.TIKTOK_PKG_MAIN
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun startScan() {
        val service = TikTokAccessibilityService.getInstance()
        if (service == null) {
            _uiState.update { it.copy(errorMessage = "Служба доступности не активна. Включите её в настройках.") }
            return
        }

        viewModelScope.launch {
            val sessionId = startScanUseCase()
            _uiState.update { it.copy(
                appState = AppState.SCANNING,
                currentSessionId = sessionId,
                scanProgress = ScanProgress(status = "Запуск..."),
                reposts = emptyList(),
                errorMessage = null
            )}

            observeReposts(sessionId)
            observeScanEvents(sessionId)
            service.startScan()
        }
    }

    private fun observeReposts(sessionId: Long) {
        repostsObserveJob?.cancel()
        repostsObserveJob = viewModelScope.launch {
            repository.getRepostsBySession(sessionId).collect { reposts ->
                _uiState.update { it.copy(reposts = reposts) }
            }
        }
    }

    private fun observeScanEvents(sessionId: Long) {
        scanEventJob?.cancel()
        scanEventJob = viewModelScope.launch {
            TikTokAccessibilityService.scanEvents.collect { event ->
                when (event) {
                    is TikTokAccessibilityService.ScanEvent.RepostFound -> {
                        repository.saveRepost(
                            RepostItem(
                                id = event.id,
                                videoId = event.videoId,
                                authorName = event.author,
                                description = event.desc
                            ),
                            sessionId
                        )
                        val current = _uiState.value.scanProgress
                        _uiState.update { it.copy(
                            scanProgress = current.copy(
                                totalFound = current.totalFound + 1
                            )
                        )}
                    }

                    is TikTokAccessibilityService.ScanEvent.ScanProgress -> {
                        _uiState.update { it.copy(
                            scanProgress = ScanProgress(
                                totalFound = event.repostsFound,
                                itemsScrolled = event.itemsScrolled,
                                status = event.status
                            )
                        )}
                    }

                    is TikTokAccessibilityService.ScanEvent.ScanComplete -> {
                        repository.finishSession(sessionId, ScanStatus.COMPLETED)
                        val stats = getSessionStatsUseCase(sessionId)
                        _uiState.update { it.copy(
                            appState = AppState.SCAN_COMPLETE,
                            scanProgress = _uiState.value.scanProgress.copy(isComplete = true),
                            lastStats = stats
                        )}
                        scanEventJob?.cancel()
                    }

                    is TikTokAccessibilityService.ScanEvent.DeleteProgress -> {
                        val progress = _uiState.value.deletionProgress
                        if (event.success) {
                            repository.markDeleted(event.id, sessionId)
                            _uiState.update { it.copy(
                                deletionProgress = progress.copy(
                                    deleted = progress.deleted + 1,
                                    current = progress.current + 1
                                )
                            )}
                        } else {
                            repository.markSkipped(event.id, event.error ?: "Unknown error")
                            _uiState.update { it.copy(
                                deletionProgress = progress.copy(
                                    failed = progress.failed + 1,
                                    current = progress.current + 1
                                )
                            )}
                        }
                    }

                    is TikTokAccessibilityService.ScanEvent.DeleteComplete -> {
                        val stats = getSessionStatsUseCase(sessionId)
                        _uiState.update { it.copy(
                            appState = AppState.DELETE_COMPLETE,
                            deletionProgress = _uiState.value.deletionProgress.copy(isComplete = true),
                            lastStats = stats
                        )}
                        scanEventJob?.cancel()
                    }

                    is TikTokAccessibilityService.ScanEvent.Error -> {
                        _uiState.update { it.copy(
                            appState = AppState.ERROR,
                            errorMessage = event.message
                        )}
                        scanEventJob?.cancel()
                    }
                }
            }
        }
    }

    fun startDeletion() {
        val service = TikTokAccessibilityService.getInstance()
        if (service == null) {
            _uiState.update { it.copy(errorMessage = "Служба доступности не активна.") }
            return
        }

        val sessionId = _uiState.value.currentSessionId
        viewModelScope.launch {
            val pending = repository.getPendingReposts(sessionId)
            if (pending.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Нет репостов для удаления.") }
                return@launch
            }

            _uiState.update { it.copy(
                appState = AppState.DELETING,
                deletionProgress = DeletionProgress(
                    total = pending.size,
                    deleted = 0,
                    failed = 0,
                    skipped = 0,
                    current = 0
                ),
                errorMessage = null
            )}

            observeScanEvents(sessionId)
            service.startDelete(pending.map { it.id })
        }
    }

    fun pauseDeletion() {
        TikTokAccessibilityService.getInstance()?.pause()
        _uiState.update { it.copy(
            deletionProgress = _uiState.value.deletionProgress.copy(isPaused = true)
        )}
    }

    fun resumeDeletion() {
        TikTokAccessibilityService.getInstance()?.resume()
        _uiState.update { it.copy(
            deletionProgress = _uiState.value.deletionProgress.copy(isPaused = false)
        )}
    }

    fun stopDeletion() {
        TikTokAccessibilityService.getInstance()?.stop()
        scanEventJob?.cancel()
        viewModelScope.launch {
            val sessionId = _uiState.value.currentSessionId
            if (sessionId != -1L) {
                repository.finishSession(sessionId, ScanStatus.PAUSED)
                val stats = getSessionStatsUseCase(sessionId)
                _uiState.update { it.copy(
                    appState = AppState.DELETE_COMPLETE,
                    deletionProgress = _uiState.value.deletionProgress.copy(isComplete = true),
                    lastStats = stats
                )}
            }
        }
    }

    fun startRescan() {
        _uiState.update { it.copy(
            appState = AppState.READY,
            scanProgress = ScanProgress(),
            deletionProgress = DeletionProgress(),
            reposts = emptyList(),
            errorMessage = null
        )}
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, appState = AppState.READY) }
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            val lastSession = repository.getLatestSession()
            if (lastSession != null && lastSession.status == ScanStatus.PAUSED.name) {
                val stats = getSessionStatsUseCase(lastSession.id)
                _uiState.update { it.copy(
                    currentSessionId = lastSession.id,
                    lastStats = stats
                )}
                observeReposts(lastSession.id)
            }
        }
    }
}
