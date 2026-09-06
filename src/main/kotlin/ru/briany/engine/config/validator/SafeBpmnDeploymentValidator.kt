package ru.briany.engine.config.validator

import org.flowable.bpmn.model.BaseElement
import org.flowable.bpmn.model.BpmnModel
import org.flowable.bpmn.model.BusinessRuleTask
import org.flowable.bpmn.model.CollectionHandler
import org.flowable.bpmn.model.EventListener
import org.flowable.bpmn.model.FlowElement
import org.flowable.bpmn.model.FlowableListener
import org.flowable.bpmn.model.ImplementationType
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics
import org.flowable.bpmn.model.Process
import org.flowable.bpmn.model.ScriptTask
import org.flowable.bpmn.model.ServiceTask
import org.flowable.bpmn.model.UserTask
import org.flowable.common.engine.impl.de.odysseus.el.tree.IdentifierNode
import org.flowable.common.engine.impl.de.odysseus.el.tree.TreeBuilderException
import org.flowable.common.engine.impl.de.odysseus.el.tree.impl.Builder
import org.flowable.validation.ValidationError
import org.flowable.validation.validator.ProcessLevelValidator
import ru.briany.engine.config.validator.SafeBpmnDeploymentValidator.Companion.ALLOWED_DELEGATE_BEANS

/**
 * Catches **deployment-time class injection**.
 *
 * A BPMN model can reference an FQCN for reflective instantiation
 * (`ReflectUtil.instantiate(className)`) in several places: `serviceTask class`,
 * execution/task/event listeners, `businessRuleTask class`, and multi-instance
 * `collectionHandler class`. Each of these bypasses the Groovy/JUEL sandbox entirely,
 * since Flowable calls `Class.forName(className).newInstance()` directly - an attacker
 * with deploy rights gets RCE from a single `<serviceTask flowable:class="..."/>`.
 *
 * `delegateExpression="..."` is checked too: it resolves through the Spring bean
 * registry, so an unrestricted expression could reach internal services
 * (`${runtimeService}`, `${repositoryService}`, ...). Only `${beanName}` where
 * `beanName` is in [ALLOWED_DELEGATE_BEANS] is allowed.
 */
class SafeBpmnDeploymentValidator : ProcessLevelValidator() {
    override fun executeValidation(
        bpmnModel: BpmnModel,
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        validateServiceTasks(process, errors)
        validateScriptTasks(process, errors)
        validateBusinessRuleTasks(process, errors)
        validateUserTasks(process, errors)
        validateProcessLevelListeners(process, errors)
        validateOtherFlowElementListeners(process, errors)
    }

    private fun validateServiceTasks(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.findFlowElementsOfType(ServiceTask::class.java).forEach { task ->
            checkClassImplementation(
                task.implementationType,
                task.implementation,
                "serviceTask:${task.id}",
                errors,
                process,
                task,
            )
            checkDelegateExpression(
                task.implementationType,
                task.implementation,
                "serviceTask:${task.id}",
                errors,
                process,
                task,
            )
            checkLoopCharacteristics(task.loopCharacteristics, "serviceTask:${task.id}", errors, process, task)
            task.executionListeners?.forEach { listener ->
                checkListener(listener, "executionListener@serviceTask:${task.id}", errors, process, task)
            }
        }
    }

    private fun validateScriptTasks(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.findFlowElementsOfType(ScriptTask::class.java).forEach { task ->
            checkScriptTaskLanguage(task, errors, process)
        }
    }

    private fun validateBusinessRuleTasks(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.findFlowElementsOfType(BusinessRuleTask::class.java).forEach { task ->
            // BusinessRuleTask uses a separate className field, not implementation
            task.className?.let { className ->
                if (!isClassAllowed(className)) {
                    addError(
                        errors,
                        ERROR_KEY,
                        process,
                        task,
                        "businessRuleTask:${task.id}: class='$className' not in whitelist (allowed prefixes: $ALLOWED_CLASS_PREFIXES)",
                    )
                }
            }
            task.executionListeners?.forEach { listener ->
                checkListener(listener, "executionListener@businessRuleTask:${task.id}", errors, process, task)
            }
        }
    }

