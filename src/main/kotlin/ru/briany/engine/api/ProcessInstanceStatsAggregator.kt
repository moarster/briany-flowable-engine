package ru.briany.engine.api

import org.flowable.engine.HistoryService
import org.flowable.engine.RuntimeService
import org.springframework.stereotype.Service
import ru.briany.generated.model.ProcessInstanceStats

/**
 * Single source of runtime/history instance counts, keyed by process definition key.
 *
 * Contour-1 anti-corruption boundary over Flowable's Runtime/History services. Both
 * [ProcessDefinitionStatsService] (one key) and the modeler stats path (sum over an app's
 * BPMN process keys) consume it, so the counting rules live in exactly one place.
 */
@Service
class ProcessInstanceStatsAggregator(
    private val runtimeService: RuntimeService,
    private val historyService: HistoryService,
) {
    /** Counts for a single process definition key. */
    fun forKey(key: String): ProcessInstanceStats {
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

    /**
     * Counts summed over a set of process definition keys. One modeler app can bundle several
     * BPMN processes; keys within an app are unique and an instance has exactly one definition
     * key, so per-key counts are additive.
     */
    fun forKeys(keys: Collection<String>): ProcessInstanceStats =
        keys.distinct().map(::forKey).fold(EMPTY, ::sum)

    private fun sum(
        a: ProcessInstanceStats,
        b: ProcessInstanceStats,
    ): ProcessInstanceStats =
        ProcessInstanceStats(
            running = a.running + b.running,
            completed = a.completed + b.completed,
            suspended = a.suspended + b.suspended,
            total = a.total + b.total,
        )

    companion object {
        val EMPTY = ProcessInstanceStats(running = 0, completed = 0, suspended = 0, total = 0)
    }
}
