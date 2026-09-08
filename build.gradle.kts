import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.detekt)
    alias(libs.plugins.version)
}

group = "ru.briany"
version = "0.0.1-SNAPSHOT"

// ─────────────────────────────────────────────────────────────────────────────
// region: Toolchain & compiler
// ─────────────────────────────────────────────────────────────────────────────

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmTarget = JvmTarget.fromTarget("25")
        languageVersion = KotlinVersion.fromVersion("2.3")
    }
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Repositories
// ─────────────────────────────────────────────────────────────────────────────

repositories {
    mavenCentral()
    maven { url = uri("https://maven.alfresco.com/nexus/content/repositories/public") }
    maven { url = uri("https://packages.confluent.io/maven/") }
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Dependencies
// ─────────────────────────────────────────────────────────────────────────────

dependencies {
    // Development only
    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly(libs.spring.boot.devtools)

    // BOMs
    implementation(platform(libs.spring.boot.bom))

    // Spring Boot
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2.resource.server)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.spring.boot.validation)

    // Database
    implementation(libs.postgresql)
    implementation(libs.liquibase)

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)

    // Flowable
    implementation(libs.flowable.starter)

    // Observability
    // implementation(libs.bundles.observability)

    // Scripting / Expressions
    implementation(libs.camunda.feel)

    // Logging
    implementation(libs.logstash.encoder)

    // Test
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.testing)
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Source sets
// ─────────────────────────────────────────────────────────────────────────────

sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        resources.srcDir(layout.buildDirectory.dir("generated/bpmn-descriptors"))
    }
    test {
        resources.srcDir(layout.buildDirectory.dir("test-archives"))
        resources.srcDir(layout.buildDirectory.dir("generated/test-bpmn"))
    }
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Spring Boot
// ─────────────────────────────────────────────────────────────────────────────

springBoot {
    mainClass.set("ru.briany.BrianyKt")
}

tasks.bootJar {
    mainClass.set("ru.briany.BrianyKt")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.jar { enabled = false }

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: OpenAPI code generation
// ─────────────────────────────────────────────────────────────────────────────

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/contract/rest/openapi-v1.yaml")
    outputDir.set(
        layout.buildDirectory
            .dir("generated/openapi")
            .get()
            .asFile.absolutePath,
    )
    apiPackage.set("ru.briany.generated.api")
    modelPackage.set("ru.briany.generated.model")
    invokerPackage.set("ru.briany.generated.invoker")
//    globalProperties.set(
//        mapOf(
//            "apis" to "Application,ProcessDefinition,ProcessInstance,Form",
//            "models" to "",
//        ),
//    )
    typeMappings.set(
        mapOf("DateTime" to "Instant", "BpmnElementPropertyValue" to "Any"),
    )
    importMappings.set(
        mapOf("Instant" to "java.time.Instant", "Any" to "kotlin.Any"),
    )
    schemaMappings.set(
        mapOf(
            "BooleanOrExpression" to "kotlin.Any",
            "FormComponentDefaultValue" to "kotlin.Any",
            "BpmnPropertyValue" to "kotlin.Any",
        ),
    )
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot4" to "true",
            "useBeanValidation" to "true",
            "useTags" to "true",
            "enumPropertyNaming" to "UPPERCASE",
            "documentationProvider" to "none",
        ),
    )
}

tasks.openApiGenerate {
    doFirst {
        val spec = file("$rootDir/contract/rest/openapi-v1.yaml")
        if (!spec.exists()) {
            throw GradleException("OpenAPI contract not found at contract/rest/openapi-v1.yaml")
        }
    }
}

tasks.compileKotlin {
    dependsOn(tasks.openApiGenerate)
}

// ─────────────────────────────────────────────────────────────────────────────
// region: Code style (ktlint)
// ─────────────────────────────────────────────────────────────────────────────

ktlint {
    version.set("1.8.0")
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
    filter {
        exclude { element ->
            val path = element.file.path
            path.contains("/generated/") || path.contains("\\generated\\")
        }
    }
}

