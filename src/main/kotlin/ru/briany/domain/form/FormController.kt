package ru.briany.domain.form

import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.common.api.params.IdOrKey
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.api.FormApi
import ru.briany.generated.model.Form
import ru.briany.generated.model.FormSummaryPage

@RestController
class FormController(
    private val formService: FormService,
) : FormApi {
    override fun getForm(key: String): ResponseEntity<Form> = ResponseEntity.ok(formService.getForm(IdOrKey.parse(key)))

    override fun getFormVersion(
        key: String,
        version: Int,
    ): ResponseEntity<Form> = ResponseEntity.ok(formService.getForm(key, version))

    override fun getFormVersions(
        key: String,
        pageable: Pageable,
    ): ResponseEntity<FormSummaryPage> = ResponseEntity.ok(formService.listForms(VersionFilter.All, pageable))

    override fun listForms(pageable: Pageable): ResponseEntity<FormSummaryPage> =
        ResponseEntity.ok(formService.listForms(VersionFilter.Latest, pageable))
}
