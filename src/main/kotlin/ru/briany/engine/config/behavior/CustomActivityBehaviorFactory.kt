package ru.briany.engine.config.behavior

import org.flowable.bpmn.model.ScriptTask
import org.flowable.bpmn.model.ServiceTask
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.impl.bpmn.behavior.ScriptTaskActivityBehavior
import org.flowable.engine.impl.bpmn.behavior.ShellActivityBehavior
import org.flowable.engine.impl.bpmn.parser.factory.DefaultActivityBehaviorFactory
import org.slf4j.LoggerFactory

/**
 * Blocks shell and script tasks at runtime unconditionally. This is defense-in-depth: the
 * guaranteed control is deploy-time rejection (`ShellTaskValidator` for shell,
 * `SafeBpmnDeploymentValidator` for scripts), so a definition carrying either never reaches the
 * engine. These overrides ensure that even if such a behavior were somehow instantiated it fails
 * closed rather than executing.
 */
class CustomActivityBehaviorFactory : DefaultActivityBehaviorFactory() {
    private val log = LoggerFactory.getLogger(CustomActivityBehaviorFactory::class.java)

    override fun createShellActivityBehavior(serviceTask: ServiceTask): ShellActivityBehavior {
        log.warn("ShellTask blocked - creating DisabledActivityBehavior")
        return DisabledActivityBehavior("Shell tasks are not allowed in this environment")
    }

    override fun createScriptTaskActivityBehavior(scriptTask: ScriptTask): ScriptTaskActivityBehavior {
        log.warn("ScriptTask blocked - creating DisabledScriptTaskBehavior")
        return DisabledScriptTaskBehavior("Script tasks are not allowed in this environment")
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

/**
 * Script sibling of [DisabledActivityBehavior]. The specific `ScriptTaskActivityBehavior` return
 * type of `createScriptTaskActivityBehavior` rules out reusing [DisabledActivityBehavior] (which
 * extends `ShellActivityBehavior`), so this blocks on the same failure path. Constructor args are
 * placeholders - the behavior never runs a script, it only fails closed.
 */
class DisabledScriptTaskBehavior(
    private val reason: String,
) : ScriptTaskActivityBehavior("", "", null) {
    private val log = LoggerFactory.getLogger(DisabledScriptTaskBehavior::class.java)

    override fun execute(execution: DelegateExecution) {
        val activityId = execution.currentActivityId
        val processDefinitionId = execution.processDefinitionId
        log.error("Blocked prohibited script task: {} in process: {} - {}", activityId, processDefinitionId, reason)
        throw SecurityException("Activity '$activityId' in process '$processDefinitionId' is prohibited: $reason")
    }
}
