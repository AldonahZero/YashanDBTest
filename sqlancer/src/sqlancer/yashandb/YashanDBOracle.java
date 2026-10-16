package sqlancer.yashandb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

public class YashanDBOracle implements TestOracle<YashanDBGlobalState> {
    private final YashanDBGlobalState state;
    private final YashanDBOracleFactory oracle;
    private String lastQuery;

    public YashanDBOracle(YashanDBGlobalState state, YashanDBOracleFactory oracle) {
        this.state = state;
        this.oracle = oracle;
    }

    /** Integer-only v1: NULL stays distinct, and numeric display scale is immaterial. */
    public static String canonicalNumber(String value) {
        return value == null ? null : new BigDecimal(value).stripTrailingZeros().toPlainString();
    }

    public static <T> Map<T, Long> bag(List<T> values) {
        Map<T, Long> result = new HashMap<>();
        for (T value : values) {
            result.merge(value, 1L, Long::sum);
        }
        return result;
    }

    private List<List<String>> readRows(String sql, int columns) throws Exception {
        List<List<String>> result = new ArrayList<>();
        try (SQLancerResultSet rs = state.executeStatementAndGet(new SQLQueryAdapter(sql))) {
            if (rs == null) {
                throw new AssertionError("Unexpected empty JDBC result handle: " + sql);
            }
            while (rs.next()) {
                if (result.size() >= state.getDbmsSpecificOptions().maxResultRows) {
                    throw new IgnoreMeException();
                }
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(canonicalNumber(rs.getString(column)));
                }
                result.add(row);
            }
        }
        return result;
    }

    private BigInteger count(String sql) throws Exception {
        List<List<String>> rows = readRows(sql, 1);
        if (rows.size() != 1 || rows.get(0).get(0) == null) {
            throw new AssertionError("Unexpected COUNT/SUM result: " + sql + " => " + rows);
        }
        return new BigDecimal(rows.get(0).get(0)).toBigIntegerExact();
    }

    private void tlp(YashanDBExpressionGenerator generator, String predicate) throws Exception {
        List<String> columns = generator.projection();
        String select = "SELECT " + String.join(", ", columns) + " FROM " + generator.from();
        lastQuery = select;
        List<List<String>> original = readRows(select, columns.size());
        String first = select + " WHERE (" + predicate + ")";
        String second = select + " WHERE NOT (" + predicate + ")";
        // This build accepts scalar IS NULL but rejects IS NULL applied to a predicate.
        String third = select + " WHERE CASE WHEN (" + predicate + ") THEN 0 WHEN NOT ("
                + predicate + ") THEN 0 ELSE 1 END = 1";
        // Separate executions isolate predicate evaluation from UNION's own coercions and rewrites.
        List<List<String>> partitioned = new ArrayList<>(readRows(first, columns.size()));
        partitioned.addAll(readRows(second, columns.size()));
        partitioned.addAll(readRows(third, columns.size()));
        if (!bag(original).equals(bag(partitioned))) {
            throw new ResultMismatch("TLP_WHERE mismatch\n" + select + ";\n" + first + ";\n" + second
                    + ";\n" + third + ";\noriginal=" + bag(original) + "\npartitioned=" + bag(partitioned));
        }
    }

    private void norec(YashanDBExpressionGenerator generator, String predicate) throws Exception {
        String optimized = "SELECT COUNT(*) FROM " + generator.from() + " WHERE (" + predicate + ")";
        String reference = "SELECT COALESCE(SUM(CASE WHEN (" + predicate
                + ") THEN 1 ELSE 0 END), 0) FROM " + generator.from();
        lastQuery = optimized;
        BigInteger first = count(optimized);
        BigInteger second = count(reference);
        if (!first.equals(second)) {
            throw new ResultMismatch("NOREC mismatch\n" + optimized + ";\n" + reference
                    + ";\noptimized=" + first + ", reference=" + second);
        }
    }

    @Override
    public void check() throws Exception {
        YashanDBMetrics.attempted();
        try {
            YashanDBExpressionGenerator generator = new YashanDBExpressionGenerator(state);
            int depth = Math.min(4, Math.max(0, state.getOptions().getMaxExpressionDepth()));
            String predicate = generator.predicate(depth);
            if (oracle == YashanDBOracleFactory.TLP_WHERE) {
                tlp(generator, predicate);
            } else {
                norec(generator, predicate);
            }
            YashanDBMetrics.completed();
        } catch (IgnoreMeException e) {
            YashanDBMetrics.skipped();
            throw e;
        } catch (ResultMismatch e) {
            YashanDBMetrics.mismatched();
            throw e;
        } catch (Exception | AssertionError e) {
            YashanDBMetrics.failed();
            throw e;
        }
    }

    @Override
    public String getLastQueryString() {
        return lastQuery;
    }

    private static class ResultMismatch extends AssertionError {
        private static final long serialVersionUID = 1L;

        ResultMismatch(String message) {
            super(message);
        }
    }
}
