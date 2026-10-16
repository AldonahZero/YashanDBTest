package sqlancer.yashandb;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.yashandb.YashanDBSchema.YashanDBColumn;
import sqlancer.yashandb.YashanDBSchema.YashanDBTable;

/** Bounded integer expressions, evaluated by the DBMS, without a MySQL client-side evaluator. */
public class YashanDBExpressionGenerator {
    private final YashanDBGlobalState state;
    private final List<String> columns = new ArrayList<>();
    private final String from;

    public YashanDBExpressionGenerator(YashanDBGlobalState state) {
        this.state = state;
        List<YashanDBTable> available = state.getSchema().getDatabaseTables();
        YashanDBTable first = Randomly.fromList(available);
        List<YashanDBTable> selected = new ArrayList<>();
        selected.add(first);
        String clause = first.getName();
        if (state.getDbmsSpecificOptions().testJoins && available.size() > 1 && Randomly.getBoolean()) {
            List<YashanDBTable> others = new ArrayList<>(available);
            others.remove(first);
            YashanDBTable second = Randomly.fromList(others);
            selected.add(second);
            clause += Randomly.fromOptions(" INNER JOIN ", " LEFT JOIN ");
            clause += second.getName() + " ON " + first.getName() + ".c0 = " + second.getName() + ".c0";
        }
        from = clause;
        for (YashanDBTable table : selected) {
            for (YashanDBColumn column : table.getColumns()) {
                columns.add(column.getFullQualifiedName());
            }
        }
    }

    public String from() {
        return from;
    }

    public List<String> projection() {
        List<String> result = new ArrayList<>();
        result.add(Randomly.fromList(columns));
        if (Randomly.getBoolean()) {
            result.add(Randomly.fromList(columns));
        }
        return result;
    }

    private String scalar(int depth) {
        if (depth <= 0 || Randomly.getBoolean()) {
            return Randomly.fromOptions(Randomly.fromList(columns), "-1", "0", "1",
                    String.valueOf(state.getRandomly().getInteger(-10, 11)));
        }
        switch (state.getRandomly().getInteger(0, 4)) {
        case 0:
            return "COALESCE(" + scalar(depth - 1) + ", 0)";
        case 1:
            return "ABS(" + scalar(depth - 1) + ")";
        case 2:
            return "NULLIF(" + Randomly.fromList(columns) + ", " + scalar(depth - 1) + ")";
        default:
            return "(" + scalar(depth - 1) + Randomly.fromOptions(" + ", " - ")
                    + scalar(depth - 1) + ")";
        }
    }

    public String predicate(int depth) {
        if (depth > 0 && Randomly.getBoolean()) {
            if (Randomly.getBooleanWithSmallProbability()) {
                return "NOT (" + predicate(depth - 1) + ")";
            }
            return "(" + predicate(depth - 1) + Randomly.fromOptions(" AND ", " OR ")
                    + predicate(depth - 1) + ")";
        }
        int scalarDepth = Math.min(2, Math.max(0, state.getOptions().getMaxExpressionDepth()));
        String left = scalar(scalarDepth);
        switch (state.getRandomly().getInteger(0, 4)) {
        case 0:
            return "(" + left + ") IS NULL";
        case 1:
            return "(" + left + ") IS NOT NULL";
        case 2:
            return "(" + left + ") IN (-1, 0, 1, NULL)";
        default:
            return "(" + left + Randomly.fromOptions(" = ", " <> ", " < ", " <= ", " > ", " >= ")
                    + scalar(scalarDepth) + ")";
        }
    }
}
