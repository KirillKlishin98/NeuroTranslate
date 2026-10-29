package org.school.neurotranslate.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TranslationService(
    private val engine: TranslationEngine,
    private val modelRepository: ModelRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val operationMutex = Mutex()
    private var activeModelId: String? = null
    private var engineReady = false

    suspend fun activateModel(modelId: String) = operationMutex.withLock {
        val model = modelRepository.findInstalledModel(modelId)
            ?: throw ModelNotInstalledException(modelId)
        require(model.id == modelId) { "Repository returned model ${model.id} for requested id $modelId." }

        closeEngine()
        activeModelId = model.id

        try {
            engine.load(model)
            engineReady = true
        } catch (cancellation: CancellationException) {
            closeEngineAfterFailure(cancellation)
            throw cancellation
        } catch (failure: Exception) {
            closeEngineAfterFailure(failure)
            throw failure
        }
    }

    suspend fun translate(
        modelId: String,
        sourceTokenIds: LongArray,
        sourceAttentionMask: LongArray,
        maxNewTokens: Int
    ): LongArray = operationMutex.withLock {
        check(activeModelId == modelId && engineReady) {
            "Model $modelId is not active. Activate it before translating."
        }
        engine.generate(sourceTokenIds, sourceAttentionMask, maxNewTokens)
    }

    suspend fun deleteModel(modelId: String) = operationMutex.withLock {
        if (activeModelId == modelId) {
            closeEngine()
        }
        modelRepository.deleteModel(modelId)
    }

    suspend fun activeModelId(): String? = operationMutex.withLock {
        activeModelId.takeIf { engineReady }
    }

    suspend fun close() = operationMutex.withLock { closeEngine() }

    private suspend fun closeEngine() {
        withContext(NonCancellable + ioDispatcher) { engine.close() }
        activeModelId = null
        engineReady = false
    }

    private suspend fun closeEngineAfterFailure(failure: Exception) {
        try {
            closeEngine()
        } catch (cleanupFailure: Exception) {
            failure.addSuppressed(cleanupFailure)
        }
    }
}

class ModelNotInstalledException(modelId: String) :
    IllegalStateException("Model $modelId is not installed.")
