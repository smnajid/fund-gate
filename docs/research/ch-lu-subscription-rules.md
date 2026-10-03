# Which Swiss and Luxembourg rules govern who may subscribe to a fund?

Research for issue #2. Researched 2026-10-03. Every rule below links to the primary text it comes from.

**Scope.** These are the regulatory facts an **Eligibility Check** has to encode: **Hard Restrictions** plus the MiFID II **Target Market** match. **Suitability** and appropriateness are left out. They are mentioned only where they change who may subscribe.

**Short answer.** Who may subscribe to a **Share Class** depends on three things:

1. The client's **Investor Classification** under the regime of the bank serving the client: FinSA (CH) or MiFID II (LU/EU).
2. Whether the fund may be offered or marketed to that classification in the client's country. This covers FINMA approval, the CH representative and paying agent, the UCITS notification and the AIFMD passport.
3. The fund's own restrictions in its **Fund Documentation**, such as the "well-informed investor only" rule for SIF/RAIF, institutional-only share classes, US Person exclusion and minimum subscription.

The Target Market comes on top of these. In the EU it is a regulatory duty for the distributor. In Switzerland it is not: FinSA has no product-governance or target-market rule, so the EMT is used only as an industry data feed.

## Source abbreviations

| Abbrev | Primary source |
|---|---|
| FinSA | Swiss Federal Act on Financial Services, SR 950.1, version of 1 Oct 2026. https://www.fedlex.admin.ch/eli/cc/2019/758/en |
| FinSO | Swiss Financial Services Ordinance, SR 950.11. https://www.fedlex.admin.ch/eli/cc/2019/759/en |
| CISA | Swiss Collective Investment Schemes Act, SR 951.31. https://www.fedlex.admin.ch/eli/cc/2006/822/en |
| MiFID II | Directive 2014/65/EU. https://eur-lex.europa.eu/eli/dir/2014/65/oj |
| MiFID DD | Delegated Directive (EU) 2017/593, product governance. https://eur-lex.europa.eu/eli/dir_del/2017/593/oj |
| MiFID DR | Delegated Regulation (EU) 2017/565, organisational requirements and records. https://eur-lex.europa.eu/eli/reg_del/2017/565/oj |
| ESMA PG GL | ESMA Guidelines on MiFID II product governance, ESMA35-43-3448 (03/08/2023). https://www.esma.europa.eu/sites/default/files/2023-08/ESMA35-43-3448_Guidelines_on_product_governance.pdf |
| UCITS | Directive 2009/65/EC. https://eur-lex.europa.eu/eli/dir/2009/65/oj |
| AIFMD | Directive 2011/61/EU. https://eur-lex.europa.eu/eli/dir/2011/61/oj |
| CBDF | Directive (EU) 2019/1160, cross-border distribution of funds. https://eur-lex.europa.eu/eli/dir/2019/1160/oj |
| PRIIPs | Regulation (EU) No 1286/2014. https://eur-lex.europa.eu/eli/reg/2014/1286/oj |
| LU 2010 | Law of 17 Dec 2010 on UCIs, CSSF consolidated text. https://www.cssf.lu/wp-content/uploads/L_171210_UCI.pdf |
| LU SIF | Law of 13 Feb 2007 on SIFs, CSSF consolidated text. https://www.cssf.lu/wp-content/uploads/L_130207_SIF.pdf |
| LU RAIF | Law of 23 Jul 2016 on RAIFs, CSSF consolidated text. https://www.cssf.lu/wp-content/uploads/L_230716_RAIF_eng.pdf |
| LU AIFM | Law of 12 Jul 2013 on AIFMs, CSSF consolidated text. https://www.cssf.lu/wp-content/uploads/L_120713_AIFM_eng.pdf |
| EMT | FinDatEx European MiFID Template V4.3 (2025-12-17). https://findatex.eu/mediaitem/1a4641be-40fd-4348-b148-d3ace0a2cb57/20251217+FinDatEx+-+EMT+V4.3.xlsx |
| Reg S | US SEC Regulation S, 17 CFR 230.902(k). https://www.ecfr.gov/current/title-17/section-230.902 |
| IRC | US Internal Revenue Code, 26 USC 7701(a)(30) and 1471 (FATCA). https://uscode.house.gov/view.xhtml?req=granuleid:USC-prelim-title26-section7701 |