tasks.runKtlintFormatOverMainSourceSet {
    dependsOn(tasks.openApiGenerate)
}

tasks.runKtlintCheckOverMainSourceSet {
    dependsOn(tasks.openApiGenerate)
}

// The ktlint plugin wires its *Check tasks directly into check; detach them
// since linting is its own CI stage, not part of every build.
afterEvaluate {
    tasks.check {
        setDependsOn(
            dependsOn.filterNot { dep ->
                dep is TaskProvider<*> && dep.name.contains("ktlint", ignoreCase = true)
            },
        )
    }
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Static analysis (detekt)
// ─────────────────────────────────────────────────────────────────────────────

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    source.setFrom(
        "src/main/kotlin",
        "src/test/kotlin",
    )
}

// Detach from check, same as ktlint - it's its own CI stage
afterEvaluate {
    tasks.check {
        setDependsOn(
            dependsOn.filterNot { dep ->
                dep is TaskProvider<*> && (
                    dep.name.contains("ktlint", ignoreCase = true) ||
                        dep.name.contains("detekt", ignoreCase = true)
                )
            },
        )
    }
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Test fixtures (ZIP archives from src/test/resources/deployments/*)
// ─────────────────────────────────────────────────────────────────────────────

val deploymentsDir = file("src/test/resources/deployments")
val zipDeploymentTasks =
    deploymentsDir
        .listFiles { f -> f.isDirectory }
        .orEmpty()
        .map { dir ->
            tasks.register<Zip>("zipDeployment_${dir.name}") {
                group = "test fixtures"
                description = "Pack ${dir.name}/ into a ZIP for integration tests"
                from(dir)
                archiveFileName.set("${dir.name}.zip")
                destinationDirectory.set(layout.buildDirectory.dir("test-archives/deployments"))
                isReproducibleFileOrder = true
                isPreserveFileTimestamps = false
            }
        }

val zipAllDeployments =
    tasks.register("zipAllDeployments") {
        group = "test fixtures"
        description = "Pack all deployment directories into ZIP archives"
        dependsOn(zipDeploymentTasks)
    }

tasks.processResources {
    from("$rootDir/api/openapi-v1.yaml") {
        into("static")
    }
}

tasks.processTestResources {
    dependsOn(zipAllDeployments)
}

// endregion

// ─────────────────────────────────────────────────────────────────────────────
// region: Test configuration
//
// Two test categories in the single src/test source set:
//   - unit tests        — *Test.kt  — ./gradlew test
//   - integration tests — *IT.kt    — ./gradlew integrationTest
//
// See docs/integration-testing-guide.md for the full rationale.
// ─────────────────────────────────────────────────────────────────────────────

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    dependsOn(zipAllDeployments)

    testLogging {
        events("passed", "failed", "skipped")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    reports {
        junitXml.required.set(true)
        junitXml.isOutputPerTestCase = true
        html.required.set(true)
    }

    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun beforeTest(test: TestDescriptor) = Unit

            override fun afterTest(
                test: TestDescriptor,
                result: TestResult,
            ) {
                val ms = result.endTime - result.startTime
                logger.lifecycle("  \u23F1  {}.{} — {} ms", test.className, test.name, ms)
            }

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) {
                if (suite.parent == null) {
                    val totalMs = result.endTime - result.startTime
                    logger.lifecycle(
                        "TOTAL: {} tests in {} ms ({} passed, {} failed, {} skipped)",
                        result.testCount,
                        totalMs,
                        result.successfulTestCount,
                        result.failedTestCount,
                        result.skippedTestCount,
                    )
                }
            }
        },
    )
}

tasks.test {
    description = "Run unit tests only (excludes *IT classes)"
    exclude("**/*IT.class")

    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Run integration tests (*IT classes only)"
        include("**/*IT.class")
        shouldRunAfter(tasks.test)
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath

        maxParallelForks = 1
        forkEvery = 0L
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    }

// endregion
