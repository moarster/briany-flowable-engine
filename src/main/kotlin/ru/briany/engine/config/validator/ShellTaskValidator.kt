package ru.briany.engine.config.validator

import org.flowable.bpmn.model.BpmnModel
import org.flowable.bpmn.model.Process
import org.flowable.bpmn.model.ServiceTask
import org.flowable.validation.ValidationError
import org.flowable.validation.validator.ProcessLevelValidator

/**
 * Rejects BPMN processes that contain Shell Tasks at deploy time.
 * Defense-in-depth: even if CustomActivityBehaviorFactory blocks execution,
 * it is better to fail fast during deployment.
 */
class ShellTaskValidator : ProcessLevelValidator() {
    override fun executeValidation(
        bpmnModel: BpmnModel,
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        val serviceTasks = process.findFlowElementsOfType(ServiceTask::class.java)

        for (serviceTask in serviceTasks) {
            if (serviceTask.type == ServiceTask.SHELL_TASK) {
                addError(
                    errors,
                    "shell-task-prohibited",
                    process,
                    serviceTask,
                    "Shell tasks are prohibited. Activity '${serviceTask.id}' uses type='shell' which is not allowed in this environment.",
                )
            }
        }
    }
}
