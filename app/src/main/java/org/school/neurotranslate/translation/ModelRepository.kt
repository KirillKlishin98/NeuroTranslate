package org.school.neurotranslate.translation

import java.io.File

interface ModelRepository {
    suspend fun findInstalledModel(modelId: String): MarianModelFiles?

    suspend fun deleteModel(modelId: String)

    suspend fun createStagingDirectory(modelId: String): File

    suspend fun writeStagedManifest(modelId: String, stagingDirectory: File)

    suspend fun installStagedModel(modelId: String, stagingDirectory: File)

    suspend fun recoverInterruptedInstalls()

    suspend fun installedModelIds(): Set<String>

    suspend fun installedBytes(): Long
}
