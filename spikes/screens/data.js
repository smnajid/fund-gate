// PROTOTYPE data for the dealer screen. Hand-written from docs/playbook/demo-dataset.md
// (coverage set rows 1-11 and the guard email). Not fixtures: nothing here is computed.

const CLASSES = {
  'GCE-BP': { fund: 'Nordea 1 – Global Climate and Environment', cls: 'BP-EUR', isin: 'LU0348926287', regime: 'LU UCITS', card: 'v2 · validated 2026-11-03 by J. Keller' },
  'GCE-BI': { fund: 'Nordea 1 – Global Climate and Environment', cls: 'BI-EUR', isin: 'LU0348927095', regime: 'LU UCITS', card: 'v2 · validated 2026-11-03 by J. Keller' },
  'SIF-W':  { fund: 'Nordea SIF – Global Private Equity', cls: 'BW-SEK', isin: 'LU2607415275', regime: 'LU SIF', card: 'v1 · validated 2026-11-03 by J. Keller' },
  'BNP-A':  { fund: 'BNP Paribas (CH) Bonds CHF', cls: 'A', isin: 'CH0112537516', regime: 'CH', card: 'v1 · validated 2026-11-04 by J. Keller' },
  'BNP-I':  { fund: 'BNP Paribas (CH) Bonds CHF', cls: 'I', isin: 'CH0112537557', regime: 'CH', card: 'v1 · validated 2026-11-04 by J. Keller' },
  'SL-ICAP':{ fund: 'Swiss Life iFunds (CH)', cls: 'I Cap', isin: 'ISIN to confirm', regime: 'CH', card: 'v1 · validated 2026-11-04 by J. Keller' },
};

const RULE_LABELS = {
  Cat: 'Investor category', US: 'US Person', Min: 'Minimum subscription', Mand: 'Mandate-only class',
  KID: 'KID for retail', Unm: 'Unmodelled restrictions', TM: 'Target Market', Reg: 'Fund Registration',
};
const RULE_ORDER = ['Cat', 'US', 'Min', 'Mand', 'KID', 'Unm', 'TM', 'Reg'];

function R(outcome, text, doc, page, quote) {
  return { outcome, text, cite: doc ? { doc, page, quote } : null };
}

// Shared Nordea 1 citations
const N1 = {
  usP: R('P', 'Not a US Person under Nordea\'s Reg S definition.', 'Nordea 1 prospectus', 5, 'any other U.S. person defined by US Rule 902'),
  kid: R('P', 'A PRIIPs KID exists for this class.', 'KID', 1, 'The document was published on 20/05/2026'),
  unm: R('P', 'Noted by the validator: the board may reject or close; this sets no condition on the client (ADR 0003).', 'Nordea 1 prospectus', 138, 'Reject or cancel any application to open an account or any request to subscribe for or transfer shares'),
  regCH: R('P', 'Registered in CH: Swiss representative named.', 'Fund Registration table', null, 'Representative and Paying Agent (Zurich branch), prospectus p.146'),
  regLU: R('P', 'Registered in LU (home jurisdiction).', 'Fund Registration table', null, 'home jurisdiction'),
};

