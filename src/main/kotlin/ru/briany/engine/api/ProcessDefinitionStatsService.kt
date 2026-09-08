package ru.briany.engine.api

import org.flowable.engine.HistoryService
import org.flowable.engine.RuntimeService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage
import ru.briany.generated.model.ProcessInstanceStats

/**
 * Enriches process definitions with live instance counts.
 *
 * The base definition data comes from [ProcessDefinitionFacade]; the counts are the
 * complex, UI-facing part (a work list shows "12 running" next to a process), so they
 * live here rather than in the facade. Stats are computed only when the caller asks for
 * them, keeping the common listing cheap.
 */
@Service
class ProcessDefinitionStatsService(
    private val processDefinitionFacade: ProcessDefinitionFacade,
    private val runtimeService: RuntimeService,
    private val historyService: HistoryService,
) {
    fun getProcess(
        key: String,
        includeStats: Boolean,
    ): ProcessDefinition {
        val definition = processDefinitionFacade.getProcessDefinitionByKey(key)
        return if (includeStats) definition.copy(stats = statsFor(key)) else definition
    }

    fun listProcesses(
        includeStats: Boolean,
        pageable: Pageable,
    ): ProcessDefinitionPage {
        val page =
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.Latest,
                appId = null,
                key = null,
                pageable = pageable,
            )
        if (!includeStats) return page
        return page.copy(data = page.data.map { it.copy(stats = statsFor(it.key)) })
    }

    private fun statsFor(key: String): ProcessInstanceStats {
        val historic = { historyService.createHistoricProcessInstanceQuery().processDefinitionKey(key) }
        val total = historic().count()
        val completed = historic().finished().count()
        val unfinished = historic().unfinished().count()
        val suspended = runtimeService.createProcessInstanceQuery().processDefinitionKey(key).suspended().count()
        val running = (unfinished - suspended).coerceAtLeast(0)
        return ProcessInstanceStats(
            running = running.toInt(),
            completed = completed.toInt(),
            suspended = suspended.toInt(),
            total = total.toInt(),
        )
    }
}
