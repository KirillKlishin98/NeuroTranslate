package org.school.neurotranslate.translation

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle

object ModelDownloadScheduler {
    fun enqueue(context: Context, modelId: String) {
        val appContext = context.applicationContext
        val runtime = ModelDownloadRuntime.get(appContext as Application)
        val model = runtime.catalog.get(modelId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val job = JobInfo.Builder(
                jobId(modelId),
                ComponentName(appContext, ModelDownloadJobService::class.java)
            )
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(model.totalBytes, 0L)
                .setExtras(PersistableBundle().apply { putString(EXTRA_MODEL_ID, modelId) })
                .build()
            val scheduler = appContext.getSystemService(JobScheduler::class.java)
            check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
                "Android could not schedule the model download."
            }
        } else {
            val intent = Intent(appContext, ModelDownloadForegroundService::class.java)
                .setAction(ModelDownloadForegroundService.ACTION_DOWNLOAD)
                .putExtra(EXTRA_MODEL_ID, modelId)
            appContext.startForegroundServiceCompat(intent)
        }
    }

    fun cancel(context: Context, modelId: String) {
        val appContext = context.applicationContext
        val runtime = ModelDownloadRuntime.get(appContext as Application)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.getSystemService(JobScheduler::class.java).cancel(jobId(modelId))
        } else {
            appContext.stopService(Intent(appContext, ModelDownloadForegroundService::class.java))
        }
        runtime.markCancelled(modelId)
    }

    private fun jobId(modelId: String): Int =
        (modelId.hashCode() and 0x7fffffff).coerceAtLeast(1)

    private fun Context.startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    const val EXTRA_MODEL_ID = "org.school.neurotranslate.extra.MODEL_ID"
}
