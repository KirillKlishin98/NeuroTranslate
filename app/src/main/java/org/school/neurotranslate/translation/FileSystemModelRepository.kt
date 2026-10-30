package org.school.neurotranslate.translation

import android.system.Os
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

class FileSystemModelRepository(
    rootDirectory: File,
    private val catalog: ModelCatalog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelRepository {
    private val root = rootDirectory.canonicalFile
    private val installedRoot = File(root, INSTALLED_DIRECTORY)
    private val stagingRoot = File(root, STAGING_DIRECTORY)

    override suspend fun findInstalledModel(modelId: String): MarianModelFiles? =
        withContext(ioDispatcher) { findInstalledModelLocked(catalog.get(modelId)) }

    override suspend fun deleteModel(modelId: String) = withContext(ioDispatcher) {
        val model = catalog.get(modelId)
        deleteIfPresent(installedDirectory(model))
        deleteIfPresent(stagingDirectory(model))
    }

    override suspend fun createStagingDirectory(modelId: String): File = withContext(ioDispatcher) {
        val directory = stagingDirectory(catalog.get(modelId))
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create model staging directory.")
        }
        directory
    }

    override suspend fun writeStagedManifest(modelId: String, stagingDirectory: File) =
        withContext(ioDispatcher) {
            val model = catalog.get(modelId)
            requireChild(stagingDirectory, stagingRoot)
            val manifest = manifestFor(model)
            File(stagingDirectory, MANIFEST_NAME).outputStream().use {
                json.encodeToStream(manifest, it)
                it.flush()
                it.fd.sync()
            }
        }

    override suspend fun installStagedModel(modelId: String, stagingDirectory: File) =
        withContext(ioDispatcher) {
            val model = catalog.get(modelId)
            requireChild(stagingDirectory, stagingRoot)
            val destination = installedDirectory(model)
            if (destination.exists()) throw ModelPackageAlreadyInstalledException(modelId)
            checkStagedPackage(model, stagingDirectory)
            if (!installedRoot.exists() && !installedRoot.mkdirs()) {
                throw IOException("Could not create model installation directory.")
            }
            requireChild(destination, installedRoot)
            try {
                Os.rename(stagingDirectory.absolutePath, destination.absolutePath)
            } catch (error: android.system.ErrnoException) {
                throw IOException("Could not atomically install model $modelId.", error)
            }
        }

    override suspend fun recoverInterruptedInstalls() = withContext(ioDispatcher) {
        if (!stagingRoot.isDirectory) return@withContext
        stagingRoot.listFiles().orEmpty().forEach { stagingDirectory ->
            val model = try {
                catalog.get(stagingDirectory.name)
            } catch (_: UnknownModelException) {
                null
            }
            val hasResumeState = File(stagingDirectory, RESUME_DIRECTORY).isDirectory &&
                File(stagingDirectory, RESUME_DIRECTORY).walkTopDown().any { it.isFile }
            if (model == null || !hasResumeState) deleteIfPresent(stagingDirectory)
        }
    }

    override suspend fun installedModelIds(): Set<String> = withContext(ioDispatcher) {
        catalog.all().filter { findInstalledModelLocked(it) != null }.mapTo(mutableSetOf()) { it.id }
    }

    override suspend fun installedBytes(): Long = withContext(ioDispatcher) {
        catalog.all().sumOf { model ->
            if (findInstalledModelLocked(model) == null) 0L
            else installedDirectory(model).walkTopDown().filter(File::isFile).sumOf(File::length)
        }
    }

    private fun findInstalledModelLocked(model: ModelPackageSpec): MarianModelFiles? {
        val directory = installedDirectory(model)
        if (!directory.isDirectory) return null
        val metadataFile = File(directory, MANIFEST_NAME)
        if (!metadataFile.isFile) return null
        val metadata = try {
            metadataFile.inputStream().use { json.decodeFromStream<InstalledPackageManifest>(it) }
        } catch (_: SerializationException) {
            return null
        } catch (_: IOException) {
            return null
        }
        if (metadata != manifestFor(model)) return null
        if (model.artifacts.any { artifact ->
                val file = childFile(directory, artifact.path)
                !file.isFile || file.length() != artifact.sizeBytes
            }
        ) return null
        return model.toEngineFiles(directory)
    }

    private fun checkStagedPackage(model: ModelPackageSpec, directory: File) {
        val metadataFile = File(directory, MANIFEST_NAME)
        if (!metadataFile.isFile) throw InvalidInstalledPackageException(model.id)
        val metadata = try {
            metadataFile.inputStream().use { json.decodeFromStream<InstalledPackageManifest>(it) }
        } catch (error: SerializationException) {
            throw InvalidInstalledPackageException(model.id, error)
        }
        require(metadata == manifestFor(model)) { "Staged manifest does not match the trusted catalog." }
        if (model.artifacts.any { artifact ->
                val file = childFile(directory, artifact.path)
                !file.isFile || file.length() != artifact.sizeBytes
            }
        ) throw InvalidInstalledPackageException(model.id)
    }

    private fun manifestFor(model: ModelPackageSpec) = InstalledPackageManifest(
        modelId = model.id,
        revision = model.revision,
        artifacts = model.artifacts.map { InstalledArtifact(it.path, it.sizeBytes, it.sha256) }
    )

    private fun installedDirectory(model: ModelPackageSpec): File = File(installedRoot, model.id)

    private fun stagingDirectory(model: ModelPackageSpec): File = File(stagingRoot, model.id)

    private fun childFile(directory: File, relativePath: String): File {
        val child = File(directory, relativePath).canonicalFile
        requireChild(child, directory)
        return child
    }

    private fun requireChild(file: File, parent: File) {
        val canonicalParent = parent.canonicalFile
        val canonicalFile = file.canonicalFile
        check(canonicalFile.path.startsWith(canonicalParent.path + File.separator)) {
            "Model path escaped its storage directory."
        }
    }

    private fun deleteIfPresent(file: File) {
        if (!file.exists()) return
        requireChild(file, root)
        file.walkBottomUp().forEach { entry ->
            if (!entry.delete() && entry.exists()) throw IOException("Could not remove ${entry.name}.")
        }
    }

    private companion object {
        const val INSTALLED_DIRECTORY = "installed"
        const val STAGING_DIRECTORY = ".staging"
        const val RESUME_DIRECTORY = ".resume"
        const val MANIFEST_NAME = "installed-manifest.json"
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    }
}

class ModelPackageAlreadyInstalledException(modelId: String) :
    IllegalStateException("Model $modelId already has an installation directory.")
