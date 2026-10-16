package sqlancer.yashandb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import sqlancer.SQLConnection;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.TableIndex;
import sqlancer.yashandb.YashanDBSchema.YashanDBTable;

public class YashanDBSchema extends AbstractSchema<YashanDBGlobalState, YashanDBTable> {
    public enum DataType {
        INT
    }

    public static class YashanDBColumn extends AbstractTableColumn<YashanDBTable, DataType> {
        public YashanDBColumn(String name) {
            super(name, null, DataType.INT);
        }
    }

    public static class YashanDBTable
            extends AbstractRelationalTable<YashanDBColumn, TableIndex, YashanDBGlobalState> {
        public YashanDBTable(String name, List<YashanDBColumn> columns, List<TableIndex> indexes) {
            super(name, columns, indexes, false);
            for (YashanDBColumn column : columns) {
                column.setTable(this);
            }
        }
    }

    public YashanDBSchema(List<YashanDBTable> tables) {
        super(tables);
    }

    public static YashanDBSchema fromConnection(SQLConnection connection, String database) throws SQLException {
        if (!database.matches("ys_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Use --database-prefix ys_<run_id>_");
        }
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + database
                        + "' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        List<YashanDBTable> tables = new ArrayList<>();
        for (String name : names) {
            if (!name.matches("t[0-9]+")) {
                throw new IllegalStateException("Unexpected table in dedicated database: " + name);
            }
            List<YashanDBColumn> columns = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                    "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                            + database + "' AND TABLE_NAME = '" + name + "' ORDER BY ORDINAL_POSITION")) {
                while (rs.next()) {
                    String type = rs.getString(2).toLowerCase(Locale.ROOT);
                    if (!type.matches("int|integer|int32|number|decimal")) {
                        throw new IllegalStateException("Unmapped generated INT type: " + type);
                    }
                    columns.add(new YashanDBColumn(rs.getString(1)));
                }
            }
            List<TableIndex> indexes = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                    "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '"
                            + database + "' AND TABLE_NAME = '" + name + "'")) {
                while (rs.next()) {
                    indexes.add(TableIndex.create(rs.getString(1)));
                }
            }
            if (columns.isEmpty()) {
                throw new IllegalStateException("No metadata columns for " + name);
            }
            tables.add(new YashanDBTable(name, columns, Collections.unmodifiableList(indexes)));
        }
        return new YashanDBSchema(tables);
    }
}
