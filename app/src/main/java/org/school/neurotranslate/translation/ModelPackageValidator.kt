package org.school.neurotranslate.translation

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelPackageValidator {
    suspend fun validate(model: ModelPackageSpec, directory: File)
}

class OnnxModelPackageValidator(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelPackageValidator {
    override suspend fun validate(model: ModelPackageSpec, directory: File) = withContext(ioDispatcher) {
        validateGraph(model.id, File(directory, model.encoder.path), ENCODER_INPUTS, ENCODER_OUTPUTS)
        validateGraph(model.id, File(directory, model.decoder.path), DECODER_INPUTS, DECODER_OUTPUTS)
    }

    private fun validateGraph(
        modelId: String,
        file: File,
        expectedInputs: Set<String>,
        expectedOutputs: Set<String>
    ) {
        require(file.isFile) { "ONNX artifact is missing for model $modelId." }
        OrtSession.SessionOptions().use { options ->
            environment.createSession(file.absolutePath, options).use { session ->
                require(session.inputNames == expectedInputs) {
                    "Unexpected ONNX inputs in ${file.name} for model $modelId."
                }
                require(session.outputNames.containsAll(expectedOutputs)) {
                    "Required ONNX outputs are missing in ${file.name} for model $modelId."
                }
            }
        }
    }

    private companion object {
        val ENCODER_INPUTS = setOf("input_ids", "attention_mask")
        val ENCODER_OUTPUTS = setOf("last_hidden_state")
        val DECODER_INPUTS = setOf("encoder_attention_mask", "input_ids", "encoder_hidden_states")
        val DECODER_OUTPUTS = setOf("logits")
    }
}
