package org.school.neurotranslate.translation

import java.io.Closeable
import java.io.File

interface TranslationEngine : Closeable {
    suspend fun load(model: MarianModelFiles)

    suspend fun generate(
        sourceTokenIds: LongArray,
        sourceAttentionMask: LongArray,
        maxNewTokens: Int
    ): LongArray
}

data class MarianModelFiles(
    val id: String,
    val encoder: File,
    val decoder: File,
    val encoderSha256: String,
    val decoderSha256: String,
    val decoderStartTokenId: Long,
    val eosTokenId: Long,
    val maxLength: Int,
    val vocabularySize: Int
)
