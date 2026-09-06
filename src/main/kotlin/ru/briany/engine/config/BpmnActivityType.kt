package ru.briany.engine.config

/**
 * Corresponds to all non-abstract descendants of [org.flowable.bpmn.model.Activity]
 * @param type — BPMN XML local name.
 */
enum class BpmnActivityType(
    val type: String,
) {
    USER_TASK("userTask"),
    SERVICE_TASK("serviceTask"),
    HTTP_SERVICE_TASK("httpServiceTask"),
    CASE_SERVICE_TASK("caseServiceTask"),
    SEND_EVENT_SERVICE_TASK("sendEventServiceTask"),
    EXTERNAL_WORKER_SERVICE_TASK("externalWorkerServiceTask"),
    FORM_AWARE_SERVICE_TASK("formAwareServiceTask"),
    SCRIPT_TASK("scriptTask"),
    BUSINESS_RULE_TASK("businessRuleTask"),
    MANUAL_TASK("manualTask"),
    RECEIVE_TASK("receiveTask"),
    SEND_TASK("sendTask"),
    SUB_PROCESS("subProcess"),
    TRANSACTION("transaction"),
    EVENT_SUB_PROCESS("eventSubProcess"),
    ADHOC_SUB_PROCESS("adhocSubProcess"),
    CALL_ACTIVITY("callActivity"),
}
