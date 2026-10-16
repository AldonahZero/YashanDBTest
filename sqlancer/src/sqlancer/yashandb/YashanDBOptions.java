package sqlancer.yashandb;

import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;

@Parameters(separators = "=", commandDescription = "YashanDB mysql mode (default port 1690)")
public class YashanDBOptions implements DBMSSpecificOptions<YashanDBOracleFactory> {
    @Parameter(names = "--oracle")
    public List<YashanDBOracleFactory> oracles = Arrays.asList(YashanDBOracleFactory.TLP_WHERE);

    @Parameter(names = "--max-tables")
    public int maxTables = 3;

    @Parameter(names = "--max-rows")
    public int maxRows = 25;

    @Parameter(names = "--max-result-rows")
    public int maxResultRows = 10000;

    @Parameter(names = "--query-timeout-seconds")
    public int queryTimeoutSeconds = 15;

    @Parameter(names = "--test-joins", arity = 1)
    public boolean testJoins = true;

    @Parameter(names = "--test-indexes", arity = 1)
    public boolean testIndexes = true;

    @Override
    public List<YashanDBOracleFactory> getTestOracleFactory() {
        return oracles;
    }
}
