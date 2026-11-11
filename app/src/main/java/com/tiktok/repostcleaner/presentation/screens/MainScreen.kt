package com.tiktok.repostcleaner.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktok.repostcleaner.domain.model.AppState
import com.tiktok.repostcleaner.presentation.components.*
import com.tiktok.repostcleaner.presentation.theme.*
import com.tiktok.repostcleaner.presentation.viewmodel.UiState

@Composable
fun MainScreen(
    uiState: UiState,
    onCheckStatus: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenTikTok: () -> Unit,
    onStartScan: () -> Unit,
    onStartDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRescan: () -> Unit,
    onClearError: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TikTokBlack)
    ) {
        AnimatedContent(
            targetState = uiState.appState,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "screen_transition"
        ) { state ->
            when (state) {
                AppState.IDLE, AppState.TIKTOK_NOT_INSTALLED ->
                    HomeScreen(uiState, onCheckStatus, onOpenAccessibility, onOpenTikTok, onStartScan)
                AppState.CHECKING_TIKTOK ->
                    LoadingScreen("Проверка TikTok...")
                AppState.READY ->
                    HomeScreen(uiState, onCheckStatus, onOpenAccessibility, onOpenTikTok, onStartScan)
                AppState.SCANNING ->
                    ScanningScreen(uiState)
                AppState.SCAN_COMPLETE ->
                    ScanResultScreen(uiState, onStartDelete, onRescan)
                AppState.DELETING ->
                    DeletionProgressScreen(uiState, onPause, onResume, onStop)
                AppState.DELETE_COMPLETE ->
                    FinalReportScreen(uiState, onRescan)
                AppState.ERROR ->
                    ErrorScreen(uiState.errorMessage ?: "Произошла ошибка", onClearError)
                AppState.TIKTOK_NOT_AUTHORIZED ->
                    ErrorScreen("TikTok не авторизован. Пожалуйста, войдите в TikTok.", onClearError)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: UiState,
    onCheckStatus: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenTikTok: () -> Unit,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Logo area
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.radialGradient(listOf(TikTokRed.copy(0.3f), Color.Transparent)),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = TikTokRed,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "TikTok\nRepost Cleaner",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TikTokWhite,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Text(
            text = "Удали все репосты в один клик",
            fontSize = 14.sp,
            color = TikTokTextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader("СТАТУС")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TikTok", color = TikTokTextSecondary, fontSize = 14.sp)
                    ConnectionStatusBadge(uiState.tikTokStatus.isInstalled)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Служба доступности", color = TikTokTextSecondary, fontSize = 14.sp)
                    ConnectionStatusBadge(uiState.tikTokStatus.isAccessibilityEnabled)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Last stats card
        if (uiState.lastStats != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader("ПОСЛЕДНЯЯ ОПЕРАЦИЯ")
                    InfoRow("Найдено репостов", "${uiState.lastStats.first}")
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Успешно удалено", "${uiState.lastStats.second}", SuccessGreen)
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Пропущено", "${uiState.lastStats.third}", WarningOrange)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action buttons
        if (!uiState.tikTokStatus.isInstalled) {
            Text(
                text = "⚠️ TikTok не установлен на этом устройстве",
                color = WarningOrange,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            if (!uiState.tikTokStatus.isAccessibilityEnabled) {
                TikTokGradientButton(
                    text = "Включить службу доступности",
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Необходимо для автоматического взаимодействия с TikTok",
                    color = TikTokTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            TikTokGradientButton(
                text = "Открыть TikTok",
                onClick = onOpenTikTok,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            TikTokGradientButton(
                text = "Начать анализ",
                onClick = onStartScan,
                enabled = uiState.tikTokStatus.isAccessibilityEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            if (!uiState.tikTokStatus.isAccessibilityEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Включите службу доступности для начала анализа",
                    color = TikTokTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onCheckStatus) {
            Text("Обновить статус", color = TikTokCyan, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ScanningScreen(uiState: UiState) {
    val progress = uiState.scanProgress
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = TikTokRed, strokeWidth = 3.dp, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(32.dp))
        Text("Сканирование репостов", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TikTokWhite)
        Spacer(Modifier.height(8.dp))
        Text(progress.status, color = TikTokTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Найдено репостов", "${progress.totalFound}", TikTokCyan)
                InfoRow("Просмотрено элементов", "${progress.itemsScrolled}")
                InfoRow("Статус", if (progress.isComplete) "Завершено" else "В процессе...",
                    if (progress.isComplete) SuccessGreen else WarningOrange)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "TikTok открыт в фоне. Не закрывайте его во время сканирования.",
            color = TikTokTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanResultScreen(
    uiState: UiState,
    onStartDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text("Анализ завершён", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TikTokWhite)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val stats = uiState.lastStats
                InfoRow("Всего найдено репостов", "${stats?.first ?: uiState.scanProgress.totalFound}", TikTokCyan)
                InfoRow("Успешно идентифицировано", "${uiState.reposts.size}")
                InfoRow("Ошибок анализа", "0")
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = WarningOrange, modifier = Modifier.size(20.dp))
                Text(
                    "Удаление необратимо. Убедитесь, что хотите удалить все найденные репосты.",
                    color = TikTokTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        TikTokGradientButton(
            text = "Удалить все репосты (${uiState.reposts.size})",
            onClick = onStartDelete,
            modifier = Modifier.fillMaxWidth(),
            isDestructive = true,
            enabled = uiState.reposts.isNotEmpty()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TikTokWhite),
            border = BorderStroke(1.dp, TikTokLightGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Отмена", fontSize = 16.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeletionProgressScreen(
    uiState: UiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val progress = uiState.deletionProgress
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Text("Удаление репостов", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TikTokWhite)
        Spacer(Modifier.height(8.dp))

        Text(
            "${progress.percentage.toInt()}%",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = TikTokRed
        )

        Spacer(Modifier.height(16.dp))
        TikTokProgressBar(
            progress = progress.percentage / 100f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Всего",
                value = "${progress.total}",
                icon = { Icon(Icons.Default.FormatListNumbered, null, tint = TikTokCyan, modifier = Modifier.size(24.dp)) },
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Удалено",
                value = "${progress.deleted}",
                icon = { Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(24.dp)) },
                modifier = Modifier.weight(1f),
                valueColor = SuccessGreen
            )
            StatusCard(
                title = "Осталось",
                value = "${progress.remaining}",
                icon = { Icon(Icons.Default.HourglassBottom, null, tint = WarningOrange, modifier = Modifier.size(24.dp)) },
                modifier = Modifier.weight(1f),
                valueColor = WarningOrange
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Ошибок", "${progress.failed}", ErrorRed)
                InfoRow("Статус", if (progress.isPaused) "Пауза" else "Удаление...",
                    if (progress.isPaused) WarningOrange else TikTokCyan)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (progress.isPaused) {
                TikTokGradientButton(
                    text = "Продолжить",
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TikTokWhite),
                    border = BorderStroke(1.dp, TikTokLightGray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Пауза")
                }
            }

            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Остановить")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Прогресс сохраняется. Вы можете продолжить позже.",
            color = TikTokTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FinalReportScreen(
    uiState: UiState,
    onRescan: () -> Unit
) {
    val stats = uiState.lastStats
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Icon(
            imageVector = Icons.Default.TaskAlt,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text("Очистка завершена", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TikTokWhite)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader("СТАТИСТИКА")
                InfoRow("Всего обнаружено", "${stats?.first ?: uiState.deletionProgress.total}")
                InfoRow("Успешно удалено", "${stats?.second ?: uiState.deletionProgress.deleted}", SuccessGreen)
                InfoRow("Не удалось удалить", "${uiState.deletionProgress.failed}", ErrorRed)
                InfoRow("Пропущено", "${stats?.third ?: uiState.deletionProgress.skipped}", WarningOrange)
            }
        }

        Spacer(Modifier.weight(1f))

        TikTokGradientButton(
            text = "Проверить результат",
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TikTokRed)
            Spacer(Modifier.height(16.dp))
            Text(message, color = TikTokTextSecondary)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Ошибка", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TikTokWhite)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TikTokTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        TikTokGradientButton("Назад", onRetry, Modifier.fillMaxWidth())
    }
}