const CHECKS = {
  1: { row: 1, ref: 'LOC90000101', pid: '3f6c2a1e-8d41-4b7a-9c55-0e2b7d91a4c3', cls: 'GCE-BI', amount: 'EUR 80,000', verdict: 'Not Eligible',
       headline: 'Advisory retail portfolio is not an institutional investor.',
       rules: {
         Cat: R('F', 'FinSA retail, advisory mandate. Class I is reserved for institutional investors (art. 174, 2010 Law).', 'Nordea 1 prospectus', 123, 'I Institutional investors.'),
         US: N1.usP,
         Min: R('P', 'EUR 80,000 ≥ EUR 75,000.', 'Nordea 1 prospectus', 123, 'EUR 75,000 or equivalent in other currencies'),
         KID: N1.kid, Unm: N1.unm,
         TM: R('P', 'Risk 3 Balanced → max SRI 4 ≥ SRI 4; horizon 7y ≥ 5y RHP; full loss accepted.', 'KID', 1, 'We have classified this Fund as 4 out of 7, which is a medium risk class.'),
         Reg: N1.regCH,
       } },
  2: { row: 2, ref: 'LOC90000102', pid: '9b2d71c0-5e3f-4a88-b1d6-7c4e0f2a6b19', cls: 'GCE-BI', amount: 'EUR 80,000', verdict: 'Eligible',
       headline: 'Discretionary mandate counts as institutional.',
       rules: {
         Cat: R('P', 'Discretionary mandate since 2021-03-01: a financial professional investing for a retail client under a discretionary management agreement is institutional.', 'Nordea 1 prospectus', 5, 'financial professionals investing on the behalf of … retail clients in connection with a discretionary management agreement'),
         US: N1.usP,
         Min: R('P', 'EUR 80,000 ≥ EUR 75,000.', 'Nordea 1 prospectus', 123, 'EUR 75,000 or equivalent in other currencies'),
         KID: N1.kid, Unm: N1.unm,
         TM: R('P', 'Risk 4 Growth → max SRI 5 ≥ SRI 4; horizon 10y ≥ 5y RHP; full loss accepted.', 'KID', 1, 'We have classified this Fund as 4 out of 7, which is a medium risk class.'),
         Reg: N1.regCH,
       } },
  3: { row: 3, ref: 'LOC90000101', pid: '3f6c2a1e-8d41-4b7a-9c55-0e2b7d91a4c3', cls: 'GCE-BP', amount: 'EUR 80,000', verdict: 'Eligible', suggested: true,
       headline: 'Retail class open to all investors: the alternative for portfolio 01.',
       rules: {
         Cat: R('P', 'Class P: all investors.', 'Nordea 1 prospectus', 123, 'P All investors.'),
         US: N1.usP,
         Min: R('P', 'No minimum.', 'Nordea 1 prospectus', 123, 'P All investors. None'),
         KID: N1.kid, Unm: N1.unm,
         TM: R('P', 'Risk 3 Balanced → max SRI 4 ≥ SRI 4; horizon 7y ≥ 5y RHP.', 'KID', 1, 'We have classified this Fund as 4 out of 7, which is a medium risk class.'),
         Reg: N1.regCH,
       } },
  // Tom: reverse lookup, CHF 2,000,000
  4: { row: 4, ref: 'LOC90000201', cls: 'GCE-BP', amount: 'CHF 2,000,000', verdict: 'Eligible', decisive: 'US',
       headline: 'Reg S: US citizenship alone is not a US Person.',
       rules: { Cat: R('P', 'All investors.'), US: R('P', 'Reg S definition lists residence and place of organisation, not citizenship.', 'Nordea 1 prospectus', 5, 'any other U.S. person defined by US Rule 902'), Min: R('P', 'No minimum.'), KID: N1.kid, Unm: N1.unm, TM: R('P', 'Risk 4 Growth → max SRI 5 ≥ SRI 4.'), Reg: N1.regCH } },
  5: { row: 5, ref: 'LOC90000201', cls: 'GCE-BI', amount: 'CHF 2,000,000', verdict: 'Not Eligible', decisive: 'Cat',
       headline: 'HNWI opt-out is not an art. 174 institutional investor (assumed reading).',
       rules: { Cat: R('F', 'FinSA professional by HNWI opt-out, advisory: not institutional.', 'Nordea 1 prospectus', 123, 'I Institutional investors.'), US: R('P', 'Reg S: not a US Person.'), Min: R('P', 'CHF 2,000,000 ≥ EUR 75,000.'), KID: N1.kid, Unm: N1.unm, TM: R('P', 'Within Target Market.'), Reg: N1.regCH } },
  6: { row: 6, ref: 'LOC90000201', cls: 'BNP-A', amount: 'CHF 2,000,000', verdict: 'Not Eligible', decisive: 'US',
       headline: 'BNP names US citizenship explicitly.',
       rules: { Cat: R('P', 'All investors.'), US: R('F', 'US citizen (dual CH/US).', 'BNP prospectus', 16, 'in particular investors with U.S. citizenship'), Min: R('P', 'No minimum.'), KID: R('P', 'Swiss KID exists.'), TM: R('W', 'CH: Target Market is a warning only. SRI 2 below risk profile.'), Reg: R('P', 'CH home jurisdiction.') } },
  7: { row: 7, ref: 'LOC90000201', cls: 'BNP-I', amount: 'CHF 2,000,000', verdict: 'Not Eligible', decisive: 'US',
       headline: 'Qualified and above CHF 1m, but a US citizen.',
       rules: { Cat: R('P', 'Qualified investor (HNWI opt-out).', 'BNP prospectus', 5, 'may only be held by qualified investors within the meaning of Art. 10 para 3 and 3ter CISA'), US: R('F', 'US citizen (dual CH/US).', 'BNP prospectus', 16, 'in particular investors with U.S. citizenship'), Min: R('P', 'CHF 2,000,000 ≥ CHF 1,000,000.'), KID: R('P', 'Swiss KID exists.'), TM: R('W', 'CH: warning only.'), Reg: R('P', 'CH home jurisdiction.') } },
  8: { row: 8, ref: 'LOC90000201', cls: 'SL-ICAP', amount: 'CHF 2,000,000', verdict: 'Not Eligible', decisive: 'US',
       headline: 'IRC definition: any US citizen, dual nationals included.',
       rules: { Cat: R('P', 'Qualified investor.'), US: R('F', 'US citizen (dual CH/US).', 'Swiss Life fund contract, appendix', 45, 'a US citizen (including dual or multiple citizenship)'), Unm: R('P', 'Noted.'), TM: R('W', 'No Target Market source (no KID): warning only in CH.'), Reg: R('P', 'Distribution approved: Switzerland.') } },
  9: { row: 9, ref: 'LOC90000201', cls: 'SIF-W', amount: 'CHF 2,000,000', verdict: 'Not Eligible', decisive: 'Reg',
       headline: 'Not registered in Switzerland.',
       rules: { Cat: R('P', 'Professional: well-informed by law.'), US: R('P', 'Reg S: not a US Person.'), Min: R('P', '≥ EUR 100,000.'), KID: R('P', 'KID exists.'), Unm: R('P', 'Noted: at AIFM discretion.'), TM: R('W', 'CH: warning only.'), Reg: R('F', 'No Swiss representative named: not registered in CH.', 'Fund Registration table', null, 'SIF-W · CH · any · no') } },
  // Claire
  10: { row: 10, ref: 'LUX90000301', pid: 'c71e4b09-2a6d-4f13-8e90-5d3b1a7c2f64', cls: 'GCE-BP', amount: 'EUR 20,000', verdict: 'Needs Review',
        headline: 'LU: outside the positive Target Market on three dimensions.',
        rules: {
          Cat: R('P', 'Class P: all investors.', 'Nordea 1 prospectus', 123, 'P All investors.'),
          US: N1.usP, Min: R('P', 'No minimum.'), KID: N1.kid, Unm: N1.unm,
          TM: R('R', 'Binding in LU. Risk 2 Income → max SRI 3 < SRI 4; horizon 2y < 5y RHP; limited loss < "could lose all".', 'KID', 1, 'you could lose some or all of your investment'),
          Reg: N1.regLU,
        },
        advisory: [
          { to: 'requester', what: 'Is the risk profile current?', why: 'The profile (2 Income) is one notch below the fund\'s SRI 4. If it was last reviewed more than 12 months ago, the adviser should re-run the risk questionnaire.',
            ask: 'le profil de risque de la cliente (Revenu) est-il à jour ? S\'il date de plus de 12 mois, merci de refaire le questionnaire ;' },
          { to: 'requester', what: 'Which horizon is 2 years?', why: 'A 2-year horizon against a 5-year recommended holding period. Is 2y the horizon of the portfolio or of this investment?',
            ask: 'l\'horizon de 2 ans s\'applique-t-il au portefeuille ou à cet investissement ?' },
          { to: 'dealer', what: 'File the acknowledgement if she proceeds', why: 'Written acknowledgement that the investment is outside her Target Market, plus the adviser\'s rationale, attached to this Check Record.' },
        ] },
  11: { row: 11, ref: 'LUX90000301', pid: 'c71e4b09-2a6d-4f13-8e90-5d3b1a7c2f64', cls: 'GCE-BI', amount: 'EUR 20,000', verdict: 'Not Eligible',
        headline: 'Retail, and below EUR 75,000.',
        rules: {
          Cat: R('F', 'MiFID retail: class I is reserved for institutional investors.', 'Nordea 1 prospectus', 123, 'I Institutional investors.'),
          US: N1.usP,
          Min: R('F', 'EUR 20,000 < EUR 75,000 (more than 5% below).', 'Nordea 1 prospectus', 123, 'EUR 75,000 or equivalent in other currencies'),
          KID: N1.kid, Unm: N1.unm,
          TM: R('R', 'Binding in LU: outside on risk, horizon and loss.', 'KID', 1, 'you could lose some or all of your investment'),
          Reg: N1.regLU,
        } },
};

