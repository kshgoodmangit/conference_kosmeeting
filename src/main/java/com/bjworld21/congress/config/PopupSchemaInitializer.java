package com.bjworld21.congress.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PopupSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public PopupSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (hasContentColumn(connection)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE popups ADD COLUMN content LONGTEXT COMMENT '팝업 HTML 본문' AFTER useEndDate"
                );
            }
        }
    }

    private boolean hasContentColumn(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "popups", "content")) {
            return columns.next();
        }
    }
}