The English texts of Swiss laws on Fedlex are unofficial translations, and the CSSF consolidated texts are for information only. The official texts are the DE/FR/IT originals and the *Journal officiel* (Mémorial).

---

## A. Investor Classification

### Switzerland (FinSA)

- **CH-IC-1. Three segments.** The bank must assign each client to one of three segments: *retail*, *professional* or *institutional*. A *retail client* is any client that is not professional. The bank may skip segmentation entirely and treat all clients as retail. Source: [FinSA Art. 4(1), (2), (7)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_4).
- **CH-IC-2. Professional clients per se.** These are:
  - financial intermediaries under the BankA, FinIA or CISA, and supervised insurers, plus foreign equivalents under prudential supervision
  - central banks
  - public entities, pension schemes and companies with professional treasury operations
  - large companies, meaning companies that exceed two of: balance sheet CHF 20m, turnover CHF 40m, equity CHF 2m
  - private investment structures with professional treasury operations set up for high-net-worth (HNW) retail clients

  Source: [FinSA Art. 4(3), (5)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_4). "Professional treasury operations" means management is entrusted permanently to a qualified professional ([FinSO Art. 3(8)](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_3)).
- **CH-IC-3. Institutional clients.** These are professional clients under Art. 4(3)(a)–(d), plus national and supranational public entities with professional treasury operations. Source: [FinSA Art. 4(4)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_4).
- **CH-IC-4. Opting out (retail to professional).** A HNW retail client, or a private investment structure set up for one, may declare that it wants to be treated as professional. HNW means one of:
  - (a) assets of at least CHF 500,000 **and** the knowledge to understand the risks, from training, education or professional experience
  - (b) assets of at least CHF 2m

  Eligible assets are financial investments: deposits, securities (including funds), derivatives, precious metals and surrender-value life insurance. Direct real estate and pension assets do not count. Joint holders must opt out together. Source: [FinSA Art. 5(1)–(2)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_5) and [FinSO Art. 5](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_5).
- **CH-IC-5. Other opt-ins and opt-outs.** Three other changes of segment are allowed:
  - Pension schemes and companies with treasury operations, and collective investment schemes and their management companies, may opt up to institutional.
  - Professional clients that are not institutional may opt in to retail.
  - Institutional clients may opt down to professional.

  Before providing services, the bank must tell non-retail clients that they can opt in. Every declaration must be made in writing or in another form evidenced by text. Source: [FinSA Art. 5(3)–(8)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_5).
- **CH-IC-6. Joint and represented clients.** Where several clients hold assets jointly, they get the most protective segment. A client acting through an authorised person may agree in text form to be segmented on that person's knowledge. Source: [FinSO Art. 4](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_4).
- **CH-IC-7. Qualified investor (CISA).** This is the fund-eligibility category, and it is wider than "professional":
  - professional clients under FinSA Art. 4(3)–(5), or opted out under Art. 5(1) or (4)
  - **plus retail clients who have a permanent portfolio-management or investment-advice relationship** with a supervised financial intermediary or insurer, unless they have declared in text form that they do not want to be treated as qualified

  Source: [CISA Art. 10(3), (3ter)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_10). For a private bank, this means a retail client with a discretionary or advisory mandate is a *qualified investor* for funds.
- **CH-IC-8. Default openness.** Collective investment schemes are open to all investors unless the law, the fund regulations or the articles of association restrict them to qualified investors. Source: [CISA Art. 10(2)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_10).

### Luxembourg / EU (MiFID II and fund laws)

