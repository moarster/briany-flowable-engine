package ru.briany.engine.api

import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.common.api.params.StateFilter
import ru.briany.generated.api.ProcessInstanceApi
import ru.briany.generated.model.ProcessInstancePage
import java.util.UUID

@RestController
class ProcessInstanceController(
    private val processInstanceFacade: ProcessInstanceFacade,
) : ProcessInstanceApi {
    override fun listProcessInstances(
        state: String?,
        pageable: Pageable,
    ): ResponseEntity<ProcessInstancePage> =
        ResponseEntity.ok(
            processInstanceFacade.getProcessInstances(
                stateFilter = StateFilter.parse(state),
                pageable = pageable,
            ),
        )

    fun listApplicationProcessInstances(
        id: UUID,
        state: String?,
        pageable: Pageable,
    ): ResponseEntity<ProcessInstancePage> =
        ResponseEntity.ok(
            processInstanceFacade.getApplicationProcessInstances(
                appId = id,
                stateFilter = StateFilter.parse(state),
                pageable = pageable,
            ),
        )

    fun listProcessDefinitionInstances(
        id: String,
        state: String?,
        pageable: Pageable,
    ): ResponseEntity<ProcessInstancePage> =
        ResponseEntity.ok(
            processInstanceFacade.getProcessInstances(
                processDefId = id,
                stateFilter = StateFilter.parse(state),
                pageable = pageable,
            ),
        )

    override fun listProcessDefinitionVersionInstances(
        key: String,
        version: Int,
        state: String?,
        pageable: Pageable,
    ): ResponseEntity<ProcessInstancePage> =
        ResponseEntity.ok(
            processInstanceFacade.getProcessInstancesByProcessDefKey(
                processDefKey = key,
                version = version,
                stateFilter = StateFilter.parse(state),
                pageable = pageable,
            ),
        )

    override fun listProcessProcessInstances(
        key: String,
        state: String?,
        pageable: Pageable,
    ): ResponseEntity<ProcessInstancePage> =
        ResponseEntity.ok(
            processInstanceFacade.getProcessInstancesByProcessDefKey(
                processDefKey = key,
                version = null,
                stateFilter = StateFilter.parse(state),
                pageable = pageable,
            ),
        )
}
