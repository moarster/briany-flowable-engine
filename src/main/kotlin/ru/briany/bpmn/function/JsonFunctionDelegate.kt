package ru.briany.bpmn.function

import org.flowable.common.engine.api.FlowableIllegalArgumentException
import org.flowable.common.engine.api.delegate.FlowableMultiFunctionDelegate
import org.springframework.stereotype.Component
import ru.briany.bpmn.function.JsonFunctionDelegate.Companion.LOCAL_NAMES
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.lang.reflect.Method

/**
 * `${json:...}` JUEL functions for JSON objects in BPMN expressions.
 *
 *  - `json:merge(a, b)` - deep-merge two objects; `b` wins on scalar/array
 *    conflicts, nested objects merge recursively.
 *  - `json:mapOf(k1, v1, ...)` - build an object from alternating key/value
 *    pairs (even arg count, String keys).
 *
 * JUEL invokes functions statically, so the logic lives in `@JvmStatic`
 * companion methods; the bean only exists for auto-registration.
 */
@Component
class JsonFunctionDelegate : FlowableMultiFunctionDelegate {
    override fun prefix(): String = PREFIX

    override fun localNames(): Collection<String> = LOCAL_NAMES

    override fun functionMethod(
        prefix: String,
        localName: String,
    ): Method =
        when (localName) {
            "merge" -> MERGE_METHOD
            "mapOf" -> MAP_OF_METHOD
            else -> throw NoSuchMethodException("Unknown JUEL function $prefix:$localName")
        }

    companion object {
        const val PREFIX: String = "json"
        private val LOCAL_NAMES: Set<String> = setOf("merge", "mapOf")

        private val MAPPER: JsonMapper = JsonMapper.shared()

        private val MERGE_METHOD: Method =
            JsonFunctionDelegate::class.java.getDeclaredMethod(
                "merge",
                Any::class.java,
                Any::class.java,
            )

        private val MAP_OF_METHOD: Method =
            JsonFunctionDelegate::class.java.getDeclaredMethod(
                "mapOf",
                Array<Any?>::class.java,
            )

        @JvmStatic
        fun merge(
            a: Any?,
            b: Any?,
        ): JsonNode {
            val nodeA = toObjectNode(a, "a")
            val nodeB = toObjectNode(b, "b")
            return deepMerge(nodeA, nodeB)
        }

        @JvmStatic
        fun mapOf(vararg pairs: Any?): JsonNode {
            if (pairs.size % 2 != 0) {
                throw FlowableIllegalArgumentException(
                    "json:mapOf — expected an even number of arguments" +
                        " (key, value, key, value, ...), got ${pairs.size}",
                )
            }
            val result = MAPPER.createObjectNode()
            var i = 0
            while (i < pairs.size) {
                val key = pairs[i]
                if (key !is String) {
                    val typeName = key?.let { it::class.simpleName } ?: "null"
                    throw FlowableIllegalArgumentException(
                        "json:mapOf — key at position $i must be a String, got $typeName",
                    )
                }
                val value = pairs[i + 1]
                result.set(key, MAPPER.valueToTree(value))
                i += 2
            }
            return result
        }

        private fun toObjectNode(
            value: Any?,
            argName: String,
        ): ObjectNode {
            if (value == null) return MAPPER.createObjectNode()
            val tree: JsonNode =
                try {
                    MAPPER.valueToTree(value)
                } catch (e: JacksonException) {
                    throw FlowableIllegalArgumentException(
                        "json:merge — argument '$argName' (${value::class.simpleName})" +
                            " could not be converted to JSON: ${e.message}",
                        e,
                    )
                }
            if (tree !is ObjectNode) {
                throw FlowableIllegalArgumentException(
                    "json:merge — argument '$argName' must be a JSON object" +
                        " (Map / POJO / ObjectNode), got ${value::class.simpleName}" +
                        " resolving to ${tree.nodeType}",
                )
            }
            return tree
        }

        private fun deepMerge(
            base: ObjectNode,
            override: ObjectNode,
        ): ObjectNode {
            val result = base.deepCopy()
            override.properties().forEach { (key, value) ->
                val existing = result.get(key)
                if (existing is ObjectNode && value is ObjectNode) {
                    result.set(key, deepMerge(existing, value))
                } else {
                    result.set(key, value.deepCopy())
                }
            }
            return result
        }
    }
}
