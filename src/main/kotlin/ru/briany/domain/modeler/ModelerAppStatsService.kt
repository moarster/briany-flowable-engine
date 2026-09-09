package ru.briany.domain.modeler

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.briany.engine.api.ProcessInstanceStatsAggregator
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppPage
import ru.briany.generated.model.ModelerAppStats
import ru.briany.generated.model.ModelerFileType

/**
 * Enriches modeler apps with the opt-in `stats` projection. File composition is cheap
 * (metadata only); instance counts reuse the shared [ProcessInstanceStatsAggregator] and are
 * computed only for deployed apps. Kept separate from [ModelerAppService] so the base CRUD
 * path stays free of engine/runtime concerns - same split as ProcessDefinitionStatsService.
 *
 * The modeler is the acknowledged "does not fit the three contours" feature (it already
 * depends on workflow.application, contour 2); consuming the contour-1
 * [ProcessInstanceStatsAggregator] here is consistent with that and keeps the counting rules
 * in one place.
 *
 * Counting is by process definition key, so if two apps ever declared a BPMN process with the
 * same id they would share instance counts. In this single-tenant demo keys are unique and
 * this matches the UI contract, so key-based counting is correct here.
 */
@Service
@Transactional(readOnly = true)
class ModelerAppStatsService(
    private val modelerAppService: ModelerAppService,
    private val fileRepository: ModelerAppFileRepository,
    private val statsAggregator: ProcessInstanceStatsAggregator,
) {
    fun list(
        filter: ModelerStateFilter,
        pageable: Pageable,
        includeStats: Boolean,
    ): ModelerAppPage {
        val page = modelerAppService.list(filter, pageable)
        if (!includeStats || page.data.isEmpty()) return page
        val metaByApp = fileRepository.findMetaByAppIdIn(page.data.map { it.id }).groupBy { it.appId }
        return page.copy(
            data =
                page.data.map { ref ->
                    val meta = metaByApp[ref.id].orEmpty()
                    ref.copy(
                        stats =
                            buildStats(
                                types = meta.map { it.type },
                                bpmnKeys = meta.filter { it.type == ModelerFileType.BPMN }.map { it.fileKey },
                                deployedVersion = ref.deployedVersion,
                            ),
                    )
                },
        )
    }

    fun get(
        key: String,
        includeStats: Boolean,
    ): ModelerApp {
        val app = modelerAppService.get(key)
        if (!includeStats) return app
        return app.copy(
            stats =
                buildStats(
                    types = app.files?.map { it.type } ?: emptyList(),
                    bpmnKeys = app.files?.filter { it.type == ModelerFileType.BPMN }?.map { it.fileKey } ?: emptyList(),
                    deployedVersion = app.deployedVersion,
                ),
        )
    }

    private fun buildStats(
        types: List<ModelerFileType>,
        bpmnKeys: List<String>,
        deployedVersion: Int?,
    ): ModelerAppStats {
        val byType = types.groupingBy { it }.eachCount()
        return ModelerAppStats(
            processDefinitions = byType[ModelerFileType.BPMN] ?: 0,
            decisions = byType[ModelerFileType.DMN] ?: 0,
            forms = byType[ModelerFileType.BFORM] ?: 0,
            instances = if (deployedVersion == null) null else statsAggregator.forKeys(bpmnKeys),
        )
    }
}
