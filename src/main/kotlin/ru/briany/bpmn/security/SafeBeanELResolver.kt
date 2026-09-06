package ru.briany.bpmn.security

import org.flowable.common.engine.impl.javax.el.ELContext
import org.flowable.common.engine.impl.javax.el.ELException
import org.flowable.common.engine.impl.javax.el.ELResolver
import org.slf4j.LoggerFactory
import org.springframework.aop.framework.AopProxyUtils
import ru.briany.bpmn.security.SafeBeanELResolver.Companion.ALLOWED_BASE_CLASSES
import ru.briany.bpmn.security.SafeBeanELResolver.Companion.ALLOWED_BASE_PREFIXES
import ru.briany.bpmn.security.SafeBeanELResolver.Companion.BLOCKED_METHODS
import ru.briany.bpmn.security.SafeBeanELResolver.Companion.BLOCKED_PROPERTIES

/**
 * Pre-bean EL resolver that sandboxes JUEL expressions against reflection
 * escapes such as `''.getClass().forName('java.lang.Runtime')...exec(...)`.
 *
 * Two layers: a base-class allow-list ([ALLOWED_BASE_PREFIXES] /
 * [ALLOWED_BASE_CLASSES]) gating which receivers are reachable at all (Spring
 * AOP proxies unwrapped to their target class first), plus a member-name
 * block-list ([BLOCKED_PROPERTIES] / [BLOCKED_METHODS]) as defence in depth
 * against known reflection / RCE / SSRF / DoS chains.
 *
 * On a violation we set `propertyResolved` and throw, so BeanELResolver never
 * runs; otherwise we return null and let the chain continue.
 */
class SafeBeanELResolver : ELResolver() {
    private val log = LoggerFactory.getLogger(SafeBeanELResolver::class.java)

    override fun getValue(
        context: ELContext,
        base: Any?,
        property: Any?,
    ): Any? {
        if (base == null || property == null) return null
        val name = property.toString()

        if (!isBaseAllowed(base)) {
            reject(context, base, name, "property")
        }
        if (name in BLOCKED_PROPERTIES) {
            reject(context, base, name, "property")
        }
        return null
    }

    override fun invoke(
        context: ELContext,
        base: Any?,
        method: Any?,
        paramTypes: Array<Class<*>>?,
        params: Array<Any?>?,
    ): Any? {
        if (base == null || method == null) return null
        val name = method.toString()

        if (!isBaseAllowed(base)) {
            reject(context, base, name, "method")
        }
        if (name in BLOCKED_METHODS) {
            reject(context, base, name, "method")
        }
        return null
    }

    override fun getType(
        context: ELContext,
        base: Any?,
        property: Any?,
    ): Class<*>? = null

    override fun setValue(
        context: ELContext,
        base: Any?,
        property: Any?,
        value: Any?,
    ) {
        // no-op — let the real BeanELResolver handle writes
    }

    override fun isReadOnly(
        context: ELContext,
        base: Any?,
        property: Any?,
    ): Boolean = false

    override fun getCommonPropertyType(
        context: ELContext,
        base: Any?,
    ): Class<*>? = null

    private fun isBaseAllowed(base: Any): Boolean {
        // Unwrap Spring AOP proxies so allow-list checks see the real target class.
        val cls: Class<*> =
            try {
                AopProxyUtils.ultimateTargetClass(base)
            } catch (_: Throwable) {
                base.javaClass
            }
        val name = cls.name
        if (name in ALLOWED_BASE_CLASSES) return true
        if (ALLOWED_BASE_PREFIXES.any { name.startsWith(it) }) return true
        return false
    }

    private fun reject(
        context: ELContext,
        base: Any,
        memberName: String,
        kind: String,
    ): Nothing {
        context.isPropertyResolved = true
        log.warn("Blocked JUEL {} access: {}.{}", kind, base.javaClass.name, memberName)
        throw ELException("$kind '$memberName' is not allowed in expressions")
    }

    companion object {
        /**
         * Exact-match allowed base classes (wrappers and other narrow types).
         * Notably absent: `Class`, `Runtime`, `System`, `Thread`, `ClassLoader`.
         */
        val ALLOWED_BASE_CLASSES: Set<String> =
            setOf(
                "java.lang.String",
                "java.lang.Boolean",
                "java.lang.Character",
                "java.lang.Byte",
                "java.lang.Short",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Float",
                "java.lang.Double",
                "java.lang.Number",
                "java.lang.Object",
                "java.lang.Enum",
                "java.lang.StringBuilder",
                "java.lang.StringBuffer",
            )

        /** Allowed base-class package prefixes (any type inside may be a receiver). */
        val ALLOWED_BASE_PREFIXES: List<String> =
            listOf(
                "ru.briany.", // our own beans
                "org.flowable.", // ExecutionEntity, TaskEntity, VariableInstance
                "java.util.", // Map, List, Set, Iterator, Optional, ...
                "java.time.", // Instant, LocalDate, ZonedDateTime, Duration, ...
                "java.math.", // BigDecimal, BigInteger
                "tools.jackson.databind.", // JsonNode and subtypes
            )

        /** Property accessors exposing reflection / classloader; blocked even on allowed bases. */
        val BLOCKED_PROPERTIES: Set<String> =
            setOf(
                "class", // ${''.class} -> java.lang.Class
                "classLoader",
                "contextClassLoader",
                "systemClassLoader",
                "protectionDomain",
                // Spring AOP proxy introspection
                "targetClass",
                "targetSource",
                "advised",
                "proxyClass",
            )

        /** Methods enabling reflection, process exec, SSRF or DoS; blocked even on allowed bases. */
        val BLOCKED_METHODS: Set<String> =
            setOf(
                // Reflection entry points
                "getClass",
                "forName",
                "getMethod",
                "getMethods",
                "getDeclaredMethod",
                "getDeclaredMethods",
                "getField",
                "getFields",
                "getDeclaredField",
                "getDeclaredFields",
                "getConstructor",
                "getConstructors",
                "getDeclaredConstructor",
                "getDeclaredConstructors",
                "newInstance",
                "invoke",
                // Process execution
                "exec",
                "getRuntime",
                "halt",
                "exit",
                // Classloader access
                "loadClass",
                "defineClass",
                "getClassLoader",
                "getContextClassLoader",
                "setContextClassLoader",
                "getProtectionDomain",
                // Resource exfiltration
                "getResource",
                "getResourceAsStream",
                // SSRF / network primitives
                "openStream",
                "openConnection",
                "getContent",
                "toURL",
                // Spring AOP proxy introspection (method form of the properties above)
                "getTargetClass",
                "getTargetSource",
                "getAdvised",
                "getProxyClass",
                // Thread / monitor DoS
                "wait",
                "notify",
                "notifyAll",
            )
    }
}
