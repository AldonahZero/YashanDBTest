package sqlancer.yashandb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.google.auto.service.AutoService;

import sqlancer.DatabaseProvider;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLProviderAdapter;
import sqlancer.common.query.SQLQueryAdapter;

@AutoService(DatabaseProvider.class)
public class YashanDBProvider extends SQLProviderAdapter<YashanDBGlobalState, YashanDBOptions> {
    public YashanDBProvider() {
        super(YashanDBGlobalState.class, YashanDBOptions.class);
    }

    @Override
    public String getDBMSName() {
        return "yashandb";
    }

    @Override
    public SQLConnection createDatabase(YashanDBGlobalState state) throws SQLException {
        String database = state.getDatabaseName();
        if (!database.matches("ys_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("YashanDB requires --database-prefix ys_<run_id>_");
        }
        YashanDBOptions options = state.getDbmsSpecificOptions();
        if (options.maxTables < 1 || options.maxTables > 8 || options.maxRows < 1 || options.maxRows > 1000
                || options.maxResultRows < 1 || options.queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Invalid generator bounds or timeout");
        }
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        if (host == null) {
            host = "127.0.0.1";
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = 1690;
        }
        String url = String.format("jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000", host, port);
        Connection connection = DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        SQLConnection result = new SQLConnection(connection) {
            @Override
            public Statement createStatement() throws SQLException {
                Statement statement = super.createStatement();
                statement.setQueryTimeout(options.queryTimeoutSeconds);
                return statement;
            }
        };
        try {
            for (String sql : new String[] { "DROP DATABASE IF EXISTS " + database, "CREATE DATABASE " + database,
                    "USE " + database }) {
                state.getState().logStatement(sql);
                try (Statement statement = result.createStatement()) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
        return result;
    }

    @Override
    public void generateDatabase(YashanDBGlobalState state) throws Exception {
        Randomly random = state.getRandomly();
        YashanDBOptions options = state.getDbmsSpecificOptions();
        int tables = random.getInteger(1, options.maxTables + 1);
        for (int table = 0; table < tables; table++) {
            String name = "t" + table;
            int columnCount = random.getInteger(2, 5);
            List<String> definitions = new ArrayList<>();
            for (int column = 0; column < columnCount; column++) {
                definitions.add("c" + column + " INT");
            }
            state.executeStatement(new SQLQueryAdapter("CREATE TABLE " + name + " ("
                    + String.join(", ", definitions) + ")", true));
            int rows = random.getInteger(0, options.maxRows + 1);
            for (int row = 0; row < rows; row++) {
                List<String> values = new ArrayList<>();
                for (int column = 0; column < columnCount; column++) {
                    values.add(Randomly.getBooleanWithSmallProbability() ? "NULL"
                            : String.valueOf(random.getInteger(-10, 11)));
                }
                // Identical rows and NULL rows exercise bag semantics and three-valued logic.
                if (row < 2) {
                    values.replaceAll(value -> "0");
                } else if (row == 2) {
                    values.replaceAll(value -> "NULL");
                }
                state.executeStatement(new SQLQueryAdapter("INSERT INTO " + name + " VALUES ("
                        + String.join(", ", values) + ")"));
            }
            if (options.testIndexes && Randomly.getBoolean()) {
                state.executeStatement(new SQLQueryAdapter("CREATE INDEX i" + table + " ON " + name + " (c0)", true));
            }
            if (Randomly.getBoolean()) {
                state.executeStatement(new SQLQueryAdapter("UPDATE " + name + " SET c1 = c0 WHERE c0 < -5"));
            }
            if (Randomly.getBooleanWithSmallProbability()) {
                state.executeStatement(new SQLQueryAdapter("DELETE FROM " + name + " WHERE c0 = 10"));
            }
        }
        state.updateSchema();
    }
}
