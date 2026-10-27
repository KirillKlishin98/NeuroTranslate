package org.school.neurotranslate.translation.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.concurrent.withLock
import org.school.neurotranslate.translation.MarianModelFiles
import org.school.neurotranslate.translation.TranslationEngine

class MarianOnnxEngine(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
) : TranslationEngine {
    private val lock = ReentrantLock()
    private var activeModel: ActiveModel? = null

    override suspend fun load(model: MarianModelFiles) = withContext(Dispatchers.IO) {
        lock.withLock {
            closeSessions()
            validateModelFiles(model)

            val encoderSession = createSession(model.encoder)
            try {
                val decoderSession = createSession(model.decoder)
                try {
                    validateSessionContracts(model.id, encoderSession, decoderSession)
                    activeModel = ActiveModel(model, encoderSession, decoderSession)
                } catch (error: Throwable) {
                    decoderSession.close()
                    throw error
                }
            } catch (error: Throwable) {
                encoderSession.close()
                throw error
            }
        }
    }

    override suspend fun generate(
        sourceTokenIds: LongArray,
        sourceAttentionMask: LongArray,
        maxNewTokens: Int
    ): LongArray = withContext(Dispatchers.Default) {
        val coroutineContext = currentCoroutineContext()
        lock.withLock {
            val active = checkNotNull(activeModel) { "Load a Marian model before generating." }
            val model = active.spec
            require(sourceTokenIds.isNotEmpty()) { "Source token IDs cannot be empty." }
            require(sourceTokenIds.size <= model.maxLength) { "Source exceeds the model's maximum sequence length." }
            require(sourceAttentionMask.size == sourceTokenIds.size) { "Attention mask must match source token IDs." }
            require(sourceTokenIds.all { it in 0 until model.vocabularySize.toLong() }) {
                "Source contains a token outside the model vocabulary."
            }
            require(sourceAttentionMask.all { it == 0L || it == 1L }) { "Attention mask values must be 0 or 1." }
            require(maxNewTokens in 1 until model.maxLength) {
                "Generation length must be between 1 and ${model.maxLength - 1}."
            }

            generateLocked(
                active = active,
                sourceTokenIds = sourceTokenIds,
                sourceAttentionMask = sourceAttentionMask,
                maxNewTokens = maxNewTokens,
                coroutineContext = coroutineContext
            )
        }
    }

    override fun close() {
        lock.withLock { closeSessions() }
    }

    private fun generateLocked(
        active: ActiveModel,
        sourceTokenIds: LongArray,
        sourceAttentionMask: LongArray,
        maxNewTokens: Int,
        coroutineContext: kotlin.coroutines.CoroutineContext
    ): LongArray {
        val generated = LongArray(maxNewTokens + 1)
        generated[0] = active.spec.decoderStartTokenId
        var generatedLength = 1
        val outputTokenIds = LongArray(maxNewTokens)
        var outputLength = 0

        createLongTensor(sourceTokenIds).use { inputIds ->
            createLongTensor(sourceAttentionMask).use { attentionMask ->
                active.encoder.run(
                    mapOf(
                        ENCODER_INPUT_IDS to inputIds,
                        ENCODER_ATTENTION_MASK to attentionMask
                    )
                ).use { encoderResult ->
                    val hiddenStates = encoderResult.get(ENCODER_OUTPUT)
                        .orElseThrow { IllegalStateException("Encoder did not return $ENCODER_OUTPUT.") } as OnnxTensor

                    repeat(maxNewTokens) {
                        coroutineContext.ensureActive()
                        val decoderInputIds = generated.copyOf(generatedLength)
                        val nextTokenId = createLongTensor(decoderInputIds).use { decoderIds ->
                            active.decoder.run(
                                mapOf(
                                    DECODER_ATTENTION_MASK to attentionMask,
                                    DECODER_INPUT_IDS to decoderIds,
                                    DECODER_ENCODER_HIDDEN_STATES to hiddenStates
                                ),
                                setOf(DECODER_OUTPUT_LOGITS)
                            ).use { decoderResult ->
                                val logits = decoderResult.get(DECODER_OUTPUT_LOGITS)
                                    .orElseThrow { IllegalStateException("Decoder did not return $DECODER_OUTPUT_LOGITS.") } as OnnxTensor
                                selectNextToken(logits, active.spec.vocabularySize)
                            }
                        }

                        if (nextTokenId == active.spec.eosTokenId) return outputTokenIds.copyOf(outputLength)
                        outputTokenIds[outputLength++] = nextTokenId
                        generated[generatedLength++] = nextTokenId
                    }
                }
            }
        }
        return outputTokenIds.copyOf(outputLength)
    }

    private fun createSession(modelFile: File): OrtSession =
        OrtSession.SessionOptions().use { options ->
            environment.createSession(modelFile.absolutePath, options)
        }

    private fun closeSessions() {
        val previous = activeModel ?: return
        activeModel = null
        try {
            previous.decoder.close()
        } finally {
            previous.encoder.close()
        }
    }

    private fun validateModelFiles(model: MarianModelFiles) {
        require(model.maxLength in 2..512) { "Unsupported max sequence length for ${model.id}." }
        require(model.vocabularySize > 0) { "Invalid vocabulary size for ${model.id}." }
        require(model.decoderStartTokenId in 0 until model.vocabularySize.toLong()) {
            "Invalid decoder start token for ${model.id}."
        }
        require(model.eosTokenId in 0 until model.vocabularySize.toLong()) { "Invalid EOS token for ${model.id}." }
        verifyFile(model.encoder, model.encoderSha256)
        verifyFile(model.decoder, model.decoderSha256)
    }

    private fun verifyFile(file: File, expectedSha256: String) {
        require(file.isFile) { "Model file is missing: ${file.name}." }
        require(expectedSha256.matches(SHA256_PATTERN)) { "Invalid trusted SHA-256 for ${file.name}." }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
        require(actual.equals(expectedSha256, ignoreCase = true)) {
            "SHA-256 mismatch for ${file.name}."
        }
    }

    private fun validateSessionContracts(
        modelId: String,
        encoder: OrtSession,
        decoder: OrtSession
    ) {
        require(encoder.inputNames == ENCODER_INPUT_NAMES) {
            "Unexpected encoder inputs for $modelId: ${encoder.inputNames}."
        }
        require(encoder.outputNames.contains(ENCODER_OUTPUT)) {
            "Encoder output $ENCODER_OUTPUT is missing for $modelId."
        }
        require(decoder.inputNames == DECODER_INPUT_NAMES) {
            "Unexpected decoder inputs for $modelId: ${decoder.inputNames}."
        }
        require(decoder.outputNames.contains(DECODER_OUTPUT_LOGITS)) {
            "Decoder output $DECODER_OUTPUT_LOGITS is missing for $modelId."
        }
    }

    private fun createLongTensor(values: LongArray): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(values.size * Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
            .put(values)
        buffer.flip()
        return OnnxTensor.createTensor(
            environment,
            buffer,
            longArrayOf(1, values.size.toLong())
        )
    }

    private fun selectNextToken(logits: OnnxTensor, vocabularySize: Int): Long {
        val values = checkNotNull(logits.floatBuffer) { "Decoder logits must be float32." }
        require(values.limit() >= vocabularySize && values.limit() % vocabularySize == 0) {
            "Decoder logits have an invalid output size."
        }

        val lastSequenceOffset = values.limit() - vocabularySize
        var bestToken = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (tokenId in 0 until vocabularySize) {
            val score = values.get(lastSequenceOffset + tokenId)
            if (score > bestScore) {
                bestScore = score
                bestToken = tokenId
            }
        }
        check(bestToken >= 0) { "Decoder logits contain no selectable token." }
        return bestToken.toLong()
    }

    private data class ActiveModel(
        val spec: MarianModelFiles,
        val encoder: OrtSession,
        val decoder: OrtSession
    )

    private companion object {
        const val ENCODER_INPUT_IDS = "input_ids"
        const val ENCODER_ATTENTION_MASK = "attention_mask"
        const val ENCODER_OUTPUT = "last_hidden_state"
        const val DECODER_ATTENTION_MASK = "encoder_attention_mask"
        const val DECODER_INPUT_IDS = "input_ids"
        const val DECODER_ENCODER_HIDDEN_STATES = "encoder_hidden_states"
        const val DECODER_OUTPUT_LOGITS = "logits"
        const val HASH_BUFFER_SIZE = 64 * 1024
        val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
        val ENCODER_INPUT_NAMES = setOf(ENCODER_INPUT_IDS, ENCODER_ATTENTION_MASK)
        val DECODER_INPUT_NAMES = setOf(
            DECODER_ATTENTION_MASK,
            DECODER_INPUT_IDS,
            DECODER_ENCODER_HIDDEN_STATES
        )
    }
}
