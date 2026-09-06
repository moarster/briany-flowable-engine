package ru.briany.engine.config.validator

import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.flowable.validation.ProcessValidatorImpl
import org.flowable.validation.validator.ValidatorSetFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Assembles a [ProcessValidatorImpl] from the standard Flowable validator set plus our
 * custom rules: [ShellTaskValidator] rejects `<serviceTask type="shell"/>` at deploy
 * time, [SafeBpmnDeploymentValidator] blocks deployment-time class injection and
 * delegate expressions on disallowed beans. Installed before `ProcessEngine` is built.
 */
@Configuration
class BpmnValidatorConfig {
    private val log = LoggerFactory.getLogger(BpmnValidatorConfig::class.java)

    @Bean
    fun bpmnValidatorConfigurer(): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> =
        EngineConfigurationConfigurer { cfg ->
            val validatorSet = ValidatorSetFactory().createFlowableExecutableProcessValidatorSet()
            validatorSet.addValidator(ShellTaskValidator())
            validatorSet.addValidator(SafeBpmnDeploymentValidator())

            val validator = ProcessValidatorImpl()
            validator.addValidatorSet(validatorSet)
            cfg.processValidator = validator

            log.info("ProcessValidator configured with Shell + class-injection deployment rules")
        }
}
