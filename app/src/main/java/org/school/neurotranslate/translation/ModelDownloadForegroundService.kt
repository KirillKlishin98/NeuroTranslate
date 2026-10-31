package org.school.neurotranslate.translation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentStartId: Int = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentStartId = startId
        val modelId = intent?.getStringExtra(ModelDownloadScheduler.EXTRA_MODEL_ID)
        if (intent?.action != ACTION_DOWNLOAD || modelId.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) return START_REDELIVER_INTENT

        startForegroundCompat(
            ModelTransferNotification.id(modelId),
            ModelTransferNotification.create(this, modelId, 0f)
        )
        downloadJob = serviceScope.launch {
            try {
                val runtime = ModelDownloadRuntime.get(application)
                runtime.download(modelId) { progress ->
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(ModelTransferNotification.id(modelId), ModelTransferNotification.create(this@ModelDownloadForegroundService, modelId, progress.fraction))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(currentStartId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notificationId: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "org.school.neurotranslate.action.DOWNLOAD_MODEL"
    }
}
