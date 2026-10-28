package com.fundgate.rulecard;

import com.fundgate.shareclasses.Isin;
import java.util.Optional;

/** Out port: the Rule Cards, Draft or Validated, each covering the Share Classes of one fund. */
public interface RuleCards {

    /**
     * The card that decides for the class: its latest Validated version, or else its latest Draft; empty when the
     * fund is not covered. Throws {@link com.fundgate.bankdata.FundDataUnavailable}.
     */
    Optional<RuleCard> forShareClass(Isin shareClass);
}
