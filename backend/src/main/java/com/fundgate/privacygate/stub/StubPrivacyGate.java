package com.fundgate.privacygate.stub;

import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.privacygate.GateRefusal;
import com.fundgate.privacygate.GateResult;
import com.fundgate.privacygate.GatedEmail;
import com.fundgate.privacygate.PrivacyGate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

/** Walking skeleton (#57): swaps the references it finds and refuses an unknown one; no client name scan yet. */
public class StubPrivacyGate implements PrivacyGate {

    private final ClientProfiles clientProfiles;

    public StubPrivacyGate(ClientProfiles clientProfiles) {
        this.clientProfiles = clientProfiles;
    }

    @Override
    public GateResult gate(String email) {
        Map<PortfolioId, PortfolioReference> swaps = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        Matcher found = PortfolioReference.FORMAT.matcher(email);
        StringBuilder text = new StringBuilder();
        while (found.find()) {
            PortfolioReference ref = new PortfolioReference(found.group());
            Optional<PortfolioId> id = clientProfiles.portfolioId(ref);
            id.ifPresentOrElse(known -> swaps.put(known, ref), () -> unknown.add(ref.value()));
            found.appendReplacement(text, id.map(PortfolioId::toString).orElse(ref.value()));
        }
        found.appendTail(text);
        if (!unknown.isEmpty()) {
            return new GateRefusal("Portfolio Reference the bank does not know", unknown);
        }
        return new GatedEmail(text.toString(), swaps);
    }
}