- **LU-IC-1. Three categories.** The three categories are *retail client*, *professional client* (per se under Annex II §I, or elective under Annex II §II) and *eligible counterparty* (Art. 30). Per-se professionals may ask to be treated as non-professional through a written agreement. Source: [MiFID II Annex II §I](https://eur-lex.europa.eu/eli/dir/2014/65/oj) and Art. 30.
- **LU-IC-2. Elective professional (opting up).** The bank must assess the client's expertise, and at least **two of three** criteria must be met:
  - an average of 10 significant transactions per quarter over the previous four quarters
  - a portfolio (cash plus financial instruments) above EUR 500,000
  - at least one year in a professional position in the financial sector

  The procedure: the client asks in writing, the bank gives a clear written warning about the protections lost, and the client confirms in a separate document that it understands the consequences. The bank must also have written categorisation policies. Source: [MiFID II Annex II §II.1–II.2](https://eur-lex.europa.eu/eli/dir/2014/65/oj).
- **LU-IC-3. Notifying the classification.** The bank must notify each client of its category, and must tell the client on a durable medium about its right to ask for a different category and what that would mean. Source: [MiFID DR Art. 45](https://eur-lex.europa.eu/eli/reg_del/2017/565/oj).
- **LU-IC-4. Well-informed investor (SIF and RAIF).** Securities of a SIF or RAIF are **reserved** to well-informed investors. A well-informed investor is:
  - an institutional investor
  - a MiFID II Annex II professional
  - or any other investor who **(a)** has confirmed in writing that it adheres to well-informed status **and (b)** either (i) invests at least **EUR 100,000**, or (ii) holds a certificate of expertise, experience and knowledge from an EU credit institution, MiFID investment firm, UCITS management company or authorised AIFM

  The fund's directors and managers are exempt. The fund must have the means to enforce the condition. The threshold was lowered from EUR 125,000 to EUR 100,000 by the Law of 21 July 2023. Source: [LU SIF Arts. 1–2](https://www.cssf.lu/wp-content/uploads/L_130207_SIF.pdf) and [LU RAIF Arts. 1–2](https://www.cssf.lu/wp-content/uploads/L_230716_RAIF_eng.pdf).
- **LU-IC-5. Institutional share classes.** A UCITS or Part II share class or compartment "reserved to one or more institutional investors" pays a reduced subscription tax of 0.01%. This is why "I" classes in Luxembourg carry an institutional-only Hard Restriction. Source: [LU 2010 Art. 174(2)(c)](https://www.cssf.lu/wp-content/uploads/L_171210_UCI.pdf). The law does not define "institutional investor" here. Take the definition from the fund's prospectus. This is an assumption based on CSSF practice and is not stated in the article.
- **LU-IC-6. Retail access by fund type.**
  - UCITS are retail funds.
  - AIFs are passported only to professional investors ([AIFMD Arts. 31–32](https://eur-lex.europa.eu/eli/dir/2011/61/oj)).
  - Member States *may* allow AIFs to be marketed to retail investors ([AIFMD Art. 43](https://eur-lex.europa.eu/eli/dir/2011/61/oj)). Luxembourg does so under conditions, and treats Part II UCIs and SIF/SICAR as meeting the supervision condition. Investor eligibility conditions in the fund-specific laws still apply, so a SIF remains well-informed-only. Source: [LU AIFM Art. 46](https://www.cssf.lu/wp-content/uploads/L_120713_AIFM_eng.pdf).

### Mapping Investor Classification across regimes

| Client Profile value | CH fund eligibility | LU fund eligibility |
|---|---|---|
| Retail, no mandate | Non-qualified investor | Retail client |
| Retail with a CH discretionary or advisory mandate | **Qualified investor** (CISA Art. 10(3ter)) unless they opted out | Retail (no equivalent) |
| Opted-out HNW (CH) / elective professional (EU) | Qualified investor | Professional client, so also well-informed |
| Professional / institutional per se | Qualified investor | Professional client / eligible counterparty |
| Retail who signed well-informed declaration + EUR 100k | n/a | Well-informed investor (SIF/RAIF only) |

**Engine implication.** Investor Classification is not a single enum. Store the FinSA segment, the opt-out or opt-in declarations with their dates, the mandate type, the CISA qualified-investor flag with any opt-out, the MiFID category, and any well-informed declaration or certificate.

---

## B. Distribution jurisdiction (may this fund be offered to this client in this country?)

### Switzerland

- **CH-DJ-1. Foreign fund offered to non-qualified investors.** The fund needs **FINMA approval** and must have appointed a Swiss **representative** and a Swiss **paying agent**. Source: [CISA Art. 120(1)–(2)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_120). The paying agent must be a Swiss bank, and investors may subscribe and redeem through it ([CISA Art. 121](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_121)). In practice, check that the fund appears on FINMA's list of approved foreign collective investment schemes.
- **CH-DJ-2. Foreign fund offered only to qualified investors.** No FINMA approval is needed. However, if the fund is offered to **opted-out HNW clients (FinSA Art. 5(1))**, a representative and a paying agent are still required at all times. Source: [CISA Art. 120(4)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_120) and [Art. 123(1)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_123). Offers only to per-se professional or institutional clients need neither.
- **CH-DJ-3. What counts as an "offer" (reverse solicitation).** An offer is a communication that contains enough information on the terms and the instrument and is meant to draw attention to it and sell it. These are **not** offers:
  - information provided at the client's request or initiative, as long as the offerer did not advertise that specific instrument beforehand
  - naming instruments with only factual data such as ISIN or NAV
  - mandatory information sent to existing clients

  Source: [FinSO Art. 3(5)–(6)](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_3) and [FinSA Art. 3(g)–(h)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_3). Services that a foreign provider gives at the client's *express initiative* are outside FinSA's territorial scope ([FinSO Art. 2](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_2)). **Engine:** an order the client initiated for a fund that is not registered for its segment should give **Needs Review**, not Eligible. The bank must record evidence that the client initiated it.
- **CH-DJ-4. KID for retail.** A financial instrument offered to retail clients needs a key information document (KID) from the producer. There are two exceptions: instruments bought only inside a portfolio-management mandate, and cases where an equivalent foreign document is used instead (the EU PRIIPs KID is accepted). Source: [FinSA Arts. 58–59](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_58). **Engine:** retail, no mandate and no KID/PRIIPs KID gives Not Eligible.

### Luxembourg / EU

- **LU-DJ-1. UCITS cross-border marketing.** Before marketing in another Member State, the UCITS must send a **notification letter** to its home regulator (the CSSF for LU funds), stating the arrangements in the host state, including per share class. Source: [UCITS Art. 93](https://eur-lex.europa.eu/eli/dir/2009/65/oj), amended by [CBDF](https://eur-lex.europa.eu/eli/dir/2019/1160/oj). **Engine:** the Share Class must be registered or notified in the client's country of residence.
- **LU-DJ-2. AIF marketing passport.** An EU AIFM may market an EU AIF to **professional investors** in its home state or other Member States once notified. Source: [AIFMD Arts. 31–32](https://eur-lex.europa.eu/eli/dir/2011/61/oj). Retail marketing depends on the host country ([Art. 43](https://eur-lex.europa.eu/eli/dir/2011/61/oj); in Luxembourg, [LU AIFM Art. 46](https://www.cssf.lu/wp-content/uploads/L_120713_AIFM_eng.pdf)).
- **LU-DJ-3. Pre-marketing window.** If a professional investor subscribes within **18 months** after the AIFM began pre-marketing that AIF, the subscription counts as the result of marketing and needs a notification. Source: AIFMD Art. 30a, inserted by [CBDF Art. 2](https://eur-lex.europa.eu/eli/dir/2019/1160/oj). EU fund law has **no harmonised reverse-solicitation exemption**. The MiFID II reverse-solicitation rule ([Art. 42](https://eur-lex.europa.eu/eli/dir/2014/65/oj)) applies to *third-country firms* providing services at the client's *own exclusive initiative*, not to fund marketing. **Engine:** a fund not notified in the client's country gives Needs Review, never Eligible.
- **LU-DJ-4. PRIIPs KID.** Anyone advising on or selling a PRIIP to a **retail investor** must provide the KID "in good time before" the investor is bound. Source: [PRIIPs Art. 13(1)](https://eur-lex.europa.eu/eli/reg/2014/1286/oj). UCITS have used the PRIIPs KID instead of the UCITS KIID since 1 Jan 2023 ([Reg. (EU) 2021/2259](https://eur-lex.europa.eu/eli/reg/2021/2259/oj)). **Engine:** retail client and no KID in the client's language or country gives Not Eligible.

---

## C. US Person / FATCA exclusion

- **US-1. Regulation S "U.S. person".** This test is based on **residence and place of organisation**, not citizenship. It covers:
  - any natural person *resident* in the US
  - US partnerships and corporations
  - estates or trusts with a US executor or trustee (with exceptions)
  - US branches of foreign entities
  - accounts held for a US person
  - foreign vehicles formed by US persons mainly to invest in unregistered securities

  Source: [17 CFR 230.902(k)](https://www.ecfr.gov/current/title-17/section-230.902). Fund prospectuses use this test to stay outside US registration.
- **US-2. FATCA / tax "United States person".** This test **does** cover citizens: a "citizen or resident of the United States", domestic partnerships and corporations, estates, and certain trusts. Source: [26 USC 7701(a)(30)](https://uscode.house.gov/view.xhtml?req=granuleid:USC-prelim-title26-section7701). FATCA withholding and reporting for foreign financial institutions, which include funds, is in [26 USC 1471](https://uscode.house.gov/view.xhtml?req=granuleid:USC-prelim-title26-section1471).
- **US-3. Which definition applies.** No CH or LU statute bans US Persons from funds. The exclusion is a **fund-level Hard Restriction found in the prospectus**, and each prospectus defines its own "US Person". Many combine Reg S with the FATCA definition, so a US citizen resident in Switzerland can be excluded. **Engine:** the Rule Card must store *which* definition the prospectus uses, with its Citation. The Client Profile needs residence, nationality and citizenship (including dual), place of birth, tax residence and FATCA status. A partial match, such as a US-born non-citizen or a green-card holder, gives Needs Review.

---

## D. Fund-level Hard Restrictions (from Fund Documentation, not from statute)

These are not set by law. The law requires them to be disclosed in the prospectus and fund rules, and the Rule Card extracts them with Citations, as in [ADR-0001](../adr/0001-llm-extracts-rules-engine-decides.md).

| Restriction | Where it comes from | Legal anchor |
|---|---|---|
| Investor category of the share class (retail / professional / institutional / qualified / well-informed) | Prospectus, share-class table | CISA Art. 10(2); LU SIF/RAIF Art. 1–2; LU 2010 Art. 174(2)(c) |
| Minimum initial subscription and holding | Prospectus, share-class table | Contractual. The SIF/RAIF EUR 100k is a statutory floor only for the *well-informed-by-amount* route |
| Countries where the fund is registered | Prospectus, country supplements, FINMA list, CSSF notifications | CISA Art. 120; UCITS Art. 93; AIFMD Arts. 31–32 |
| US Person exclusion | Prospectus definitions | Reg S / IRC (section C) |
| Other share-class restrictions, e.g. "clean" classes only for fee-based advisory or discretionary mandates, or distributor-specific classes | Prospectus | Inducement rules (EMT `00110_Fund_Share_Class_Without_Retrocession`) |

---

## E. MiFID II Target Market (LU/EU regulatory; CH voluntary)

- **TM-1. Manufacturer's duty.** The manufacturer defines an "identified target market of end clients within the relevant category of clients" and a distribution strategy that fits it. Source: [MiFID II Art. 16(3), 24(2)](https://eur-lex.europa.eu/eli/dir/2014/65/oj) and [MiFID DD Art. 9(9)](https://eur-lex.europa.eu/eli/dir_del/2017/593/oj).
- **TM-2. Distributor's duty.** The distributor (the bank) must understand the product, check that it fits its own clients "taking account of the identified target market", and offer it only when that is in the client's interest. Source: [MiFID II Art. 24(2)](https://eur-lex.europa.eu/eli/dir/2014/65/oj) and [MiFID DD Art. 10](https://eur-lex.europa.eu/eli/dir_del/2017/593/oj).
- **TM-3. Five categories.** The target market has five categories: (a) client type (retail / professional / eligible counterparty), (b) knowledge and experience, (c) financial situation with a focus on ability to bear losses, (d) risk tolerance and risk/reward profile (using the PRIIPs SRI or UCITS SRRI), and (e) objectives and needs, including the investment horizon and sustainability preferences. Source: [ESMA PG GL ¶19–20](https://www.esma.europa.eu/sites/default/files/2023-08/ESMA35-43-3448_Guidelines_on_product_governance.pdf).
- **TM-4. Deviations and how to treat them.**
  - Sales **outside the positive target market** are possible in specific cases. Each must be justified by the facts and **documented** (¶84).
  - Sales into the **negative target market** should be rare, need a significant justification, must be **reported to the manufacturer and disclosed to the client** (¶66, ¶85), and a firm may choose to block them (¶86).
  - Deviations on **client type** and **knowledge and experience** cannot be justified by diversification or hedging (¶64).

  Source: [ESMA PG GL](https://www.esma.europa.eu/sites/default/files/2023-08/ESMA35-43-3448_Guidelines_on_product_governance.pdf).

  **Suggested Verdict mapping:**
  - Client type or knowledge and experience in the negative target market: Not Eligible.
  - Any other category in the negative target market: Needs Review, with a strong justification.
  - Outside the positive target market but not in the negative (the "grey" zone): Needs Review.
  - All categories inside the positive target market: target market is OK.
- **TM-5. Switzerland.** FinSA, FinSO and CISA contain **no target-market or product-governance duty**. The only mention is an *optional* "target group and target market" item in the FinSA KID ([FinSO Annex 9 item 3.3](https://www.fedlex.admin.ch/eli/cc/2019/759/en)). For a Swiss booking centre, the target-market match is good practice and an EMT data feed, not a Hard Restriction. Swiss banks still have to apply MiFID when serving EU clients cross-border, which is outside the scope of this note.

### EMT V4.3 Target Market fields (FinDatEx, 2025-12-17)

Field names are copied exactly from the [EMT V4.3 template](https://findatex.eu/mediaitem/1a4641be-40fd-4348-b148-d3ace0a2cb57/20251217+FinDatEx+-+EMT+V4.3.xlsx). In the Status column, M means mandatory, C conditional, O optional and I indicative. "Neutral" means the manufacturer takes no position. In section 03, "N" is a **negative target market** value.

| ESMA category | EMT field | Values | Status |
|---|---|---|---|
| (control) | `00006_EMT_Data_Reporting_Target_Market` | Y / N | M |
| (control) | `01000_Target_Market_Reference_Date` | ISO date | M |
| (product) | `00060_Financial_Instrument_Product_Type` | U (UCITS) / N (non-UCITS) / … | M |
| (product) | `00075_Financial_Instrument_Manufacturer_Product_Governance_Process` | A (MiFID II) / B (comparable) / C (none) | — |
| Client type | `01010_Investor_Type_Retail` | Y / N / Neutral | M |
| Client type | `01020_Investor_Type_Professional` | Y / N / P (per se only) / E (elective only) | M |
| Client type | `01030_Investor_Type_Eligible_Counterparty` | Y / N | M |
| Knowledge and experience | `02010_Basic_Investor` | Y / N / Neutral | M |
| Knowledge and experience | `02020_Informed_Investor` | Y / N / Neutral | M |
| Knowledge and experience | `02030_Advanced_Investor` | Y / N / Neutral | M |
| Knowledge and experience | `02040_Expert_Investor_Germany` | Y / N / Neutral | O |
| Ability to bear losses | `03010_Compatible_With_Clients_Who_Can_Not_Bear_Capital_Loss` | Y / N (negative target) / Neutral | M |
| Ability to bear losses | `03020_Compatible_With_Clients_Who_Can_Bear_Limited_Capital_Loss` | Y / N / Neutral | C |
| Ability to bear losses | `03030_Limited_Capital_Loss_Level` | decimal (0.5 = 50%) | C |
| Ability to bear losses | `03040_Compatible_With_Clients_Who_Do_Not_Need_Capital_Guarantee` | Y / N / Neutral | M |
| Ability to bear losses | `03050_Compatible_With_Clients_Who_Can_Bear_Loss_Beyond_Capital` | Y / N / Neutral | M |
| Risk tolerance | `04010_Risk_Tolerance_PRIIPS_Methodology` | SRI 1–7 | C |
| Risk tolerance | `04020_Risk_Tolerance_UCITS_Methodology` | SRRI 1–7 | C |
| Risk tolerance | `04030_Risk_Tolerance_Internal_Methodology_For_Non_PRIIPS_And_Non_UCITS` | L / M / H | C |
| Risk tolerance | `04040_Risk_Tolerance_For_Non_PRIIPS_And_Non_UCITS_Spain` | 1–6 | C |
| Risk tolerance | `04050_Not_For_Investors_With_The_Lowest_Risk_Tolerance_Germany` | Y / Neutral | O |
| Objectives and needs | `05010_Return_Profile_Client_Looking_For_Preservation` | Y / N / Neutral | — |
| Objectives and needs | `05020_Return_Profile_Client_Looking_For_Capital_Growth` | Y / N / Neutral | — |
| Objectives and needs | `05030_Return_Profile_Client_Looking_For_Income` | Y / N / Neutral | — |
| Objectives and needs | `05040_Return_Profile_Hedging` | Y / N / Neutral (structured only) | C |
| Objectives and needs | `05050_Option_Or_Leveraged_Return_Profile` | Y / N / Neutral | O |
| Objectives and needs | `05070_Return_Profile_Pension_Scheme_Germany` | Y / N / Neutral | O |
| Objectives and needs (horizon) | `05080_Minimum_Recommended_Holding_Period` | years, or V(<1y) / S(≥1y) / M(≥3y) / L(>5y) / H(to maturity) | M |
| Objectives and needs (ESG) | `05105_Does_This_Financial_Instrument_Consider_End_Client_Sustainability_Preferences` | Y / Neutral (detail in the EET) | M |
| Objectives and needs | `05115_Other_Specific_Investment_Need` | N / I (Islamic) / O | I |
| Distribution strategy | `06010_Execution_Only` | R / P / B / N (Retail / Professional / Both / Neither) | I |
| Distribution strategy | `06020_Execution_With_Appropriateness_Test_Or_Non_Advised_Services` | R / P / B / N | I |
| Distribution strategy | `06030_Investment_Advice` | R / P / B / N | I |
| Distribution strategy | `06040_Portfolio_Management` | R / P / B / N | I |

Related fields useful for Hard Restrictions: `00010_Financial_Instrument_Identifying_Data` (ISIN), `00040_Financial_Instrument_Currency` and `00110_Fund_Share_Class_Without_Retrocession`. The EMT has **no** fields for registered countries, US Person or minimum subscription. Those must come from the prospectus.

The template labels the sections for investor type, knowledge and experience, and ability to bear losses as using "hierarchical logic". Read this as: a "Y" at a more vulnerable level implies the less vulnerable levels are also compatible. This is an interpretation; confirm it against FinDatEx's EMT Q&A before relying on it.

---

## F. What the bank must record to justify a decision

- **REC-CH-1.** The bank must document, in an appropriate manner:
  - the financial services agreed and the information collected about the client
  - the notice that no appropriateness or suitability check will be done (execution-only), or the fact that it advised the client against the instrument
  - the services provided
  - for advice, the client's needs and the reasons for each recommendation

  Source: [FinSA Art. 15](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_15). The records must allow the bank to account to the client, usually within **10 working days** ([FinSO Art. 18](https://www.fedlex.admin.ch/eli/cc/2019/759/en#art_18)).
- **REC-CH-2.** Opt-out, opt-in and qualified-investor opt-out declarations must be in writing or in a form evidenced by text, so keep them with their dates. Source: [FinSA Art. 5(8)](https://www.fedlex.admin.ch/eli/cc/2019/758/en#art_5) and [CISA Art. 10(3ter)(b)](https://www.fedlex.admin.ch/eli/cc/2006/822/en#art_10).
- **REC-EU-1.** The bank must keep records of all services and transactions that are enough for the regulator to check compliance. Records must let the regulator **reconstruct each key stage** of processing, must show any corrections alongside the original content, and must be protected from tampering. Source: [MiFID II Art. 16(6)](https://eur-lex.europa.eu/eli/dir/2014/65/oj) and [MiFID DR Art. 72 and Annex I](https://eur-lex.europa.eu/eli/reg_del/2017/565/oj), which lists the minimum records. Agreements setting out the client's rights are kept for at least the duration of the relationship ([MiFID DR Art. 73](https://eur-lex.europa.eu/eli/reg_del/2017/565/oj)). Records of order-related communications are kept for 5 years ([MiFID II Art. 16(7)](https://eur-lex.europa.eu/eli/dir/2014/65/oj)). Plan for at least 5 years in total.
- **REC-EU-2.** The bank must record the classification notice and the information given about the right to change category ([MiFID DR Art. 45](https://eur-lex.europa.eu/eli/reg_del/2017/565/oj)). For an elective professional, it must also keep the client's written request, the bank's written warning and the client's separate acknowledgement ([MiFID II Annex II §II.2](https://eur-lex.europa.eu/eli/dir/2014/65/oj)).
- **REC-EU-3.** Any sale outside the positive target market must have its reason documented. A sale into the negative target market must also be reported to the manufacturer and disclosed to the client. Source: [ESMA PG GL ¶66, ¶84–85](https://www.esma.europa.eu/sites/default/files/2023-08/ESMA35-43-3448_Guidelines_on_product_governance.pdf).
- **REC-LU-SIF.** For a SIF or RAIF, keep evidence of well-informed status: the written declaration plus either proof of the EUR 100k investment or the third-party assessment certificate. Source: [LU SIF Art. 2](https://www.cssf.lu/wp-content/uploads/L_130207_SIF.pdf) and [LU RAIF Art. 2](https://www.cssf.lu/wp-content/uploads/L_230716_RAIF_eng.pdf).

**For the Justification object, record at least:**
- Client Profile snapshot: classification and its evidence, residence, nationality, US-person facts, mandate type
- Rule Card version, with the Fund Documentation document, version or date, and passage for each rule
- each rule's outcome
- the EMT version and `01000_Target_Market_Reference_Date`
- the Verdict
- the reviewer, if the Verdict is Needs Review
- a timestamp

Keep all of it immutable.

---

## G. Open questions and caveats

- **Reverse solicitation** cannot be proven by rules. Treat it as Needs Review that requires evidence the client initiated the order (CH-DJ-3, LU-DJ-3).
- **"Institutional investor"** for Luxembourg Art. 174 share classes is defined by CSSF practice and FAQs, not by the article itself. Rely on the prospectus definition.
- **Swiss Target Market.** Confirm with the hackathon bank whether it applies the EMT target market in its Swiss booking centre as a hard rule or only as advice.
- **ELTIF, EuVECA and EuSEF** have their own retail rules (LU AIFM Art. 46(3)). They are not covered here.
- **FINMA registration status** and **CSSF/ESMA notification status** of a fund are data lookups: the FINMA list of approved foreign schemes and the ESMA register of cross-border fund marketing. They do not come from the prospectus.
