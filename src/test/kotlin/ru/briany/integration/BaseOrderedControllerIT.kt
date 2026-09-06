package ru.briany.integration

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.engine.AppEngineConfiguration
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngineConfiguration
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.flowable.idm.api.IdmIdentityService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.zip.ZipInputStream
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class BaseOrderedControllerIT {
    companion object {
        const val TEST_USER = "testuser"

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
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var historyService: HistoryService

    @Autowired
    lateinit var appRepositoryService: AppRepositoryService

    @Autowired
    lateinit var idmIdentityService: IdmIdentityService

    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var processEngineConfiguration: ProcessEngineConfiguration

    @Autowired(required = false)
    var appEngineConfiguration: AppEngineConfiguration? = null

    @BeforeAll
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        if (idmIdentityService.createUserQuery().userId(TEST_USER).count() == 0L) {
            val user = idmIdentityService.newUser(TEST_USER)
            user.firstName = TEST_USER
            user.lastName = ""
            user.displayName = TEST_USER
            idmIdentityService.saveUser(user)
        }

        truncateEngineTables()
    }

    @AfterAll
    fun tearDown() {
        clearFlowableCaches()
        truncateEngineTables()
    }

    protected fun clearFlowableCaches() {
        (processEngineConfiguration as? ProcessEngineConfigurationImpl)?.let { impl ->
            impl.processDefinitionCache?.clear()
            impl.processDefinitionInfoCache?.clear()
            impl.knowledgeBaseCache?.clear()
        }
        appEngineConfiguration?.appDefinitionCache?.clear()
    }

    protected fun truncateEngineTables() {
        val tables = mutableListOf<String>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt
                    .executeQuery(
                        """
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = current_schema()
                          AND (tablename LIKE 'act_%' OR tablename LIKE 'flw_%')
                          AND tablename <> 'act_ge_property'
                          AND tablename NOT LIKE 'act_id_%'
                        """.trimIndent(),
                    ).use { rs ->
                        while (rs.next()) tables.add(rs.getString(1))
                    }
            }
            if (tables.isEmpty()) return
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE",
                )
            }
        }
        clearFlowableCaches()
    }

    protected fun testUserAuth(): RequestPostProcessor = authAs(TEST_USER)

    protected fun authAs(userId: String): RequestPostProcessor =
        authentication(
            UsernamePasswordAuthenticationToken(
                userId,
                null,
                listOf(SimpleGrantedAuthority("access-task")),
            ),
        )

    protected fun deleteAllProcessInstances() {
        runtimeService.createProcessInstanceQuery().list().forEach {
            runtimeService.deleteProcessInstance(it.id, "test cleanup")
        }
        historyService.createHistoricProcessInstanceQuery().list().forEach {
            historyService.deleteHistoricProcessInstance(it.id)
        }
    }

    protected fun deployAppZip(classpathResource: String): String {
        val stream =
            javaClass.classLoader.getResourceAsStream(classpathResource)
                ?: throw IllegalArgumentException("Resource not found: $classpathResource")
        val deployment =
            appRepositoryService
                .createDeployment()
                .addZipInputStream(ZipInputStream(stream))
                .deploy()
        return deployment.id
    }

    protected fun deployProcess(classpathResource: String): String {
        val deployment =
            repositoryService
                .createDeployment()
                .addClasspathResource(classpathResource)
                .deploy()
        return deployment.id
    }
}
