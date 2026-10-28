package com.fundgate.ruleengine;

import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.clientprofiles.ClientProfile.Classification;
import com.fundgate.clientprofiles.ClientProfile.Mandate;
import com.fundgate.eligibility.SubscriptionRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The Client Profile facts one check reads, each as the Citation of the field it came from. Absent means unknown. */
record Facts(SubscriptionRequest request, ClientProfile profile) {

    Optional<Classification.Category> category() {
        return profile.classification().flatMap(Classification::category);
    }

    Optional<List<Classification.Declaration>> declarations() {
        return profile.classification().flatMap(Classification::declarations);
    }

    Optional<Mandate> mandate() {
        return profile.portfolio().flatMap(ClientProfile.Portfolio::mandate);
    }

    Optional<ClientProfile.MandateType> mandateType() {
        return mandate().flatMap(Mandate::type);
    }

    boolean declared(Classification.DeclarationType type) {
        return declarations().orElse(List.of()).stream().anyMatch(declaration -> declaration.type() == type);
    }

    Citation categoryField() {
        return field("classification.category", category());
    }

    Citation declarationsField() {
        return field("classification.declarations",
                declarations().map(all -> all.stream().map(declaration -> lower(declaration.type())).toList()));
    }

    Citation mandateTypeField() {
        return field("portfolio.mandate.type", mandateType());
    }

    static Citation field(String name, Optional<?> value) {
        return new Citation.ClientProfileField(name, value.map(Facts::lower).orElse("unknown"));
    }

    /** Enum values as the contract writes them, e.g. discretionary; anything else as it prints. */
    static String lower(Object value) {
        return value instanceof Enum<?> constant ? constant.name().toLowerCase(Locale.ROOT) : String.valueOf(value);
    }
}
