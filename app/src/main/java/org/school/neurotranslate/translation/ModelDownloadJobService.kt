package org.school.neurotranslate.translation

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ModelDownloadJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val modelId = params.extras.getString(ModelDownloadScheduler.EXTRA_MODEL_ID)
        if (modelId.isNullOrBlank()) return false
        val notificationId = ModelTransferNotification.id(modelId)
        setNotification(
            params,
            notificationId,
            ModelTransferNotification.create(this, modelId, 0f),
            JOB_END_NOTIFICATION_POLICY_REMOVE
        )
        activeJob = serviceScope.launch {
            var shouldFinish = false
            try {
                val runtime = ModelDownloadRuntime.get(application)
                if (!params.isUserInitiatedJob) {
                    runtime.markFailed(modelId)
                    shouldFinish = true
                    return@launch
                }
                runtime.download(modelId) { progress ->
                    val notification = ModelTransferNotification.create(this@ModelDownloadJobService, modelId, progress.fraction)
                    mainHandler.post {
                        setNotification(params, notificationId, notification, JOB_END_NOTIFICATION_POLICY_REMOVE)
                    }
                }
                shouldFinish = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                shouldFinish = true
            } finally {
                if (shouldFinish) mainHandler.post {
                    activeJob = null
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
