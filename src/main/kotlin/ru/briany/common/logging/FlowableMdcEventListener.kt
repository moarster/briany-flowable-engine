package ru.briany.common.logging

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType
import org.flowable.common.engine.api.delegate.event.FlowableEvent
import org.flowable.common.engine.api.delegate.event.FlowableEventListener
import org.flowable.engine.delegate.event.FlowableActivityEvent
import org.flowable.engine.delegate.event.impl.FlowableProcessStartedEventImpl
import org.springframework.stereotype.Component

@Component
class FlowableMdcEventListener : FlowableEventListener {
    override fun onEvent(event: FlowableEvent) {
        when (event.type) {
            FlowableEngineEventType.PROCESS_STARTED -> {
                handleProcessStarted(event)
            }

            FlowableEngineEventType.ACTIVITY_STARTED,
            FlowableEngineEventType.ACTIVITY_COMPLETED,
            -> {
                handleActivityEvent(event)
            }

            else -> {}
        }
    }

    private fun handleProcessStarted(event: FlowableEvent) {
        val processEvent = event as? FlowableProcessStartedEventImpl ?: return
        MdcContext.putFlowableContext(
            processInstanceId = processEvent.processInstanceId,
            executionId = processEvent.executionId,
            activityId = null,
            tenantId = null,
        )
    }

    private fun handleActivityEvent(event: FlowableEvent) {
        val activityEvent = event as? FlowableActivityEvent ?: return

        MdcContext.putFlowableContext(
            processInstanceId = activityEvent.processInstanceId,
            executionId = activityEvent.executionId,
            activityId = activityEvent.activityId,
            tenantId = null,
        )
    }

    override fun isFailOnException(): Boolean = false

    override fun isFireOnTransactionLifecycleEvent(): Boolean = false

    override fun getOnTransaction(): String? = null
}