    private fun validateUserTasks(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.findFlowElementsOfType(UserTask::class.java).forEach { task ->
            task.taskListeners?.forEach { listener ->
                checkListener(listener, "taskListener@userTask:${task.id}", errors, process, task)
            }
            task.executionListeners?.forEach { listener ->
                checkListener(listener, "executionListener@userTask:${task.id}", errors, process, task)
            }
            checkLoopCharacteristics(task.loopCharacteristics, "userTask:${task.id}", errors, process, task)
        }
    }

    private fun validateProcessLevelListeners(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.executionListeners?.forEach { listener ->
            checkListener(listener, "executionListener@process:${process.id}", errors, process, null)
        }
        process.eventListeners?.forEach { eventListener ->
            checkEventListener(eventListener, "eventListener@process:${process.id}", errors, process)
        }
    }

    // Any FlowElement not already covered above may still carry execution listeners
    private fun validateOtherFlowElementListeners(
        process: Process,
        errors: MutableList<ValidationError>,
    ) {
        process.findFlowElementsOfType(FlowElement::class.java).forEach { el ->
            if (el !is ServiceTask && el !is BusinessRuleTask && el !is UserTask) {
                el.executionListeners?.forEach { listener ->
                    checkListener(
                        listener,
                        "executionListener@${el.javaClass.simpleName}:${el.id}",
                        errors,
                        process,
                        el,
                    )
                }
            }
        }
    }

