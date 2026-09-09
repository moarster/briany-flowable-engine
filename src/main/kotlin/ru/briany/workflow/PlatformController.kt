package ru.briany.workflow

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.generated.api.PlatformApi
import ru.briany.generated.model.BpmnPalette
import ru.briany.generated.model.EngineCapabilities

/**
 * Contour 1 platform endpoints: the engine's self-description a modeling client reads once
 * (`GET /api/v1/engine-capabilities`) and the custom palette it renders
 * (`GET /api/v1/bpmn-palette`).
 */
@RestController
class PlatformController(
    private val engineCapabilitiesService: EngineCapabilitiesService,
    private val bpmnPaletteService: BpmnPaletteService,
) : PlatformApi {
    override fun getEngineCapabilities(): ResponseEntity<EngineCapabilities> = ResponseEntity.ok(engineCapabilitiesService.capabilities())

    override fun getBpmnPalette(): ResponseEntity<BpmnPalette> = ResponseEntity.ok(bpmnPaletteService.palette())
}
