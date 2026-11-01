package com.fundgate.ruleengine;

import com.fundgate.bankdata.Country;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** US Persons are excluded, judged by the fund's own definition. */
public final class UsPersonExclusionKind implements RuleKindPlugin<UsPersonExclusionKind.UsPersonExclusion> {

    private static final Country US = new Country("US");

    /** Which US Person definition the prospectus uses; one US citizen can pass one fund and fail another. */
    public record UsPersonExclusion(UsPersonDefinition definition, boolean excludesUsCitizens) implements Condition {

        public UsPersonExclusion {
            Objects.requireNonNull(definition, "definition");
        }

        public enum UsPersonDefinition { REG_S, FATCA, IRC_CITIZENSHIP, MIXED }
    }

    @Override
    public RuleKind kind() {
        return RuleKind.US_PERSON_EXCLUSION;
    }

    @Override
    public Class<UsPersonExclusion> condition() {
        return UsPersonExclusion.class;
    }

    @Override
    public String prompt() {
        return "- us_person_exclusion: US persons are excluded; definition is reg_s, fatca, irc_citizenship or "
                + "mixed; excludesUsCitizens says whether US citizenship alone excludes.";
    }

    /**
     * Each definition names the facts that make a US Person; a known one gives Fail, an unknown one Review. Reg S
     * is residence, a Green Card included; some funds add citizenship; the IRC adds FATCA status.
     */
    @Override
    public Optional<Finding> evaluate(UsPersonExclusion condition, Facts facts, Lookups lookups) {
        if (condition.definition() == UsPersonExclusion.UsPersonDefinition.MIXED) {
            return Optional.of(Finding.review("the documents mix US Person definitions"));
        }
        Optional<ClientProfile.Client> client = facts.profile().client();
        Optional<Country> residence = client.flatMap(ClientProfile.Client::residenceCountry);
        Optional<Boolean> greenCard = client.flatMap(ClientProfile.Client::usPerson)
                .flatMap(ClientProfile.UsPerson::usPermanentResident);
        Optional<List<Country>> nationalities = client.flatMap(ClientProfile.Client::nationalities);
        Optional<ClientProfile.FatcaStatus> fatca = client.flatMap(ClientProfile.Client::usPerson)
                .flatMap(ClientProfile.UsPerson::fatcaStatus);

        List<Finding> usPersonFacts = new ArrayList<>();
        if (condition.definition() != UsPersonExclusion.UsPersonDefinition.FATCA) {
            usPersonFacts.add(fact(residence.map(US::equals), "resident in the United States",
                    Facts.field("client.residenceCountry", residence)));
            usPersonFacts.add(fact(greenCard, "a US permanent resident",
                    Facts.field("client.usPerson.usPermanentResident", greenCard)));
        }
        if (condition.excludesUsCitizens() || condition.definition() == UsPersonExclusion.UsPersonDefinition.IRC_CITIZENSHIP) {
            usPersonFacts.add(fact(nationalities.map(all -> all.contains(US)), "a US citizen",
                    Facts.field("client.nationalities", nationalities)));
        }
        if (condition.definition() == UsPersonExclusion.UsPersonDefinition.IRC_CITIZENSHIP
                || condition.definition() == UsPersonExclusion.UsPersonDefinition.FATCA) {
            usPersonFacts.add(fact(fatca.map(ClientProfile.FatcaStatus.US_PERSON::equals), "a US Person for FATCA",
                    Facts.field("client.usPerson.fatcaStatus", fatca)));
        }
        Finding worst = Finding.worst(usPersonFacts);
        return Optional.of(switch (worst.outcome()) {
            case PASS -> Finding.pass("not a US Person under the fund's definition")
                    .citing(usPersonFacts.stream().flatMap(fact -> fact.citations().stream()).toList());
            case REVIEW -> Finding.review("US Person status unknown: " + worst.reason()).citing(worst.citations());
            default -> worst;
        });
    }

    /** True makes a US Person, false does not, unknown needs review. */
    private static Finding fact(Optional<Boolean> isUsPerson, String what, Citation field) {
        return isUsPerson.map(yes -> yes ? Finding.fail(what, field) : Finding.pass("not " + what, field))
                .orElseGet(() -> Finding.review("whether " + what, field));
    }
}