    private fun checkClassImplementation(
        implementationType: String?,
        implementation: String?,
        location: String,
        errors: MutableList<ValidationError>,
        process: Process,
        element: BaseElement?,
    ) {
        if (implementationType == ImplementationType.IMPLEMENTATION_TYPE_CLASS && implementation != null) {
            if (!isClassAllowed(implementation)) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    element,
                    "$location: class='$implementation' not in whitelist (allowed prefixes: $ALLOWED_CLASS_PREFIXES)",
                )
            }
        }
    }

    private fun checkDelegateExpression(
        implementationType: String?,
        implementation: String?,
        location: String,
        errors: MutableList<ValidationError>,
        process: Process,
        element: BaseElement?,
    ) {
        if (implementationType == ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION && implementation != null) {
            val violation = validateDelegateExpression(implementation)
            if (violation != null) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    element,
                    "$location: delegateExpression='$implementation' - $violation",
                )
            }
        }
    }

    private fun checkListener(
        listener: FlowableListener,
        location: String,
        errors: MutableList<ValidationError>,
        process: Process,
        element: BaseElement?,
    ) {
        checkClassImplementation(
            listener.implementationType,
            listener.implementation,
            location,
            errors,
            process,
            element,
        )
        checkDelegateExpression(
            listener.implementationType,
            listener.implementation,
            location,
            errors,
            process,
            element,
        )

        // customPropertiesResolverImplementation is a separate class field
        if (listener.customPropertiesResolverImplementationType == ImplementationType.IMPLEMENTATION_TYPE_CLASS &&
            listener.customPropertiesResolverImplementation != null
        ) {
            val cls = listener.customPropertiesResolverImplementation
            if (!isClassAllowed(cls)) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    element,
                    "$location: customPropertiesResolver class='$cls' not in whitelist",
                )
            }
        }
    }

    private fun checkEventListener(
        listener: EventListener,
        location: String,
        errors: MutableList<ValidationError>,
        process: Process,
    ) {
        if (listener.implementationType == ImplementationType.IMPLEMENTATION_TYPE_CLASS && listener.implementation != null) {
            if (!isClassAllowed(listener.implementation)) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    null as BaseElement?,
                    "$location: class='${listener.implementation}' not in whitelist",
                )
            }
        }
        if (listener.implementationType == ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION && listener.implementation != null) {
            val violation = validateDelegateExpression(listener.implementation)
            if (violation != null) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    null as BaseElement?,
                    "$location: delegateExpression='${listener.implementation}' - $violation",
                )
            }
        }
    }

    private fun checkLoopCharacteristics(
        loop: MultiInstanceLoopCharacteristics?,
        location: String,
        errors: MutableList<ValidationError>,
        process: Process,
        element: BaseElement?,
    ) {
        val handler: CollectionHandler = loop?.handler ?: return
        if (handler.implementationType == ImplementationType.IMPLEMENTATION_TYPE_CLASS && handler.implementation != null) {
            if (!isClassAllowed(handler.implementation)) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    element,
                    "$location: collectionHandler class='${handler.implementation}' not in whitelist",
                )
            }
        }
        if (handler.implementationType == ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION && handler.implementation != null) {
            val violation = validateDelegateExpression(handler.implementation)
            if (violation != null) {
                addError(
                    errors,
                    ERROR_KEY,
                    process,
                    element,
                    "$location: collectionHandler delegateExpression='${handler.implementation}' - $violation",
                )
            }
        }
    }

    private fun isClassAllowed(className: String): Boolean = ALLOWED_CLASS_PREFIXES.any { className.startsWith(it) }

    private fun checkScriptTaskLanguage(
        task: ScriptTask,
        errors: MutableList<ValidationError>,
        process: Process,
    ) {
        val scriptFormat = task.scriptFormat?.trim()?.lowercase()
        if (scriptFormat in ALLOWED_SCRIPT_FORMATS) return

        addError(
            errors,
            SCRIPT_LANGUAGE_ERROR_KEY,
            process,
            task,
            "scriptTask:${task.id}: scriptFormat='${task.scriptFormat ?: ""}' not allowed " +
                "(allowed: $ALLOWED_SCRIPT_FORMATS; no sandbox for other languages)",
        )
    }

    /**
     * Returns null if the expression is allowed, otherwise an error message.
     *
     * Parses the JUEL expression with Flowable's own parser, walks the AST, and
     * collects every identifier node (root variables). Each identifier must be in
     * [ALLOWED_DELEGATE_BEANS] - this covers `${kafkaRpc}` (one identifier),
     * `${kafkaRpc.task('x').outputs(map)}` (two: `kafkaRpc`, `map`), `${a + b}` (two:
     * `a`, `b`).
     *
     * Process variables must never appear as a root identifier here - a
     * delegateExpression must resolve to a bean (a JavaDelegate), not a variable.
     */
    private fun validateDelegateExpression(expression: String): String? {
        val tree =
            try {
                Builder(
                    Builder.Feature.METHOD_INVOCATIONS,
                    Builder.Feature.VARARGS,
                    Builder.Feature.NULL_PROPERTIES,
                ).build(expression)
            } catch (e: TreeBuilderException) {
                return "failed to parse expression (${e.message}) - " +
                    "if this is valid JUEL, rewrite it without map literals or static calls"
            }

        val identifierNames = mutableSetOf<String>()
        for (idNode in tree.identifierNodes) {
            identifierNames.add((idNode as IdentifierNode).name)
        }

        if (identifierNames.isEmpty()) {
            return "expression contains no bean references"
        }

        val notAllowed = identifierNames - ALLOWED_DELEGATE_BEANS
        if (notAllowed.isNotEmpty()) {
            return "identifier(s) $notAllowed not in whitelist (allowed: $ALLOWED_DELEGATE_BEANS)"
        }
        return null
    }

    companion object {
        const val ERROR_KEY = "briany-class-injection"
        const val SCRIPT_LANGUAGE_ERROR_KEY = "briany-unsafe-script-language"

        // Allowed FQCN prefixes for `class="..."` attributes: our own packages, plus
        // Flowable internals in case the BPMN model references its own standard delegates.
        val ALLOWED_CLASS_PREFIXES: List<String> =
            listOf(
                "ru.briany.",
                "org.flowable.",
            )

        /**
         * Spring beans referenceable via `delegateExpression="${beanName}"`. Deliberately
         * separate from `processEngineConfiguration.beans` and much narrower, since a
         * delegate expression gives direct access to the whole bean, not just to
         * whitelisted expression functions.
         */
        val ALLOWED_DELEGATE_BEANS: Set<String> =
            setOf(
                "kvDelegate",
            )

        val ALLOWED_SCRIPT_FORMATS: Set<String> =
            setOf(
                "groovy",
            )
    }
}
