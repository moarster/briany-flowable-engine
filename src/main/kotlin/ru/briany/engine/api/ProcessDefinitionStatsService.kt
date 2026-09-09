package ru.briany.engine.api

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage

/**
 * Enriches process definitions with live instance counts.
 *
 * The base definition data comes from [ProcessDefinitionFacade]; the counts are the
 * complex, UI-facing part (a work list shows "12 running" next to a process), so they
 * live here rather than in the facade. The counting rules themselves are delegated to the
 * shared [ProcessInstanceStatsAggregator]. Stats are computed only when the caller asks for
 * them, keeping the common listing cheap.
 */
@Service
class ProcessDefinitionStatsService(
    private val processDefinitionFacade: ProcessDefinitionFacade,
    private val statsAggregator: ProcessInstanceStatsAggregator,
) {
    fun getProcess(
        key: String,
        includeStats: Boolean,
    ): ProcessDefinition {
        val definition = processDefinitionFacade.getProcessDefinitionByKey(key)
        return if (includeStats) definition.copy(stats = statsAggregator.forKey(key)) else definition
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
        return page.copy(data = page.data.map { it.copy(stats = statsAggregator.forKey(it.key)) })
    }
}
