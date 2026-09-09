package ru.briany.integration.security

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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(
    properties = [
        "briany.security.auth.strategy=jwks",
        "briany.security.auth.jwks.audiences=bpm-test-client",
    ],
)
abstract class BaseKeycloakIT {
    companion object {
        const val TEST_USER = "test-user"
        const val TEST_PASSWORD = "test-pass"
        const val REALM = "bpm-test"
        const val CLIENT_ID = "bpm-test-client"

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
                withReuse(true)
            }

        @JvmStatic
        val keycloak: GenericContainer<*> =
            GenericContainer("quay.io/keycloak/keycloak:26.5.0")
                .withCommand("start-dev", "--import-realm")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withExposedPorts(8080)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/bpm-test-realm.json"),
                    "/opt/keycloak/data/import/bpm-test-realm.json",
                ).waitingFor(
                    Wait
                        .forHttp("/realms/$REALM/.well-known/openid-configuration")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)),
                ).withReuse(true)
                .apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            registry.add("briany.security.auth.strategy") { "jwks" }
            registry.add("briany.security.auth.jwks.issuer-uri") { keycloakIssuerUri() }
            registry.add("briany.security.auth.jwks.jwk-set-uri") { keycloakJwkSetUri() }
        }

        private fun keycloakBaseUrl(): String = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"

        private fun keycloakIssuerUri(): String = "${keycloakBaseUrl()}/realms/$REALM"

        private fun keycloakJwkSetUri(): String = "${keycloakIssuerUri()}/protocol/openid-connect/certs"

        private val httpClient: HttpClient = HttpClient.newHttpClient()

        private val jsonMapper: ObjectMapper = ObjectMapper()
    }

    lateinit var mockMvc: MockMvc

    @Autowired lateinit var webApplicationContext: WebApplicationContext

    @Autowired lateinit var repositoryService: RepositoryService

    @Autowired lateinit var runtimeService: RuntimeService

    @Autowired lateinit var historyService: HistoryService

    @Autowired lateinit var appRepositoryService: AppRepositoryService

    @Autowired lateinit var idmIdentityService: IdmIdentityService

    @Autowired lateinit var dataSource: DataSource

    @Autowired lateinit var processEngineConfiguration: ProcessEngineConfiguration

    @Autowired(required = false)
    var appEngineConfiguration: AppEngineConfiguration? = null

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @AfterEach
    fun cleanUpFlowable() {
        clearFlowableCaches()
        truncateEngineTables()
    }

    protected fun obtainAccessToken(
        username: String = TEST_USER,
        password: String = TEST_PASSWORD,
    ): String {
        val form =
            "grant_type=password" +
                "&client_id=$CLIENT_ID" +
                "&username=$username" +
                "&password=$password"
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${keycloakIssuerUri()}/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        val response = httpClient.send(request, BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Keycloak token endpoint returned ${response.statusCode()}: ${response.body()}"
        }
        return jsonMapper.readTree(response.body()).get("access_token").asString()
    }

    private fun clearFlowableCaches() {
        (processEngineConfiguration as? ProcessEngineConfigurationImpl)?.let { impl ->
            impl.processDefinitionCache?.clear()
            impl.processDefinitionInfoCache?.clear()
            impl.knowledgeBaseCache?.clear()
        }
        appEngineConfiguration?.appDefinitionCache?.clear()
    }

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
}
