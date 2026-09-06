package ru.briany.workflow.application

import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.briany.common.api.params.VersionFilter
import ru.briany.common.archive.readZipEntries
import ru.briany.generated.api.ApplicationApi
import ru.briany.generated.model.Application
import ru.briany.generated.model.ApplicationPage

@RestController
class ApplicationController(
    private val applicationService: ApplicationService,
    private val deploymentService: DeploymentService,
) : ApplicationApi {
    override fun listApplications(pageable: Pageable): ResponseEntity<ApplicationPage> =
        ResponseEntity.ok(applicationService.getApplications(null, VersionFilter.Latest, pageable))

    override fun listApplicationsVersions(pageable: Pageable): ResponseEntity<ApplicationPage> =
        ResponseEntity.ok(applicationService.getApplications(null, VersionFilter.All, pageable))

    override fun listApplicationVersions(
        key: String,
        pageable: Pageable,
    ): ResponseEntity<ApplicationPage> =
        ResponseEntity.ok(
            applicationService.getApplications(key, VersionFilter.All, pageable),
        )

    override fun getApplication(key: String): ResponseEntity<Application> =
        ResponseEntity.ok(
            applicationService.getApplication(key),
        )

    override fun getApplicationVersion(
        key: String,
        version: Int,
    ): ResponseEntity<Application> = ResponseEntity.ok(applicationService.getApplication(key, version))

    override fun deployApplication(file: MultipartFile): ResponseEntity<Application> {
        val bformFiles = mutableMapOf<String, ByteArray>()
        val flwFiles = mutableMapOf<String, ByteArray>()

        file.inputStream.readZipEntries().forEach { (name, bytes) ->
            if (name.endsWith(BFORM_SUFFIX)) {
                bformFiles[name] = bytes
            } else {
                flwFiles[name] = bytes
            }
        }

        return ResponseEntity.ok(
            deploymentService.deploy(
                deploymentName = file.originalFilename ?: "deployment",
                flwFiles = flwFiles,
                bformFiles = bformFiles,
            ),
        )
    }

    companion object {
        const val BFORM_SUFFIX = ".bform"
    }
}
