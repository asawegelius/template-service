package com.template.service.search.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/** Transactional revision tracking for the embedded development database. */
public final class H2CatalogRevisionTrigger implements Trigger {

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE catalog_revision SET revision = revision + 1 WHERE id = 1");
        }
    }
}
