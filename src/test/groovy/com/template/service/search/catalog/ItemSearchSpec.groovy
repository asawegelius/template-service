package com.template.service.search.catalog

import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = [
        'spring.datasource.url=jdbc:h2:mem:catalog-search-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE',
        'spring.datasource.driver-class-name=org.h2.Driver',
        'spring.datasource.username=sa',
        'spring.datasource.password='
])
class ItemSearchSpec extends ItemSearchContract {
}
