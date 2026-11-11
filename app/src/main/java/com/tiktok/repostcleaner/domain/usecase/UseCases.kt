package com.tiktok.repostcleaner.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import com.tiktok.repostcleaner.accessibility.TikTokAccessibilityService
import com.tiktok.repostcleaner.data.database.RepostItemEntity
import com.tiktok.repostcleaner.data.repository.RepostRepository
import com.tiktok.repostcleaner.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckTikTokStatusUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): TikTokStatus {
        val packages = listOf(
            TikTokAccessibilityService.TIKTOK_PKG_MAIN,
            TikTokAccessibilityService.TIKTOK_PKG_ALT
        )
        var installedPkg = ""
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                installedPkg = pkg
                break
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
        }
        val isInstalled = installedPkg.isNotEmpty()
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        return TikTokStatus(
            isInstalled = isInstalled,
            isRunning = TikTokAccessibilityService.getInstance() != null,
            packageName = installedPkg,
            isAccessibilityEnabled = accessibilityEnabled
        )
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceId = "${context.packageName}/${TikTokAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(serviceId, ignoreCase = true)) return true
        }
        return false
    }
}

@Singleton
class StartScanUseCase @Inject constructor(
    private val repository: RepostRepository
) {
    suspend operator fun invoke(): Long {
        return repository.startNewSession()
    }
}

@Singleton
class ObserveScanEventsUseCase @Inject constructor(
    private val repository: RepostRepository
) {
    operator fun invoke(sessionId: Long): Flow<List<RepostItemEntity>> {
        return repository.getRepostsBySession(sessionId)
    }
}

@Singleton
class GetSessionStatsUseCase @Inject constructor(
    private val repository: RepostRepository
) {
    suspend operator fun invoke(sessionId: Long): Triple<Int, Int, Int> {
        return repository.getSessionStats(sessionId)
    }
}
