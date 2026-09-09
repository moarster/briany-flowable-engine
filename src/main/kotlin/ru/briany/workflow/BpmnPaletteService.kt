package ru.briany.workflow

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import ru.briany.generated.model.BpmnPalette
import ru.briany.generated.model.BpmnPaletteElementsInner
import tools.jackson.databind.ObjectMapper
import java.io.IOException

/**
 * Loads the custom BPMN palette element descriptors published on the classpath by the build-time
 * descriptor pipeline. With no descriptors present the palette is empty, which is a valid response
 * meaning "only the built-in palette is available"; a missing location never fails the request.
 */
@Service
class BpmnPaletteService(
    private val objectMapper: ObjectMapper,
    @param:Value("\${briany.bpmn.palette.descriptors-location:classpath*:/bpmn-descriptors/*.json}")
    private val location: String,
) {
    private val log = LoggerFactory.getLogger(BpmnPaletteService::class.java)
    private val resolver = PathMatchingResourcePatternResolver()

    fun palette(): BpmnPalette {
        val resources =
            try {
                resolver.getResources(location)
            } catch (ex: IOException) {
                log.warn("Could not resolve palette descriptors at '{}': {}", location, ex.message)
                emptyArray()
            }
        val elements =
            resources
                .filter { it.isReadable }
                .map { res -> res.inputStream.use { objectMapper.readValue(it, BpmnPaletteElementsInner::class.java) } }
                .sortedBy { it.id }
        return BpmnPalette(elements = elements)
    }
}
