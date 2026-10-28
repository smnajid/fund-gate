package com.fundgate.fundregistrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.bankdata.Country;
import com.fundgate.fundregistrations.Registration.InvestorType;
import com.fundgate.fundregistrations.fixture.FixtureFundRegistrations;
import com.fundgate.shareclasses.Isin;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The FundRegistrations port over the demo dataset's Fund Registration table (contracts/fund-registrations.md). */
class FixtureFundRegistrationsTest {

    private static final Isin GCE_BP = new Isin("LU0348926287");
    private static final Isin SIF_W = new Isin("LU2607415275");
    private static final Isin BNP_I = new Isin("CH0112537557");
    private static final Isin NOT_COVERED = new Isin("XXNOTCOV0001");

    private final FundRegistrations fundRegistrations =
            new FixtureFundRegistrations(Path.of("../docs/playbook/contracts/fixtures/fund-registrations.json"));

    @Test
    void a_class_registered_for_everyone_in_its_home_country() {
        assertThat(fundRegistrations.registration(GCE_BP, new Country("LU")))
                .isEqualTo(new Registration.Registered(Set.of(InvestorType.ALL)));
    }

    @Test
    void a_class_registered_for_qualified_investors_only() {
        assertThat(fundRegistrations.registration(BNP_I, new Country("CH")))
                .isEqualTo(new Registration.Registered(Set.of(InvestorType.QUALIFIED)));
    }

    @Test
    void a_country_missing_from_a_list_that_exists_is_not_registered() {
        // Row 9: no Swiss representative named for the SIF
        assertThat(fundRegistrations.registration(SIF_W, new Country("CH")))
                .isEqualTo(new Registration.NotRegistered());
    }

    @Test
    void a_class_with_no_list_at_all_is_unknown() {
        assertThat(fundRegistrations.registration(NOT_COVERED, new Country("CH")))
                .isEqualTo(new Registration.Unknown());
    }
}
