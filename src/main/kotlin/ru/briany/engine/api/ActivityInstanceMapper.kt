package ru.briany.engine.api

import org.flowable.engine.history.HistoricActivityInstance
import ru.briany.generated.model.ActivityInstance
import ru.briany.generated.model.ActivityInstanceState

object ActivityInstanceMapper {
    fun from(activity: HistoricActivityInstance): ActivityInstance =
        ActivityInstance(
            id = activity.id,
            activityId = activity.activityId,
            activityType = activity.activityType,
            state = mapState(activity),
            activityName = activity.activityName,
            executionId = activity.executionId,
            parentActivityInstanceId = null,
            taskId = activity.taskId,
            calledProcessInstanceId = activity.calledProcessInstanceId,
            startTime = activity.startTime?.toInstant(),
            endTime = activity.endTime?.toInstant(),
            durationInMillis = activity.durationInMillis,
        )

    // An activity still open has no end time; one closed by cancellation or an interrupting
    // boundary event carries a delete reason, everything else left normally.
    private fun mapState(activity: HistoricActivityInstance): ActivityInstanceState =
        when {
            activity.endTime == null -> ActivityInstanceState.ACTIVE
            activity.deleteReason != null -> ActivityInstanceState.TERMINATED
            else -> ActivityInstanceState.COMPLETED
        }
}
