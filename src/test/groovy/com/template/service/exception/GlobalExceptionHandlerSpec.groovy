package com.template.service.exception

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import com.template.service.logging.CorrelationIdFilter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import spock.lang.Specification

class GlobalExceptionHandlerSpec extends Specification {

    private Logger logger
    private ListAppender appender

    def setup() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler)
        appender = new ListAppender()
        appender.start()
        logger.addAppender(appender)
    }

    def cleanup() {
        logger.detachAppender(appender)
        appender.stop()
        MDC.clear()
    }

    def "bad request response includes stable error code and correlation id"() {
        given:
        def handler = new GlobalExceptionHandler()
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, 'corr-123')

        when:
        def response = handler.handleIllegalArgument(new IllegalArgumentException('invalid input'))

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.errorCode() == ErrorCode.BAD_REQUEST.value()
        response.body.message() == 'Bad request'
        response.body.correlationId() == 'corr-123'

        and:
        appender.list.size() == 1
        def event = appender.list.first()
        event.level == Level.WARN
        event.mdcPropertyMap[GlobalExceptionHandler.ERROR_CODE_MDC_KEY] == ErrorCode.BAD_REQUEST.value()
        event.mdcPropertyMap[CorrelationIdFilter.CORRELATION_ID_MDC_KEY] == 'corr-123'

        and:
        MDC.get(GlobalExceptionHandler.ERROR_CODE_MDC_KEY) == null
    }

    def "internal server error response includes stable error code and logs at error level"() {
        given:
        def handler = new GlobalExceptionHandler()
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, 'corr-500')

        when:
        def response = handler.handleException(new IllegalStateException('boom'))

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.errorCode() == ErrorCode.INTERNAL_SERVER_ERROR.value()
        response.body.message() == 'Internal server error'
        response.body.correlationId() == 'corr-500'

        and:
        appender.list.size() == 1
        def event = appender.list.first()
        event.level == Level.ERROR
        event.mdcPropertyMap[GlobalExceptionHandler.ERROR_CODE_MDC_KEY] == ErrorCode.INTERNAL_SERVER_ERROR.value()
        event.mdcPropertyMap[CorrelationIdFilter.CORRELATION_ID_MDC_KEY] == 'corr-500'
    }
}
