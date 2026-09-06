package ru.briany

import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class Briany {
    private val log = org.slf4j.LoggerFactory.getLogger(Briany::class.java)

    // Log engine configuration on startup
    @org.springframework.context.event.EventListener
    fun onApplicationEvent(event: org.springframework.boot.context.event.ApplicationStartedEvent) {
        val ctx = event.applicationContext

        val eventRepositoryService =
            runCatching {
                ctx.getBean<org.flowable.eventregistry.api.EventRepositoryService>()
            }.getOrNull()

        if (eventRepositoryService != null) {
            log.info("EVENT REGISTRY CONFIGURATION:")
            log.info("Event definitions: {}", eventRepositoryService.createEventDefinitionQuery().list().map { it.key })
            log.info("Event channels: {}", eventRepositoryService.createChannelDefinitionQuery().list().map { it.key })
        } else {
            log.info("EVENT REGISTRY: not available")
        }

        val deploymentService = ctx.getBean<org.flowable.engine.RepositoryService>()
        log.info("PROCESS ENGINE CONFIGURATION:")
        log.info("Process definitions: {}", deploymentService.createProcessDefinitionQuery().list().map { it.key })
    }
}

fun main(args: Array<String>) {
    runApplication<Briany>(*args)
}
