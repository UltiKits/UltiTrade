package com.ultikits.plugins.trade;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
            "1.19.2-R0.1-SNAPSHOT, 6.3.0, REFUSE",
            "1.20.6-R0.1-SNAPSHOT, 6.3.1-SNAPSHOT, REFUSE",
            "1.20.6, 6.4.0, COMPATIBLE",
            "1.20.6, 6.4.0-SNAPSHOT, COMPATIBLE",
            "1.21, 6.3.0, COMPATIBLE",
            "1.21.11-R0.1-SNAPSHOT, 6.3.0, COMPATIBLE",
            "1.20.6, 6.3.99, REFUSE",
            "1.20.6, 6.4.1, COMPATIBLE",
            "1.20.6, 6.10.0, COMPATIBLE",
            "1.20.6, 7.0.0, COMPATIBLE",
            "1.22, 6.3.0, COMPATIBLE",
            "2.0, 6.3.0, COMPATIBLE",
            "garbage, 6.3.0, UNKNOWN",
            "1.20.6, garbage, UNKNOWN",
            "garbage, garbage, UNKNOWN",
            ", 6.3.0, UNKNOWN",
            "1.20.6, , UNKNOWN",
            "1.20.6, 6.3, UNKNOWN",
            "1.20.6junk, 6.3.0, UNKNOWN",
            "1.20.6, 6.3.0junk, UNKNOWN",
            "999999999999999.20.6, 6.3.0, UNKNOWN",
            "1.20.6, 6.999999999999999.0, UNKNOWN"
    })
    void appliesVersionMatrix(String server, String framework, RuntimeCompatibility.Result expected) {
        assertThat(RuntimeCompatibility.check(server, framework)).isEqualTo(expected);
    }
}
