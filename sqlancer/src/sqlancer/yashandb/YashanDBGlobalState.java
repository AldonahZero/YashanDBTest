package sqlancer.yashandb;

import java.sql.SQLException;

import sqlancer.SQLGlobalState;

public class YashanDBGlobalState extends SQLGlobalState<YashanDBOptions, YashanDBSchema> {
    @Override
    protected YashanDBSchema readSchema() throws SQLException {
        return YashanDBSchema.fromConnection(getConnection(), getDatabaseName());
    }
}
