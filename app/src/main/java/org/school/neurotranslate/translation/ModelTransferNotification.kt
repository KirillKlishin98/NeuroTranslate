package org.school.neurotranslate.translation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.school.neurotranslate.R

internal object ModelTransferNotification {
    const val CHANNEL_ID = "model_downloads"

    @Suppress("DEPRECATION")
    fun create(context: Context, modelId: String, progress: Float): Notification {
        ensureChannel(context)
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.model_download_notification_title))
            .setContentText(context.getString(R.string.model_download_notification_progress, modelId, percent))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
    }

    fun id(modelId: String): Int = (modelId.hashCode() and 0x7fffffff).coerceAtLeast(1)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.model_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