const SCENARIOS = {
  anna: {
    label: 'Anna: two portfolios',
    email: `From: Marc Aebi (relationship manager)
To: Fund dealing desk
Subject: Subscription Nordea Global Climate – LOC90000101, LOC90000102

Hi desk,
The client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class, from portfolios LOC90000101 and LOC90000102. Can you confirm they can subscribe?
Thanks, Marc`,
    gate: { ok: true, swaps: [['LOC90000101', '3f6c2a1e-…a4c3'], ['LOC90000102', '9b2d71c0-…6b19']] },
    requests: [
      { ref: 'LOC90000101', cls: 'GCE-BI', amount: '80000', ccy: 'EUR', init: false },
      { ref: 'LOC90000102', cls: 'GCE-BI', amount: '80000', ccy: 'EUR', init: false },
    ],
    checks: [1, 2, 3],
    reply: `Hi Marc,

LOC90000101: not eligible for Nordea 1 Global Climate BI-EUR. Class I is reserved for institutional investors [1] and this is an advisory portfolio. The retail class BP-EUR (LU0348926287) is open to it and is eligible for EUR 80,000 [2].

LOC90000102: eligible for BI-EUR. The discretionary mandate makes it an institutional investor [3], and EUR 80,000 meets the EUR 75,000 minimum [4].

Best regards,
Fund dealing desk`,
    replyCites: { 1: [1, 'Cat'], 2: [3, 'Cat'], 3: [2, 'Cat'], 4: [2, 'Min'] },
    llm: [
      { call: 'Request Parser', model: 'claude-sonnet-5-5 · Foundry', fields: [], sent: 'Subject: Subscription Nordea Global Climate – 3f6c2a1e-…a4c3, 9b2d71c0-…6b19\n\nHi desk,\nThe client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class, from portfolios 3f6c2a1e-…a4c3 and 9b2d71c0-…6b19. Can you confirm they can subscribe?\nThanks, Marc', got: '[{portfolio_id: "3f6c2a1e-…", share_class_mention: "Nordea Global Climate, institutional class", amount: 80000, currency: "EUR", client_initiated: false}, {portfolio_id: "9b2d71c0-…", …}]' },
      { call: 'Reply Drafter', model: 'claude-sonnet-5-5 · Foundry', fields: ['Portfolio Id', 'Legal Entity (LOC)', 'Verdict', 'Justification + Citations', 'mandate type'], sent: 'Verdicts and Justifications for 3f6c2a1e-… (Not Eligible) and 9b2d71c0-… (Eligible), alternative GCE-BP (Eligible). Language: en.', got: 'Drafted reply with Portfolio Ids; swapped back to Portfolio References locally.' },
    ],
  },
  claire: {
    label: 'Claire: French, LU Target Market',
    email: `De : Sophie Lentz (conseillère)
À : Fund dealing desk
Objet : Souscription Nordea Global Climate – LUX90000301

Bonjour,
Ma cliente (LUX90000301) souhaite investir 20 000 EUR dans Nordea 1 Global Climate and Environment. Pouvez-vous valider ?
Merci, Sophie`,
    gate: { ok: true, swaps: [['LUX90000301', 'c71e4b09-…2f64']] },
    requests: [
      { ref: 'LUX90000301', cls: 'GCE-BP', amount: '20000', ccy: 'EUR', init: false, fanout: true },
      { ref: 'LUX90000301', cls: 'GCE-BI', amount: '20000', ccy: 'EUR', init: false, fanout: true },
    ],
    fanoutNote: 'No Share Class named: checking every class of the fund.',
    checks: [10, 11],
    reply: `Bonjour Sophie,

Pour LUX90000301, Nordea 1 – Global Climate and Environment :

• BP-EUR : à vérifier. Au Luxembourg le marché cible s'applique, et le profil de la cliente en sort sur trois points : risque 4/7 pour un profil Revenu, durée conseillée de 5 ans pour un horizon de 2 ans, perte totale possible [1]. {{questions}}Nous revenons vers vous après vérification.
• BI-EUR : non éligible. Classe réservée aux investisseurs institutionnels [2], minimum 75 000 EUR [3].

Cordialement,
Fund dealing desk`,
    replyCites: { 1: [10, 'TM'], 2: [11, 'Cat'], 3: [11, 'Min'] },
    questionsIntro: 'Pour avancer, pourriez-vous nous confirmer :',
    llm: [
      { call: 'Request Parser', model: 'claude-sonnet-5-5 · Foundry', fields: [], sent: 'Objet : Souscription Nordea Global Climate – c71e4b09-…2f64\n\nBonjour,\nMa cliente (c71e4b09-…2f64) souhaite investir 20 000 EUR dans Nordea 1 Global Climate and Environment. Pouvez-vous valider ?\nMerci, Sophie', got: '[{portfolio_id: "c71e4b09-…", share_class_mention: "Nordea 1 Global Climate and Environment" (no class), amount: 20000, currency: "EUR"}]' },
      { call: 'Advisory lane', model: 'claude-sonnet-5-5 · Foundry', fields: ['Portfolio Id', 'Legal Entity (LUX)', 'Investor Classification: retail', 'risk profile: 2 Income', 'ability to bear losses: limited', 'horizon: 2y', 'knowledge (funds): basic'], sent: 'Needs Review Justification for GCE-BP (Target Market, 3 dimensions) + 7 allow-listed facts.', got: '3 suggestions (what to check, evidence to file). Verdict unchanged.' },
      { call: 'Reply Drafter', model: 'claude-sonnet-5-5 · Foundry', fields: ['Portfolio Id', 'Legal Entity (LUX)', 'Verdict', 'Justification + Citations'], sent: 'Verdicts for GCE-BP (Needs Review) and GCE-BI (Not Eligible) + the Requester Questions the dealer accepted. Language: fr.', got: 'Drafted reply in French, accepted questions asked in it.' },
    ],
  },
  guard: {
    label: 'Guard: client name in email',
    email: `From: Marc Aebi
Subject: Nordea Global Climate for Anna Steinmann

Hi desk, can Anna Steinmann (LOC90000101) subscribe EUR 80,000 to Nordea Global Climate BP-EUR?`,
    gate: { ok: false, found: 'Anna Steinmann', where: 'Subject and line 1', reason: 'Known client name for LOC90000101' },
    requests: [],
    checks: [],
    llm: [],
  },
  tom: {
    label: 'Tom: what can this portfolio buy?',
    reverse: { ref: 'LOC90000201', amount: 'CHF 2,000,000', checks: [4, 5, 6, 7, 8, 9] },
    llm: [],
  },
};

window.PROTO = { CLASSES, RULE_LABELS, RULE_ORDER, CHECKS, SCENARIOS };
