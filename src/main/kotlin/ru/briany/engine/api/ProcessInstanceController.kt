package ru.briany.engine.api

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.common.api.params.StateFilter
import ru.briany.generated.api.ProcessInstanceApi
import ru.briany.generated.model.ActivityInstance
import ru.briany.generated.model.CancelProcessInstanceRequest
import ru.briany.generated.model.ProcessInstance
import ru.briany.generated.model.ProcessInstancePage
import ru.briany.generated.model.StartProcessInstanceRequest
import ru.briany.generated.model.Variable
import java.util.UUID

@RestController
class ProcessInstanceController(
    private val processInstanceFacade: ProcessInstanceFacade,
    private val processInstanceService: ProcessInstanceService,
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

    override fun cancelProcessInstance(
        id: String,
        cancelProcessInstanceRequest: CancelProcessInstanceRequest?,
    ): ResponseEntity<Unit> {
        processInstanceService.cancel(id, cancelProcessInstanceRequest?.reason)
        return ResponseEntity.noContent().build()
    }

    override fun deleteProcessInstance(id: String): ResponseEntity<Unit> {
        processInstanceService.delete(id)
        return ResponseEntity.noContent().build()
    }

    override fun getProcessInstance(id: String): ResponseEntity<ProcessInstance> =
        ResponseEntity.ok(processInstanceFacade.getProcessInstance(id))

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

    override fun listProcessInstanceActivities(id: String): ResponseEntity<List<ActivityInstance>> =
        ResponseEntity.ok(processInstanceService.activities(id))

    override fun listProcessInstanceVariables(id: String): ResponseEntity<List<Variable>> =
        ResponseEntity.ok(processInstanceService.variables(id))

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

    override fun startProcessInstance(
        key: String,
        startProcessInstanceRequest: StartProcessInstanceRequest?,
    ): ResponseEntity<ProcessInstance> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(processInstanceService.start(key, startProcessInstanceRequest))
}
