package com.fundgate.clientprofiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.clientprofiles.ClientProfile.Classification.Category;
import com.fundgate.clientprofiles.ClientProfile.Classification.DeclarationType;
import com.fundgate.clientprofiles.ClientProfile.Classification.Regime;
import com.fundgate.clientprofiles.fixture.FixtureClientProfiles;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The ClientProfiles port over the demo dataset's fixtures (contracts/client-profile.md, demo-dataset.md). */
class FixtureClientProfilesTest {

    private final ClientProfiles clientProfiles =
            new FixtureClientProfiles(Path.of("../docs/playbook/contracts/fixtures/client-profiles"));

    @Test
    void toms_profile_carries_his_two_nationalities_his_opt_out_and_his_growth_risk_profile() {
        ClientProfile tom = clientProfiles.clientProfile(new PortfolioReference("LOC90000201"));

        assertThat(tom.legalEntity()).isEqualTo("LOC");
        assertThat(tom.client().orElseThrow().nationalities()).contains(List.of("CH", "US"));
        ClientProfile.Classification classification = tom.classification().orElseThrow();
        assertThat(classification.regime()).contains(Regime.FINSA);
        assertThat(classification.category()).contains(Category.PROFESSIONAL);
        assertThat(classification.declarations().orElseThrow())
                .extracting(ClientProfile.Classification.Declaration::type)
                .containsExactly(DeclarationType.FINSA_HNWI_OPT_OUT);
        assertThat(tom.portfolio().orElseThrow().investmentProfile().orElseThrow().riskProfile().orElseThrow().code())
                .isEqualTo("4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LOC90000101", "LOC90000102", "LOC90000201", "LUX90000301", "LUX90000401", "LUX90000501",
            "LUX90000601", "LOC90000701", "LUX90000801", "LOC90000901", "LOC90001001", "LOC90001101",
            "LOC90001201", "SIN90001301", "LOC90001401"})
    void every_portfolio_of_the_demo_dataset_loads(String reference) {
        PortfolioReference ref = new PortfolioReference(reference);

        ClientProfile profile = clientProfiles.clientProfile(ref);

        assertThat(profile.portfolioRef()).isEqualTo(ref);
        assertThat(profile.legalEntity()).isEqualTo(ref.legalEntity());
    }

    @Test
    void a_missing_us_person_status_is_unknown_not_a_default() {
        ClientProfile row23 = clientProfiles.clientProfile(new PortfolioReference("LOC90001201"));

        assertThat(row23.client().orElseThrow().usPerson()).isEmpty();
    }

    @Test
    void a_fixture_field_outside_the_contract_is_refused_not_ignored() {
        // "nationalitys": a typo that would otherwise leave the nationalities unknown
        ClientProfiles withATypo = new FixtureClientProfiles(Path.of("src/test/resources/client-profiles-outside-the-contract"));

        assertThatThrownBy(() -> withATypo.clientProfile(new PortfolioReference("LOC90000101")))
                .isInstanceOf(ClientDataUnavailable.class)
                .rootCause().hasMessageContaining("nationalitys");
    }

    @Test
    void a_known_reference_gives_its_portfolio_id() {
        // The Portfolio Id of LOC90000201 in the contract's example
        assertThat(clientProfiles.portfolioId(new PortfolioReference("LOC90000201")))
                .contains(new PortfolioId(UUID.fromString("4b0f6f3e-2f7a-4c55-9a59-0c8f1d5b7e21")));
    }

    @Test
    void a_reference_the_bank_does_not_know_gives_no_portfolio_id_and_no_profile() {
        PortfolioReference unknown = new PortfolioReference("LOC90009901");

        assertThat(clientProfiles.portfolioId(unknown)).isEmpty();
        assertThatThrownBy(() -> clientProfiles.clientProfile(unknown))
                .isInstanceOf(PortfolioNotFound.class)
                .hasMessageContaining("LOC")
                .hasMessageNotContaining("90009901");
    }

    @Test
    void a_portfolio_reference_printed_by_accident_hides_the_client_number() {
        PortfolioReference ref = new PortfolioReference("LOC90000201");

        assertThat(ref.toString()).isEqualTo("LOC********");
        assertThat(ref.value()).isEqualTo("LOC90000201");
    }

    @Test
    void a_malformed_reference_is_refused_without_echoing_it() {
        assertThatThrownBy(() -> new PortfolioReference("Anna Steinmann LOC9000010"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("Anna");
    }
}
