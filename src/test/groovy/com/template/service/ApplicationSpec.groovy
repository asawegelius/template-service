package com.template.service

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class ApplicationSpec extends Specification {

    def "context loads"() {
        expect:
        true
    }
}
