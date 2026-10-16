package sqlancer.yashandb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class TestYashanDBComparison {
    @Test
    public void preservesMultiplicityEvenWhenCardinalityAndDistinctValuesAgree() {
        assertNotEquals(YashanDBOracle.bag(Arrays.asList(1, 1, 2)),
                YashanDBOracle.bag(Arrays.asList(1, 2, 2)));
    }

    @Test
    public void ignoresOrderAndPreservesNullMultiplicity() {
        assertEquals(YashanDBOracle.bag(Arrays.asList(null, "1", null)),
                YashanDBOracle.bag(Arrays.asList("1", null, null)));
        assertNotEquals(YashanDBOracle.bag(Arrays.asList(null, "1", null)),
                YashanDBOracle.bag(Arrays.asList("1", null, "1")));
    }

    @Test
    public void canonicalizesOnlyTheSupportedNumericDomain() {
        assertEquals("1", YashanDBOracle.canonicalNumber("1.000"));
        assertEquals("0", YashanDBOracle.canonicalNumber("-0.0"));
        assertEquals(null, YashanDBOracle.canonicalNumber(null));
    }
}
