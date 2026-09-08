package ru.briany.engine.api

import org.flowable.variable.api.history.HistoricVariableInstance
import ru.briany.generated.model.Variable
import ru.briany.generated.model.VariableScope

object VariableMapper {
    fun from(variable: HistoricVariableInstance): Variable =
        Variable(
            name = variable.variableName,
            scope = scopeOf(variable),
            type = variable.variableTypeName,
            value = variable.value,
            executionId = variable.executionId,
            taskId = variable.taskId,
            createTime = variable.createTime?.toInstant(),
            lastUpdatedTime = variable.lastUpdatedTime?.toInstant(),
        )

    // Global is held on the process instance itself; a variable bound to a task or to a
    // child execution is local and visible only within it.
    private fun scopeOf(variable: HistoricVariableInstance): VariableScope {
        val local = variable.taskId != null ||
            (variable.executionId != null && variable.executionId != variable.processInstanceId)
        return if (local) VariableScope.LOCAL else VariableScope.GLOBAL
    }
}
