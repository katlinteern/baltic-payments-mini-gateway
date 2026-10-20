package com.baltic.gateway.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PayeeNameMatcherTest {
    @ParameterizedTest(name = "[{index}] \"{0}\" vs \"{1}\" -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            Bob Tamm        | Bob Tamm       | MATCH
            bob   tamm      | Bob Tamm       | MATCH
            Tamm, Bob       | Bob Tamm       | MATCH
            Jüri Õun        | Juri Oun       | MATCH
            Bob             | Bob Tamm       | CLOSE_MATCH
            Bob Tamn        | Bob Tamm       | CLOSE_MATCH
            Alice Kask      | Bob Tamm       | NO_MATCH
            B               | Bob Tamm       | NO_MATCH
            Bobby Tomm      | Bob Tamm       | NO_MATCH
            '   '           | Bob Tamm       | NO_MATCH
            """)
    void comparesPayerProvidedNameWithAccountHolder(String provided, String holder, NameCheckResult expected) {
        assertEquals(expected, PayeeNameMatcher.check(provided, holder));
    }

    @Test void holderNameUnavailableIsNotAFailure() {
        assertEquals(NameCheckResult.NOT_AVAILABLE, PayeeNameMatcher.check("Anyone", null));
        assertEquals(NameCheckResult.NOT_AVAILABLE, PayeeNameMatcher.check("Anyone", "  "));
    }
}
