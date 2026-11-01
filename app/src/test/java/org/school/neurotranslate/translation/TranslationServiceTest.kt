package org.school.neurotranslate.translation

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    @Test
    fun deletingActiveModelClosesEngineBeforeRemovingFiles() = runBlocking {
        val events = mutableListOf<String>()
        val service = TranslationService(RecordingEngine(events), RecordingRepository(events))

        service.activateModel(MODEL_ID)
        events.clear()
        service.deleteModel(MODEL_ID)

        assertEquals(listOf("close", "delete"), events)
        assertNull(service.activeModelId())
    }

    @Test
    fun failedActivationClosesPartialSessionAndDoesNotMarkModelActive() = runBlocking {
        val events = mutableListOf<String>()
        val service = TranslationService(RecordingEngine(events, failLoad = true), RecordingRepository(events))

        val failure = try {
            service.activateModel(MODEL_ID)
            null
        } catch (caught: IllegalStateException) {
            caught
        }

        assertTrue(failure is IllegalStateException)
        assertNull(service.activeModelId())
        assertTrue(events.count { it == "close" } >= 2)
    }

    @Test
    fun deleteWaitsUntilTranslationFinishes() = runBlocking {
        val events = mutableListOf<String>()
        val generationStarted = CompletableDeferred<Unit>()
        val allowGenerationToFinish = CompletableDeferred<Unit>()
        val engine = RecordingEngine(
            events = events,
            generationStarted = generationStarted,
            allowGenerationToFinish = allowGenerationToFinish
        )
        val service = TranslationService(engine, RecordingRepository(events))
        service.activateModel(MODEL_ID)

        val translation = async { service.translate(MODEL_ID, longArrayOf(1), longArrayOf(1), 16) }
        generationStarted.await()
        val deletion = launch { service.deleteModel(MODEL_ID) }
        yield()
        assertFalse("Repository deletion must wait behind translation.", events.contains("delete"))

        allowGenerationToFinish.complete(Unit)
        translation.await()
        deletion.join()

        assertTrue(events.indexOf("generate-end") < events.indexOf("delete"))
        assertTrue(events.indexOf("close") < events.indexOf("delete"))
    }

    private class RecordingEngine(
        private val events: MutableList<String>,
        private val failLoad: Boolean = false,
        private val generationStarted: CompletableDeferred<Unit>? = null,
        private val allowGenerationToFinish: CompletableDeferred<Unit>? = null
    ) : TranslationEngine {
        override suspend fun load(model: MarianModelFiles) {
            events += "load"
            if (failLoad) error("load failed")
        }

        override suspend fun generate(
            sourceTokenIds: LongArray,
            sourceAttentionMask: LongArray,
            maxNewTokens: Int
        ): LongArray {
            events += "generate-start"
            generationStarted?.complete(Unit)
            allowGenerationToFinish?.await()
            events += "generate-end"
            return longArrayOf(2)
        }

        override fun close() {
            events += "close"
        }
    }

    private class RecordingRepository(private val events: MutableList<String>) : ModelRepository {
        override suspend fun findInstalledModel(modelId: String): MarianModelFiles {
            events += "find"
            return MarianModelFiles(
                id = modelId,
                encoder = File("encoder.onnx"),
                decoder = File("decoder.onnx"),
                encoderSha256 = "encoder-hash",
                decoderSha256 = "decoder-hash",
                decoderStartTokenId = 0,
                eosTokenId = 1,
                maxLength = 16,
                vocabularySize = 8
            )
        }

        override suspend fun deleteModel(modelId: String) {
            events += "delete"
        }

        override suspend fun createStagingDirectory(modelId: String): File = error("unused")
        override suspend fun writeStagedManifest(modelId: String, stagingDirectory: File) = error("unused")
        override suspend fun installStagedModel(modelId: String, stagingDirectory: File) = error("unused")
        override suspend fun recoverInterruptedInstalls() = error("unused")
        override suspend fun installedModelIds(): Set<String> = error("unused")
        override suspend fun installedBytes(): Long = error("unused")
    }

    private companion object {
        const val MODEL_ID = "en-ru"
    }
}
