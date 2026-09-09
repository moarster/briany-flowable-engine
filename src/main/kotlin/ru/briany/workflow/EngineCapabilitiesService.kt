package ru.briany.workflow

import org.flowable.engine.ProcessEngine
import org.springframework.stereotype.Service
import ru.briany.engine.config.BpmEngineProperties
import ru.briany.engine.config.BpmnActivityType
import ru.briany.engine.config.validator.SafeBpmnDeploymentValidator
import ru.briany.generated.model.EngineCapabilities

/**
 * Reports the constraints this deployment enforces on models it accepts, so a modeling client can
 * offer only what deployment validation will let through. Deliberately never exposes a script or
 * shell flag - those are prohibited unconditionally (see [SafeBpmnDeploymentValidator]) and simply
 * absent from [EngineCapabilities.allowedActivityTypes].
 */
@Service
class EngineCapabilitiesService(
    private val bpmConfig: BpmEngineProperties,
) {
    fun capabilities(): EngineCapabilities {
        val whitelist = bpmConfig.whitelist
        return EngineCapabilities(
            allowedActivityTypes = whitelist.activities.map { it.name.lowercase() },
            allowedDelegateBeans = SafeBpmnDeploymentValidator.ALLOWED_DELEGATE_BEANS.toList(),
            allowedClassPrefixes = SafeBpmnDeploymentValidator.ALLOWED_CLASS_PREFIXES,
            flowableVersion = ProcessEngine.VERSION,
            activityWhitelistEnabled = whitelist.enabled,
            httpTaskEnabled = !whitelist.enabled || whitelist.activities.contains(BpmnActivityType.HTTP_SERVICE_TASK),
        )
    }
}
