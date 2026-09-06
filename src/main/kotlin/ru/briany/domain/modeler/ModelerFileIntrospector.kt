package ru.briany.domain.modeler

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.w3c.dom.Element
import org.xml.sax.SAXException
import ru.briany.generated.model.ModelerFileError
import ru.briany.generated.model.ModelerFileErrorSeverity
import ru.briany.generated.model.ModelerFileType
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Derives file identity (key/name/description) from raw content, which is the source of truth.
 * Enforces strictly one primary element (process/decision) per file.
 */
@Component
class ModelerFileIntrospector(
    private val objectMapper: ObjectMapper,
) {
    data class Introspection(
        val type: ModelerFileType,
        val fileKey: String?,
        val name: String?,
        val description: String?,
        val errors: List<ModelerFileError>,
    )

    fun detectType(resourceName: String): ModelerFileType =
        when {
            resourceName.endsWith(BPMN_SUFFIX, ignoreCase = true) -> ModelerFileType.BPMN
            resourceName.endsWith(DMN_SUFFIX, ignoreCase = true) -> ModelerFileType.DMN
            resourceName.endsWith(BFORM_SUFFIX, ignoreCase = true) -> ModelerFileType.BFORM
            else ->
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file extension: '$resourceName' (expected .bpmn, .dmn or .bform)",
                )
        }

    fun introspect(
        resourceName: String,
        bytes: ByteArray,
    ): Introspection =
        when (detectType(resourceName)) {
            ModelerFileType.BPMN -> introspectXml(ModelerFileType.BPMN, bytes, "process")
            ModelerFileType.DMN -> introspectXml(ModelerFileType.DMN, bytes, "decision")
            ModelerFileType.BFORM -> introspectBform(bytes)
        }

    private fun introspectXml(
        type: ModelerFileType,
        bytes: ByteArray,
        elementName: String,
    ): Introspection {
        val root =
            parseXml(bytes)
                ?: return failure(type, PARSE_ERROR, "Invalid XML content")
        val elements = root.getElementsByTagNameNS("*", elementName)
        if (elements.length != 1) {
            val message = "Expected exactly one <$elementName>, found ${elements.length}"
            return failure(type, ELEMENT_COUNT, message)
        }
        val element = elements.item(0) as Element
        val id = element.getAttribute("id").trim()
        if (id.isEmpty()) {
            return failure(type, MISSING_ID, "<$elementName> is missing a non-empty id")
        }
        val name = element.getAttribute("name").trim().ifEmpty { null }
        return Introspection(type, id, name, extractDocumentation(element), emptyList())
    }

    private fun introspectBform(bytes: ByteArray): Introspection {
        val node =
            parseJson(bytes)
                ?: return failure(ModelerFileType.BFORM, PARSE_ERROR, "Invalid JSON content")
        val id = node.get("id")?.asString()?.trim().orEmpty()
        if (id.isEmpty()) {
            return failure(ModelerFileType.BFORM, MISSING_ID, "Form is missing a non-empty 'id'")
        }
        val label = node.get("components")?.firstOrNull()?.get("label")?.asString()
        return Introspection(ModelerFileType.BFORM, id, label ?: id, null, emptyList())
    }

    private fun parseXml(bytes: ByteArray): Element? =
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true)
            factory.setFeature(FEATURE_EXTERNAL_GENERAL, false)
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER, false)
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
        } catch (ex: SAXException) {
            log(ex)
            null
        } catch (ex: IOException) {
            log(ex)
            null
        } catch (ex: ParserConfigurationException) {
            log(ex)
            null
        }

    private fun parseJson(bytes: ByteArray): JsonNode? =
        try {
            objectMapper.readTree(bytes)
        } catch (ex: JacksonException) {
            log(ex)
            null
        }

    private fun extractDocumentation(element: Element): String? {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.localName == "documentation") {
                return child.textContent?.trim()?.ifEmpty { null }
            }
        }
        return null
    }

    private fun failure(
        type: ModelerFileType,
        code: String,
        message: String,
    ): Introspection =
        Introspection(
            type = type,
            fileKey = null,
            name = null,
            description = null,
            errors =
                listOf(
                    ModelerFileError(
                        severity = ModelerFileErrorSeverity.ERROR,
                        code = code,
                        message = message,
                        at = Instant.now(),
                    ),
                ),
        )

    private fun log(ex: Exception) {
        LOG.debug("Modeler file introspection failed: {}", ex.message)
    }

    companion object {
        const val BPMN_SUFFIX = ".bpmn"
        const val DMN_SUFFIX = ".dmn"
        const val BFORM_SUFFIX = ".bform"

        const val PARSE_ERROR = "PARSE_ERROR"
        const val ELEMENT_COUNT = "ELEMENT_COUNT"
        const val MISSING_ID = "MISSING_ID"

        private const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        private const val FEATURE_EXTERNAL_GENERAL = "http://xml.org/sax/features/external-general-entities"
        private const val FEATURE_EXTERNAL_PARAMETER = "http://xml.org/sax/features/external-parameter-entities"

        private val LOG = org.slf4j.LoggerFactory.getLogger(ModelerFileIntrospector::class.java)
    }
}
