# Score: citations-robeco (spring-ai claude-opus-5-5 + claude-sonnet-5-5)

- Scope: 25 prospectus pages [1,2,3,4,5,14,15,21,22,23,71,95,96,130,131,241,242,243,244,366,367,485,486,543,544]
- Cost $0.6164, latency 83.3s (claude-opus-5-5 54.4s $0.561218, claude-sonnet-5-5 28.9s $0.05521)
- Card status: draft
- **Rule recall 8/8**, **value accuracy 8/8**
- Citation support: 29/31 quotes verified on their page; 0 rules with no citation
- Traps: mandate-I: avoided

| gold rule | class | found | value | quotes verified | on gold page | confidence |
|---|---|---|---|---|---|---|
| us-person | D-EUR | found | ok | 5/5 | yes | 0.85 |
| us-person | I-EUR | found | ok | 5/5 | yes | 0.85 |
| category-D | D-EUR | found | ok | 2/2 | yes | 0.95 |
| category-I | I-EUR | found | ok | 4/6 | yes | 0.9 |
| minimum-D | D-EUR | found | ok | 1/1 | yes | 0.85 |
| minimum-I | I-EUR | found | ok | 2/2 | yes | 0.85 |
| tm-horizon | D-EUR | found | ok | 1/1 | yes | 0.9 |
| tm-horizon | I-EUR | found | ok | 1/1 | yes | 0.9 |

## Unexpected rules (review by hand)
- * kid_required_for_retail: Subscriptions are accepted only on the basis of the prospectus and the key investor information documents (UCITS KIID per sub-fund). The excerpt gives no evidence of a PRIIPs KID, so kid_available is left unset. The validator should check whether a PRIIPs KID exists.
- * target_market: Suitable for investors who want their investment to contribute fully to a sustainability objective, and also for experienced investors with specific investment objectives. No client_type category from the allowed set is stated, so value is left null. The information is for reference only.
- * target_market: No SRRI/SRI is stated in the excerpt. Only a sustainability risk rating (scale 1-5, shown as 2) appears, which is not an SRI.
- D-EUR mandate_only_class: Class D is not reserved to discretionary-mandate clients, since it is available to all investors.

## Unmodelled Restrictions extracted
- *: US benefit plan investors (ERISA): shares may neither be offered nor sold to them.
- *: Hong Kong: the prospectus is not registered there and may only be distributed to Professional Investors as defined in the Securities and Futures Ordinance.
- *: Germany: no restriction. Smart Energy Equities is not on the list of sub-funds without a §310 KAGB notification. Only the listed sub-funds may not be distributed to shareholders in Germany.
