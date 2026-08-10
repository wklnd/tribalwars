package com.twlan.backend.service;

import com.twlan.backend.domain.BuildingType;
import com.twlan.backend.domain.MovementType;
import com.twlan.backend.domain.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Hibernate's ddl-auto=update never widens an existing database ENUM column, so when new BuildingType / UnitType / MovementType
// constants are appended an older save would reject them. This finds every ENUM column that holds only constants of
// one of those enums but not all of them yet, and widens it (existing values are converted by name) before the game
// loop starts. Idempotent: does nothing when a column already holds every value.
@Component
public class SchemaMigration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, DTD_IDENTIFIER, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE DATA_TYPE = 'ENUM' AND TABLE_SCHEMA = 'PUBLIC'");
        for (Map<String, Object> col : columns) {
            String table = (String) col.get("TABLE_NAME");
            String column = (String) col.get("COLUMN_NAME");
            List<String> have = jdbc.queryForList(
                    "SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES WHERE OBJECT_NAME = ? AND OBJECT_TYPE = 'TABLE' "
                            + "AND ENUM_IDENTIFIER = ? ORDER BY VALUE_ORDINAL",
                    String.class, table, col.get("DTD_IDENTIFIER"));
            for (List<String> want : List.of(names(BuildingType.values()), names(UnitType.values()), names(MovementType.values()))) {
                // H2 lists a native ENUM's values alphabetically, so compare as sets: the column holds some of the constants
                if (have.size() > 0 && have.size() < want.size() && want.containsAll(have)) {
                    log.info("Widening {}.{} enum from {} to {} values", table, column, have.size(), want.size());
                    String list = want.stream().map(n -> "'" + n + "'").collect(Collectors.joining(", "));
                    boolean notNull = "NO".equals(col.get("IS_NULLABLE"));
                    jdbc.execute("ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" ENUM(" + list + ")"
                            + (notNull ? " NOT NULL" : ""));
                }
            }
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
