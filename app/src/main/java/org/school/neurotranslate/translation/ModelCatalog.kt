package org.school.neurotranslate.translation

import android.content.res.AssetManager
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

class ModelCatalog private constructor(
    private val models: Map<String, ModelPackageSpec>
) {
    fun get(modelId: String): ModelPackageSpec =
        models[modelId] ?: throw UnknownModelException(modelId)

    fun all(): List<ModelPackageSpec> = models.values.toList()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromAssets(assets: AssetManager): ModelCatalog {
            val document = assets.open(CATALOG_ASSET).use {
                json.decodeFromStream<ModelCatalogDocument>(it)
            }
            val packages = document.models.map { it.toPackageSpec() }
            require(packages.map(ModelPackageSpec::id).distinct().size == packages.size) {
                "Model catalog contains duplicate ids."
            }
            return ModelCatalog(packages.associateBy(ModelPackageSpec::id))
        }

        private fun CatalogModel.toPackageSpec(): ModelPackageSpec {
            require(id.matches(MODEL_ID_PATTERN)) { "Invalid model id $id." }
            require(onnxRepository.revision.matches(REVISION_PATTERN)) {
                "Model $id must use an immutable upstream revision."
            }
            val repository = onnxRepository.repository
            require(repository.matches(REPOSITORY_PATTERN)) { "Invalid model repository for $id." }
            val modelFiles = onnx.files.map { it.toArtifact(repository, onnxRepository.revision) }
            val tokenizerFiles = listOf(
                tokenizer.source,
                tokenizer.target
            ).map { it.toArtifact(repository, onnxRepository.revision) }
            val allFiles = modelFiles + tokenizerFiles
            require(allFiles.map(ModelArtifactSpec::path).distinct().size == allFiles.size) {
                "Model $id contains duplicate artifact paths."
            }
            require(allFiles.all { it.sizeBytes > 0 && it.sha256.matches(SHA256_PATTERN) }) {
                "Model $id has an invalid artifact size or SHA-256."
            }
            require(configuration.modelType == "marian")
            require(configuration.vocabularySize > 0)
            require(configuration.maxLength in 2..512)
            require(configuration.eosTokenId in 0 until configuration.vocabularySize)
            require(configuration.decoderStartTokenId in 0 until configuration.vocabularySize)

            val encoder = modelFiles.single { it.path == ENCODER_PATH }
            val decoder = modelFiles.single { it.path == DECODER_PATH }
            return ModelPackageSpec(
                id = id,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                revision = onnxRepository.revision,
                configuration = configuration,
                artifacts = allFiles,
                encoder = encoder,
                decoder = decoder
            )
        }

        private fun CatalogArtifact.toArtifact(repository: String, revision: String): ModelArtifactSpec {
            validateRelativePath(path)
            val encodedPath = path.split('/').joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
            return ModelArtifactSpec(
                path = path,
                sizeBytes = sizeBytes,
                sha256 = sha256.lowercase(),
                url = "https://huggingface.co/$repository/resolve/$revision/$encodedPath?download=true"
            )
        }

        private fun validateRelativePath(path: String) {
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) {
                "Invalid model artifact path."
            }
            require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                "Invalid model artifact path."
            }
        }

        private const val CATALOG_ASSET = "models/catalog.json"
        private const val ENCODER_PATH = "onnx/encoder_model_quantized.onnx"
        private const val DECODER_PATH = "onnx/decoder_model_quantized.onnx"
        private val MODEL_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")
        private val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        private val REVISION_PATTERN = Regex("[a-fA-F0-9]{40}")
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}

data class ModelArtifactSpec(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String
)

data class ModelPackageSpec(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val revision: String,
    val configuration: MarianModelConfiguration,
    val artifacts: List<ModelArtifactSpec>,
    val encoder: ModelArtifactSpec,
    val decoder: ModelArtifactSpec
) {
    val totalBytes: Long = artifacts.sumOf(ModelArtifactSpec::sizeBytes)

    fun toEngineFiles(directory: File): MarianModelFiles = MarianModelFiles(
        id = id,
        encoder = File(directory, encoder.path),
        decoder = File(directory, decoder.path),
        encoderSha256 = encoder.sha256,
        decoderSha256 = decoder.sha256,
        decoderStartTokenId = configuration.decoderStartTokenId.toLong(),
        eosTokenId = configuration.eosTokenId.toLong(),
        maxLength = configuration.maxLength,
        vocabularySize = configuration.vocabularySize
    )
}

@Serializable
data class InstalledPackageManifest(
    val modelId: String,
    val revision: String,
    val artifacts: List<InstalledArtifact>
)

@Serializable
data class InstalledArtifact(
    val path: String,
    val sizeBytes: Long,
    val sha256: String
)

@Serializable
private data class ModelCatalogDocument(val models: List<CatalogModel>)

@Serializable
private data class CatalogModel(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val onnxRepository: ModelRepositoryRevision,
    val configuration: MarianModelConfiguration,
    val onnx: OnnxCatalog,
    val tokenizer: TokenizerCatalog
)

@Serializable
private data class ModelRepositoryRevision(val repository: String, val revision: String)

@Serializable
data class MarianModelConfiguration(
    val modelType: String,
    val vocabularySize: Int,
    val decoderStartTokenId: Int,
    val eosTokenId: Int,
    val maxLength: Int
)

@Serializable
private data class OnnxCatalog(val files: List<CatalogArtifact>)

@Serializable
private data class CatalogArtifact(val path: String, val sizeBytes: Long, val sha256: String)

@Serializable
private data class TokenizerCatalog(val source: CatalogArtifact, val target: CatalogArtifact)

class UnknownModelException(modelId: String) : IllegalArgumentException("Unknown model $modelId.")

class InvalidInstalledPackageException(modelId: String, cause: SerializationException? = null) :
    IllegalStateException("Installed package $modelId is incomplete or invalid.", cause)
