package com.template.service.search.catalog

import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.conf.ParamType
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class SearchSqlTrace implements ExecuteListener {
    private final ThreadLocal<Trace> current = new ThreadLocal<>()

    @Bean
    DefaultExecuteListenerProvider searchTraceListener() {
        new DefaultExecuteListenerProvider(this)
    }

    Trace startTrace(Closure afterRevision = null) {
        def trace = new Trace(afterRevision: afterRevision)
        current.set(trace)
        trace
    }

    void stopTrace() {
        current.remove()
    }

    @Override
    void recordEnd(ExecuteContext ctx) {
        if (current.get() != null) {
            ctx.data('searchTestRows', ((ctx.data('searchTestRows') ?: 0) as int) + 1)
        }
    }

    @Override
    void end(ExecuteContext ctx) {
        def trace = current.get()
        if (trace == null || ctx.query() == null) {
            return
        }
        def statement = new Statement(ctx.sql(), ctx.query().getSQL(ParamType.INLINED),
                (ctx.data('searchTestRows') ?: 0) as int)
        trace.statements.add(statement)
        if (trace.afterRevision != null && statement.sql.contains('catalog_revision')) {
            def callback = trace.afterRevision
            trace.afterRevision = null
            callback.call()
        }
    }

    static class Trace {
        final List<Statement> statements = []
        Closure afterRevision
    }

    static record Statement(String sql, String inlined, int rows) {
    }
}
