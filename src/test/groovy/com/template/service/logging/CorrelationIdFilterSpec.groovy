package com.template.service.logging

import java.util.UUID

import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class CorrelationIdFilterSpec extends Specification {

    def "uses incoming correlation id and exposes request context in MDC during the request"() {
        given:
        def filter = new CorrelationIdFilter('X-Correlation-Id')
        def request = new MockHttpServletRequest('GET', '/healthz')
        request.addHeader('X-Correlation-Id', 'corr-123')
        def response = new MockHttpServletResponse()
        def observedValues = [:]
        FilterChain chain = { req, res ->
            observedValues.correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)
            observedValues.method = MDC.get(CorrelationIdFilter.HTTP_METHOD_MDC_KEY)
            observedValues.path = MDC.get(CorrelationIdFilter.REQUEST_PATH_MDC_KEY)
        }

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader('X-Correlation-Id') == 'corr-123'
        observedValues == [
            correlationId: 'corr-123',
            method       : 'GET',
            path         : '/healthz'
        ]
        MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY) == null
        MDC.get(CorrelationIdFilter.HTTP_METHOD_MDC_KEY) == null
        MDC.get(CorrelationIdFilter.REQUEST_PATH_MDC_KEY) == null
    }

    def "generates correlation id when header is missing"() {
        given:
        def filter = new CorrelationIdFilter('X-Correlation-Id')
        def request = new MockHttpServletRequest('POST', '/api/example')
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> }

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader('X-Correlation-Id')
        UUID.fromString(response.getHeader('X-Correlation-Id'))
    }
}
