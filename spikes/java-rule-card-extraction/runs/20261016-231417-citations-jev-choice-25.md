# Score: citations-jev-choice-25 (spring-ai claude-opus-5-5 + claude-sonnet-5-5)

- Scope: 25 prospectus pages [1,2,3,4,5,6,16,67,106,123,124,131,136,137,138,210,248,249,857,859,860,861,880,936,944]
- Cost $0.2114, latency 149.4s (claude-opus-5-5 118.1s $0.1451758, claude-sonnet-5-5 31.3s $0.066268)
- Card status: draft
- **Rule recall 18/18**, **value accuracy 18/18**
- Citation support: 45/45 quotes verified on their page; 0 rules with no citation
- Traps: mandate-only-BI: avoided; contradiction-BI: flagged

| gold rule | class | found | value | quotes verified | on gold page | confidence |
|---|---|---|---|---|---|---|
| us-person | BP-EUR | found | ok | 5/5 | yes | 0.9 |
| us-person | BI-EUR | found | ok | 5/5 | yes | 0.9 |
| category-BP | BP-EUR | found | ok | 2/2 | yes | 0.95 |
| category-BI | BI-EUR | found | ok | 2/2 | yes | 0.9 |
| minimum-BP | BP-EUR | found | ok | 1/1 | yes | 0.85 |
| minimum-BI | BI-EUR | found | ok | 2/2 | yes | 0.9 |
| kid-BP | BP-EUR | found | ok | 3/3 | yes | 0.95 |
| kid-BI | BI-EUR | found | ok | 3/3 | yes | 0.95 |
| tm-client-BP | BP-EUR | found | ok | 2/2 | yes | 0.9 |
| tm-knowledge-BP | BP-EUR | found | ok | 2/2 | yes | 0.75 |
| tm-loss-BP | BP-EUR | found | ok | 1/1 | yes | 0.9 |
| tm-sri-BP | BP-EUR | found | ok | 1/1 | yes | 0.95 |
| tm-horizon-BP | BP-EUR | found | ok | 2/2 | yes | 0.95 |
| tm-client-BI | BI-EUR | found | ok | 3/3 | yes | 0.6 |
| tm-knowledge-BI | BI-EUR | found | ok | 2/2 | yes | 0.75 |
| tm-loss-BI | BI-EUR | found | ok | 1/1 | yes | 0.9 |
| tm-sri-BI | BI-EUR | found | ok | 1/1 | yes | 0.95 |
| tm-horizon-BI | BI-EUR | found | ok | 2/2 | yes | 0.95 |

## Unexpected rules (review by hand)
- BP-EUR mandate_only_class: The class is open to all investors; the prospectus places no discretionary-mandate condition on it.

## Unmodelled Restrictions extracted
- *: Jurisdiction/registration restriction: distribution, offering or investing is legal only where the shares are registered for public sale or where sale is not prohibited. Some funds or share classes available in certain jurisdictions may not be available in others.
- *: AML/KYC: investors must provide evidence of identity (and of beneficial owners), address and source of funds before account opening and on an ongoing basis. Account opening and transactions may be delayed or denied until satisfactory documents are received.
- *: Discretionary rejection of applications in whole or in part; eligibility information may be requested where an owner is or may become ineligible; soft or hard closure may apply to new or existing investors.
- *: Market timing/excessive trading: requests believed to represent excessive trading or market timing may be rejected, suspended or cancelled.
