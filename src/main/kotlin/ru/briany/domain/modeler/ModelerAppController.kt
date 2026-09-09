package ru.briany.domain.modeler

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.briany.generated.api.ModelerAppApi
import ru.briany.generated.model.CreateModelerAppRequest
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppFile
import ru.briany.generated.model.ModelerAppFileSummary
import ru.briany.generated.model.ModelerAppPage
import ru.briany.generated.model.ModelerAppState
import ru.briany.generated.model.ModelerFileType
import ru.briany.generated.model.UpdateModelerAppRequest

@RestController
class ModelerAppController(
    private val modelerAppService: ModelerAppService,
    private val modelerAppDeployService: ModelerAppDeployService,
    private val modelerAppStatsService: ModelerAppStatsService,
) : ModelerAppApi {
    override fun listModelerApps(
        includeStats: Boolean,
        state: ModelerAppState?,
        pageable: Pageable,
    ): ResponseEntity<ModelerAppPage> =
        ResponseEntity.ok(modelerAppStatsService.list(ModelerStateFilter.of(state), pageable, includeStats))

    override fun createModelerApp(createModelerAppRequest: CreateModelerAppRequest): ResponseEntity<ModelerApp> =
        ResponseEntity.status(HttpStatus.CREATED).body(modelerAppService.create(createModelerAppRequest))

    override fun getModelerApp(
        key: String,
        includeStats: Boolean,
    ): ResponseEntity<ModelerApp> = ResponseEntity.ok(modelerAppStatsService.get(key, includeStats))

    override fun updateModelerApp(
        key: String,
        updateModelerAppRequest: UpdateModelerAppRequest,
    ): ResponseEntity<ModelerApp> = ResponseEntity.ok(modelerAppService.update(key, updateModelerAppRequest))

    override fun deleteModelerApp(key: String): ResponseEntity<Unit> {
        modelerAppService.delete(key)
        return ResponseEntity.noContent().build()
    }

    override fun deployModelerApp(key: String): ResponseEntity<ModelerApp> = ResponseEntity.ok(modelerAppDeployService.deploy(key))

    override fun undeployModelerApp(key: String): ResponseEntity<ModelerApp> = ResponseEntity.ok(modelerAppDeployService.undeploy(key))

    override fun listModelerAppFiles(key: String): ResponseEntity<List<ModelerAppFileSummary>> =
        ResponseEntity.ok(modelerAppService.listFiles(key))

    override fun upsertModelerAppFile(
        key: String,
        file: MultipartFile,
    ): ResponseEntity<ModelerAppFile> {
        val result = modelerAppService.upsertFile(key, file.originalFilename.orEmpty(), file.bytes)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(ModelerAppMapper.toFile(key, result.file))
    }

    override fun getModelerAppFile(
        key: String,
        fileKey: String,
    ): ResponseEntity<ModelerAppFile> = ResponseEntity.ok(modelerAppService.getFile(key, fileKey))

    override fun updateModelerAppFileContent(
        key: String,
        fileKey: String,
        file: MultipartFile,
    ): ResponseEntity<ModelerAppFile> =
        ResponseEntity.ok(
            modelerAppService.updateFileContent(key, fileKey, file.originalFilename.orEmpty(), file.bytes),
        )

    override fun getModelerAppFileContent(
        key: String,
        fileKey: String,
    ): ResponseEntity<Resource> {
        val file = modelerAppService.getFileEntity(key, fileKey)
        val mediaType =
            when (file.type) {
                ModelerFileType.BFORM -> MediaType.APPLICATION_JSON
                else -> MediaType.APPLICATION_XML
            }
        return ResponseEntity
            .ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.resourceName}\"")
            .body(ByteArrayResource(file.content))
    }

    override fun deleteModelerAppFile(
        key: String,
        fileKey: String,
    ): ResponseEntity<Unit> {
        modelerAppService.deleteFile(key, fileKey)
        return ResponseEntity.noContent().build()
    }
}
