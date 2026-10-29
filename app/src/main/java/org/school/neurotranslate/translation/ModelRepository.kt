package org.school.neurotranslate.translation

interface ModelRepository {
    suspend fun findInstalledModel(modelId: String): MarianModelFiles?

    suspend fun deleteModel(modelId: String)
}
