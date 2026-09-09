package ru.briany.engine.config.validator

import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.flowable.validation.ProcessValidator
import org.flowable.validation.ProcessValidatorImpl
import org.flowable.validation.validator.ValidatorSetFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Assembles a [ProcessValidatorImpl] from the standard Flowable validator set plus our
 * custom rules: [ShellTaskValidator] rejects `<serviceTask type="shell"/>` at deploy
 * time, [SafeBpmnDeploymentValidator] blocks deployment-time class injection and
 * delegate expressions on disallowed beans.
 *
 * The validator is a Spring bean so both the engine (via [bpmnValidatorConfigurer]) and the
 * modeler pre-deploy check consume the exact same rules and cannot drift.
 */
@Configuration
class BpmnValidatorConfig {
    private val log = LoggerFactory.getLogger(BpmnValidatorConfig::class.java)

    @Bean
    fun brianyProcessValidator(): ProcessValidator {
        val validatorSet = ValidatorSetFactory().createFlowableExecutableProcessValidatorSet()
        validatorSet.addValidator(ShellTaskValidator())
        validatorSet.addValidator(SafeBpmnDeploymentValidator())

        val validator = ProcessValidatorImpl()
        validator.addValidatorSet(validatorSet)
        log.info("ProcessValidator configured with Shell + class-injection deployment rules")
        return validator
    }

    @Bean
    fun bpmnValidatorConfigurer(
        brianyProcessValidator: ProcessValidator,
    ): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> =
        EngineConfigurationConfigurer { cfg ->
            cfg.processValidator = brianyProcessValidator
            log.info("ProcessValidator installed on the engine configuration")
        }
}
