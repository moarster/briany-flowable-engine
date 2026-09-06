package ru.briany.integration.workflow.application

import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import ru.briany.integration.BaseOrderedControllerIT

class ApplicationControllerIT : BaseOrderedControllerIT() {
    companion object {
        const val ONLY_NATIVE_APP_ZIP = "deployments/only-native-app.zip"
        const val APP_WITH_ALL_ZIP = "deployments/app-with-all.zip"
        const val APP_WITHOUT_APP = "deployments/app-without-app.zip"
        const val APP_WITH_TWO_APPS = "deployments/app-with-two-apps.zip"

        const val ONLY_NATIVE_APP_KEY = "native-samples"
        const val APP_WITH_ALL_KEY = "full-samples"
    }

    // Phase 0: empty state

    @Test
    @Order(1)
    fun `listApplications returns empty page when no apps deployed`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasPrevious").value(false))
    }

    @Test
    @Order(2)
    fun `listApplicationsVersions returns empty page when no apps deployed`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
    }

    // Phase 1: only-native-app v1 (key=native-samples)

    @Test
    @Order(10)
    fun `deploy only-native-app returns 200 with app metadata`() {
        deployAppViaApi(ONLY_NATIVE_APP_ZIP)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Native app sample"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString)
            .andExpect(MockMvcResultMatchers.jsonPath("$.deploymentId").isString)
    }

    @Test
    @Order(11)
    fun `listApplications reflects v1 deploy`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].version").value(1))
    }

    @Test
    @Order(12)
    fun `getApplication by key returns v1`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
    }

    @Test
    @Order(13)
    fun `getApplicationVersion returns v1`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions/1")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
    }

    @Test
    @Order(14)
    fun `listApplicationVersions returns one entry after first deploy`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].version").value(1))
    }

    // Phase 1.5: app-with-all (key=full-samples, independent key — starts at v1)

    @Test
    @Order(15)
    fun `deploy app-with-bform returns 200`() {
        deployAppViaApi(APP_WITH_ALL_ZIP)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(APP_WITH_ALL_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
    }

    @Test
    @Order(16)
    fun `listApplicationsVersions returns one entry per deployed version`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
    }

    // Phase 2: only-native-app v2 (key=native-samples, version=2)

    @Test
    @Order(20)
    fun `deploy native-app v2 returns 200`() {
        deployAppViaApi(ONLY_NATIVE_APP_ZIP)
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
    }

    @Test
    @Order(21)
    fun `listApplications returns latest per key after multiple deploys`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
    }

    @Test
    @Order(22)
    fun `listApplicationVersions for native-app returns two versions`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
    }

    @Test
    @Order(23)
    fun `listApplicationVersions pagination page 0 has next`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions")
                    .param("page", "0")
                    .param("size", "1")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasPrevious").value(false))
    }

    @Test
    @Order(24)
    fun `listApplicationVersions pagination page 1 has previous`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions")
                    .param("page", "1")
                    .param("size", "1")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasPrevious").value(true))
    }

    @Test
    @Order(25)
    fun `getApplication returns latest version by key`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
    }

    @Test
    @Order(26)
    fun `getApplicationVersion returns historical v1`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions/1")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(1))
    }

    @Test
    @Order(27)
    fun `getApplicationVersion returns current v2`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions/2")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(ONLY_NATIVE_APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(2))
    }

    @Test
    @Order(28)
    fun `listApplicationsVersions returns all versions across all apps`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(3))
    }

    @Test
    @Order(29)
    fun `listApplicationsVersions supports pagination`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/versions")
                    .param("page", "0")
                    .param("size", "2")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(3))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasNext").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.hasPrevious").value(false))
    }

    // Phase 3: state-independent tests

    @Test
    @Order(30)
    fun `getApplication by unknown key returns 404`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/nonexistent")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    @Order(40)
    fun `getApplicationVersion with unknown version returns 404`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions/9999")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    @Order(50)
    fun `unauthenticated request returns 401`() {
        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/v1/applications"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    @Order(60)
    fun `deploy zip without app file returns 400`() {
        deployAppViaApi(APP_WITHOUT_APP)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(60)
    fun `deploy zip with two apps file returns 400`() {
        deployAppViaApi(APP_WITH_TWO_APPS)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(70)
    fun `deploy without file part returns 400`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/v1/applications")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(80)
    fun `deploy non-zip file returns 400`() {
        val file =
            MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "not a zip".toByteArray(),
            )
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/v1/applications")
                    .file(file)
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(90)
    fun `deploy empty zip returns 400`() {
        val emptyZipBytes =
            run {
                val out = java.io.ByteArrayOutputStream()
                java.util.zip
                    .ZipOutputStream(out)
                    .close()
                out.toByteArray()
            }
        val file =
            MockMultipartFile(
                "file",
                "empty.zip",
                "application/zip",
                emptyZipBytes,
            )
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/v1/applications")
                    .file(file)
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(100)
    fun `deploy without authentication returns 401`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/v1/applications"),
            ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    @Order(110)
    fun `getApplicationVersion with non-numeric version returns 400`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/$ONLY_NATIVE_APP_KEY/versions/abc")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(120)
    fun `listApplicationVersions for unknown key returns empty page`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/applications/unknown-key/versions")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
    }

    private fun deployAppViaApi(classpathResource: String): ResultActions {
        val bytes =
            javaClass.classLoader.getResourceAsStream(classpathResource)?.readAllBytes()
                ?: throw IllegalArgumentException("Resource not found: $classpathResource")
        val file =
            MockMultipartFile(
                "file",
                classpathResource.substringAfterLast('/'),
                "application/zip",
                bytes,
            )
        return mockMvc.perform(
            MockMvcRequestBuilders
                .multipart("/api/v1/applications")
                .file(file)
                .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
        )
    }
}
