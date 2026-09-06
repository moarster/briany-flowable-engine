package ru.briany.integration

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.engine.AppEngineConfiguration
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngineConfiguration
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.flowable.idm.api.IdmIdentityService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.zip.ZipInputStream
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class BaseControllerIT {
    companion object {
        const val TEST_USER = "testuser"

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
                withReuse(true)
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

    @BeforeEach
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
    }

    @AfterEach
    fun cleanUpFlowable() {
        clearFlowableCaches()
        truncateEngineTables()
    }

    // Cache clearing is required because TRUNCATE bypasses the engine's own delete paths
    // that would normally evict cache entries. Without this, stale cached process/app
    // definitions would point to rows that no longer exist.
    private fun clearFlowableCaches() {
        (processEngineConfiguration as? ProcessEngineConfigurationImpl)?.let { impl ->
            impl.processDefinitionCache?.clear()
            impl.processDefinitionInfoCache?.clear()
            impl.knowledgeBaseCache?.clear()
        }
        appEngineConfiguration?.appDefinitionCache?.clear()
    }

    // Preserves act_ge_property (schema version) and act_id_* (IDM users/groups,
    // including TEST_USER created once and reused across tests).
    private fun truncateEngineTables() {
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
