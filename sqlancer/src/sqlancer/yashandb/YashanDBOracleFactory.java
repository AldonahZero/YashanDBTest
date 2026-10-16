package sqlancer.yashandb;

import sqlancer.OracleFactory;
import sqlancer.common.oracle.TestOracle;

public enum YashanDBOracleFactory implements OracleFactory<YashanDBGlobalState> {
    TLP_WHERE, NOREC;

    @Override
    public TestOracle<YashanDBGlobalState> create(YashanDBGlobalState state) {
        return new YashanDBOracle(state, this);
    }
}
