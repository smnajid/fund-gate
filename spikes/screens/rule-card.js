// PROTOTYPE data: Draft Rule Card for Nordea 1 GCE BI-EUR, copied from the extraction spike run
// prototype/rule-card-extraction: spikes/rule-card-extraction/runs/20261004-182155-plan-gold-pages-literal.json
window.DRAFT_CARD_RULES = [
 {
  "kind": "investor_category",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "allowed_categories": [
   "institutional"
  ],
  "contradiction": true,
  "confidence": 0.85,
  "summary": "BI-EUR is core class I, which the prospectus restricts to institutional investors. The prospectus defines these as investors under art. 174 of the 2010 Law: credit institutions and other financial professionals investing for themselves or for others (including retail clients under a discretionary management agreement), insurance companies, pension funds and other UCIs. CONTRADICTION: the BI-EUR KID says the fund 'is suitable for all types of investors through all distribution channels', which conflicts with the institutional-only rule. A validator must decide.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 123,
    "quote": "I Institutional investors. Yes None",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 5,
    "quote": "institutional investors Investors within the meaning of article 174 of the 2010 Law, such as credit institutions and other financial professionals investing on the behalf of themselves or other investors (whether retail in connection with a discretionary management agreement or institutional), as well as insurance companies, pension funds, or other UCIs.",
    "verified": true
   },
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "The fund is suitable for all types of investors through all distribution channels.",
    "verified": true
   }
  ]
 },
 {
  "kind": "us_person_exclusion",
  "scope": "fund",
  "share_class": "*",
  "us_person_definition": "reg_s",
  "excludes_us_citizens": false,
  "contradiction": false,
  "confidence": 0.8,
  "summary": "Shares may not be offered or sold in the USA, and US persons may not subscribe unless the board or management company decides otherwise. 'US person' is defined mainly by residence and place of organisation, plus any US person under Rule 902 of Regulation S. Anyone who is a 'Non-United States person' under CFTC Rule 4.7 is carved out. US taxpayers may subscribe if they are not US persons, so US citizenship alone does not exclude an investor.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 3,
    "quote": "The shares in the SICAV may not be directly or indirectly offered or sold in the USA. Unless otherwise decided by the board or the management company, US persons may not subscribe for shares in the SICAV.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 3,
    "quote": "Therefore, US taxpayers, as defined for US federal income tax purposes, may subscribe for shares in the SICAV as long as they do not qualify as US persons.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 5,
    "quote": "any other U.S. person defined by US Rule 902 of Regulation S",
    "verified": true
   }
  ]
 },
 {
  "kind": "minimum_initial_subscription",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "amount": 75000,
  "currency": "EUR",
  "contradiction": false,
  "confidence": 0.9,
  "summary": "Class I requires a minimum initial investment of EUR 75,000 or the equivalent in other currencies. The minimum applies per share class per fund and includes switches. The SICAV may reduce or waive it if this is consistent with equal treatment of shareholders.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 123,
    "quote": "EUR 75,000 or equivalent in other currencies",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 124,
    "quote": "Amounts apply for each share class of each fund, and include initial investments made by switches",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 139,
    "quote": "Reduce or waive any stated sales charge, or minimum initial investment amount, for any share class, fund, investor, or request, so long as it is consistent with equal treatment of shareholders.",
    "verified": true
   }
  ]
 },
 {
  "kind": "mandate_only_class",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "mandate_only": false,
  "contradiction": false,
  "confidence": 0.75,
  "summary": "Class I is restricted to institutional investors, not to mandate clients as such. However, the definition of 'institutional investors' includes financial professionals investing for retail clients under a discretionary management agreement. So retail clients can reach this class only through such a mandate.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 5,
    "quote": "institutional investors Investors within the meaning of article 174 of the 2010 Law, such as credit institutions and other financial professionals investing on the behalf of themselves or other investors (whether retail in connection with a discretionary management agreement or institutional), as well as insurance companies, pension funds, or other UCIs.",
    "verified": true
   }
  ]
 },
 {
  "kind": "kid_required_for_retail",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "kid_available": true,
  "contradiction": false,
  "confidence": 0.95,
  "summary": "A PRIIPs KID exists for BI-EUR (LU0348927095), published 20/05/2026.",
  "citations": [
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "Nordea 1 – Global Climate and Environment Fund - BI - EUR Share Class",
    "verified": true
   },
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "The document was published on 20/05/2026",
    "verified": true
   }
  ]
 },
 {
  "kind": "target_market",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "dimension": "client_type",
  "value": "all",
  "contradiction": true,
  "confidence": 0.7,
  "summary": "The BI-EUR KID (and the fund-level prospectus text) literally gives the target market client type as 'all types of investors'. CONTRADICTION: the prospectus restricts core class I to 'Institutional investors'. Not resolved; a validator must decide.",
  "citations": [
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "The fund is suitable for all types of investors through all distribution channels.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 123,
    "quote": "I Institutional investors. Yes None",
    "verified": true
   }
  ]
 },
 {
  "kind": "target_market",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "dimension": "knowledge_experience",
  "value": "basic",
  "contradiction": false,
  "confidence": 0.6,
  "summary": "Investors should understand the fund's risks and accept that its value fluctuates. No specific experience is required, so this is mapped to basic.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 16,
    "quote": "Investor profile Investors who understand the risks of the fund and plan to invest for at least 5 years.",
    "verified": true
   },
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "The fund is intended for investors who accept and understand that the value of the fund is directly linked to the value of underlying investments, will fluctuate over time and that the invested amount can be lost entirely.",
    "verified": true
   }
  ]
 },
 {
  "kind": "target_market",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "dimension": "loss_bearing",
  "value": "full_loss",
  "contradiction": false,
  "confidence": 0.9,
  "summary": "Investors must be able to bear the loss of their entire investment. There is no capital protection.",
  "citations": [
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "This product does not include any protection from future market performance so you could lose some or all of your investment.",
    "verified": true
   }
  ]
 },
 {
  "kind": "target_market",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "dimension": "risk_sri",
  "value": "4",
  "contradiction": false,
  "confidence": 0.95,
  "summary": "Summary risk indicator (SRI) is 4 out of 7 (medium).",
  "citations": [
   {
    "doc": "kid-BI-EUR",
    "page": 1,
    "quote": "We have classified this Fund as 4 out of 7, which is a medium risk class.",
    "verified": true
   }
  ]
 },
 {
  "kind": "target_market",
  "scope": "share_class",
  "share_class": "BI-EUR",
  "dimension": "horizon_years",
  "value": "5",
  "contradiction": false,
  "confidence": 0.95,
  "summary": "Recommended holding period is at least 5 years. The KID and the prospectus agree.",
  "citations": [
   {
    "doc": "kid-BI-EUR",
    "page": 3,
    "quote": "This product has no required minimum holding period but is designed for long-term investment; you should be prepared to stay invested for at least 5 years.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 16,
    "quote": "Investor profile Investors who understand the risks of the fund and plan to invest for at least 5 years.",
    "verified": true
   }
  ]
 },
 {
  "kind": "unmodelled_restriction",
  "scope": "fund",
  "share_class": "*",
  "value": "jurisdiction_registration",
  "contradiction": false,
  "confidence": 0.8,
  "summary": "Jurisdiction restriction: shares may be offered or bought only where they are registered for public sale or where sale is not prohibited by local law. Some classes may be unavailable in some jurisdictions. The client's country of residence must be checked against the registration status.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 3,
    "quote": "Distributing this prospectus, offering these shares for sale, or investing in these shares is legal only where the shares are registered for public sale or where sale is not prohibited by local law or regulation.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 123,
    "quote": "Also, some funds or share classes that are available in certain jurisdictions may not be available in others.",
    "verified": true
   }
  ]
 },
 {
  "kind": "unmodelled_restriction",
  "scope": "fund",
  "share_class": "*",
  "value": "discretionary_rejection_and_closure",
  "contradiction": false,
  "confidence": 0.7,
  "summary": "The SICAV may reject any subscription in whole or in part. It may soft-close or hard-close a fund or share class without notice, and may compulsorily redeem holdings of ineligible shareholders.",
  "citations": [
   {
    "doc": "prospectus",
    "page": 138,
    "quote": "Reject or cancel any application to open an account or any request to subscribe for or transfer shares We can reject the entire amount or part of it.",
    "verified": true
   },
   {
    "doc": "prospectus",
    "page": 138,
    "quote": "A closure may apply only to new investors (soft closure) or to further investments from existing shareholders as well (hard closure).",
    "verified": true
   }
  ]
 }
];
