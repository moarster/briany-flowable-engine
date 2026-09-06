package ru.briany.engine.health

import org.flowable.engine.ManagementService
import org.flowable.engine.ProcessEngine
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component("flowable")
class FlowableHealthIndicator(
    private val processEngine: ProcessEngine,
    private val managementService: ManagementService,
) : HealthIndicator {
    override fun health(): Health {
        val deadLetterCount =
            try {
                managementService.createDeadLetterJobQuery().count()
            } catch (_: Exception) {
                -1L
            }

        val builder =
            Health
                .up()
                .withDetail("engineName", processEngine.name)
                .withDetail("deadLetterJobs", deadLetterCount)

        if (deadLetterCount > 0) {
            builder.withDetail("warning", "dead letter jobs present")
        }

        return builder.build()
    }
}
