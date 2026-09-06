package ru.briany.integration

import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
abstract class BaseServiceIT {
    companion object {
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
                withReuse(true)
                start()
            }

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url", postgres::getJdbcUrl)
            reg.add("spring.datasource.username", postgres::getUsername)
            reg.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired lateinit var runtimeService: RuntimeService

    @Autowired lateinit var historyService: HistoryService

    @Autowired lateinit var taskService: TaskService

    @AfterEach
    fun cleanupFlowable() {
        runtimeService
            .createProcessInstanceQuery()
            .list()
            .forEach { runtimeService.deleteProcessInstance(it.id, "cleanup") }
        repositoryService
            .createDeploymentQuery()
            .list()
            .forEach { repositoryService.deleteDeployment(it.id, true) }
    }
}
