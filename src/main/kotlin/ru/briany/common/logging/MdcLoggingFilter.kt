package ru.briany.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class MdcLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val requestId =
                request.getHeader("X-Request-ID")
                    ?: UUID.randomUUID().toString().take(8)
            MDC.put(MdcContext.REQUEST_ID, requestId)

            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                val userId =
                    when (val details = authentication.details) {
                        is String -> details
                        else -> authentication.name
                    }
                if (userId != null && userId != "anonymousUser") {
                    MDC.put(MdcContext.USER_ID, userId)
                }
            }

            val tenantId =
                request.getHeader("X-Tenant-ID")
                    ?: request.getHeader("customer_key")
            if (tenantId != null) {
                MDC.put(MdcContext.TENANT_ID, tenantId)
            }

            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MdcContext.USER_ID)
            MDC.remove(MdcContext.TENANT_ID)
            MDC.remove(MdcContext.REQUEST_ID)
        }
    }
}
