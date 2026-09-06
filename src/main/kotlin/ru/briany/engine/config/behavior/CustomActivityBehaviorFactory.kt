package ru.briany.engine.config.behavior

import org.flowable.bpmn.model.ServiceTask
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.impl.bpmn.behavior.ShellActivityBehavior
import org.flowable.engine.impl.bpmn.parser.factory.DefaultActivityBehaviorFactory
import org.slf4j.LoggerFactory
import ru.briany.engine.config.BpmEngineProperties

class CustomActivityBehaviorFactory(
    private val bpmConfig: BpmEngineProperties,
) : DefaultActivityBehaviorFactory() {
    private val log = LoggerFactory.getLogger(CustomActivityBehaviorFactory::class.java)

    override fun createShellActivityBehavior(serviceTask: ServiceTask): ShellActivityBehavior? =
        if (isAllowed("shell")) {
            log.info("ShellTask allowed, creating ShellActivityBehavior")
            super.createShellActivityBehavior(serviceTask)
        } else {
            log.warn("ShellTask not allowed - blocked attempt to create ShellActivityBehavior")
            DisabledActivityBehavior("Shell tasks are not allowed in this environment")
        }

    // Other activity types can be blocked the same way:
    // override fun createXXXBehavior(...) = if (isAllowed("xxx")) super.createXXXBehavior(...) else DisabledActivityBehavior("...")

    private fun isAllowed(activityType: String): Boolean {
        if (!bpmConfig.whitelist.enabled) {
            return true
        }

        val allowed = bpmConfig.whitelist.activities.any { it.type == activityType }
        log.debug("Activity type {} is {}", activityType, if (allowed) "allowed" else "denied")
        return allowed
    }
}

class DisabledActivityBehavior(
    private val reason: String,
) : ShellActivityBehavior() {
    private val log = LoggerFactory.getLogger(DisabledActivityBehavior::class.java)

    override fun execute(execution: DelegateExecution) {
        block(execution)
    }

    override fun trigger(
        execution: DelegateExecution,
        signalName: String?,
        signalData: Any?,
    ) {
        block(execution)
    }

    private fun block(execution: DelegateExecution): Nothing {
        val activityId = execution.currentActivityId
        val processDefinitionId = execution.processDefinitionId
        log.error("Blocked prohibited activity: {} in process: {} - {}", activityId, processDefinitionId, reason)
        throw SecurityException("Activity '$activityId' in process '$processDefinitionId' is prohibited: $reason")
    }
}
