package ru.briany.common.scheduling

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
class ScheduledTaskErrorHandler : SchedulingConfigurer {
    private val log = LoggerFactory.getLogger(ScheduledTaskErrorHandler::class.java)

    private val poolSize = 2
    private val threadNamePrefix = "bpm-scheduled-"

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize
        scheduler.setThreadNamePrefix(threadNamePrefix)
        scheduler.setErrorHandler { throwable ->
            log.warn("Scheduled task failed", throwable)
        }
        scheduler.initialize()
        taskRegistrar.setTaskScheduler(scheduler)
    }
}
