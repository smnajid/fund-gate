# Score: gold-pages-literal

- Scope: 9 prospectus pages, pass-1 input 64,647 tokens
- Cost $0.2205, latency 1095.3s (claude-opus-5-5 (claude -p) 1095.3s $0.2205)
- **Rule recall 18/18**, **value accuracy 18/18**
- Citation support: 38/38 quotes verified on their page; 0 rules with no citation
- Traps: mandate-only-BI: avoided; contradiction-BI: flagged

| gold rule | class | found | value | quotes verified | on gold page | confidence |
|---|---|---|---|---|---|---|
| us-person | BP-EUR | found | ok | 3/3 | yes | 0.8 |
| us-person | BI-EUR | found | ok | 3/3 | yes | 0.8 |
| category-BP | BP-EUR | found | ok | 2/2 | yes | 0.95 |
| category-BI | BI-EUR | found | ok | 3/3 | yes | 0.85 |
| minimum-BP | BP-EUR | found | ok | 1/1 | yes | 0.9 |
| minimum-BI | BI-EUR | found | ok | 3/3 | yes | 0.9 |
| kid-BP | BP-EUR | found | ok | 2/2 | yes | 0.95 |
| kid-BI | BI-EUR | found | ok | 2/2 | yes | 0.95 |
| tm-client-BP | BP-EUR | found | ok | 2/2 | yes | 0.9 |
| tm-knowledge-BP | BP-EUR | found | ok | 2/2 | yes | 0.6 |
| tm-loss-BP | BP-EUR | found | ok | 1/1 | yes | 0.9 |
| tm-sri-BP | BP-EUR | found | ok | 1/1 | yes | 0.95 |
| tm-horizon-BP | BP-EUR | found | ok | 2/2 | no | 0.95 |
| tm-client-BI | BI-EUR | found | ok | 2/2 | yes | 0.7 |
| tm-knowledge-BI | BI-EUR | found | ok | 2/2 | yes | 0.6 |
| tm-loss-BI | BI-EUR | found | ok | 1/1 | yes | 0.9 |
| tm-sri-BI | BI-EUR | found | ok | 1/1 | yes | 0.95 |
| tm-horizon-BI | BI-EUR | found | ok | 2/2 | no | 0.95 |

## Unexpected rules (review by hand)
- BP-EUR mandate_only_class: Class P is open to all investors. It is not reserved for discretionary-mandate clients and needs no written agreement with the management company.

## Unmodelled Restrictions extracted
- *: Jurisdiction restriction: shares may be offered or bought only where they are registered for public sale or where sale is not prohibited by local law. Some classes may be unavailable in some jurisdictions. The client's country of residence must be checked against the registration status.
- *: The SICAV may reject any subscription in whole or in part. It may soft-close or hard-close a fund or share class without notice, and may compulsorily redeem holdings of ineligible shareholders.