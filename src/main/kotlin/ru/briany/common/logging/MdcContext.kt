package ru.briany.common.logging

import org.flowable.engine.delegate.DelegateExecution
import org.slf4j.MDC

object MdcContext {
    const val PROCESS_INSTANCE_ID = "process_instance_id"
    const val EXECUTION_ID = "execution_id"
    const val ACTIVITY_ID = "activity_id"
    const val USER_ID = "user_id"
    const val TENANT_ID = "tenant_id"
    const val REQUEST_ID = "request_id"

    fun putFlowableContext(
        processInstanceId: String?,
        executionId: String?,
        activityId: String?,
        tenantId: String? = null,
    ) {
        processInstanceId?.let { MDC.put(PROCESS_INSTANCE_ID, it) }
        executionId?.let { MDC.put(EXECUTION_ID, it) }
        activityId?.let { MDC.put(ACTIVITY_ID, it) }
        tenantId?.takeIf { it.isNotBlank() }?.let { MDC.put(TENANT_ID, it) }
    }

    fun clearFlowableContext() {
        MDC.remove(PROCESS_INSTANCE_ID)
        MDC.remove(EXECUTION_ID)
        MDC.remove(ACTIVITY_ID)
    }

    inline fun <T> withFlowableContext(
        execution: DelegateExecution,
        block: () -> T,
    ): T {
        val previousContext = MDC.getCopyOfContextMap()
        try {
            putFlowableContext(
                processInstanceId = execution.processInstanceId,
                executionId = execution.id,
                activityId = execution.currentActivityId,
                tenantId = execution.tenantId,
            )
            return block()
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext)
            } else {
                MDC.clear()
            }
        }
    }

    inline fun <T> withContext(
        vararg pairs: Pair<String, String?>,
        block: () -> T,
    ): T {
        val previousContext = MDC.getCopyOfContextMap()
        try {
            pairs.forEach { (key, value) -> value?.let { MDC.put(key, it) } }
            return block()
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext)
            } else {
                MDC.clear()
            }
        }
    }
}
